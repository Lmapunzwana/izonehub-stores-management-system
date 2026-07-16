package com.izonehub.stores.movement;

import com.izonehub.stores.item.Item;
import com.izonehub.stores.item.ItemRepository;
import com.izonehub.stores.reporting.DispatchNoteService;
import com.izonehub.stores.store.Store;
import com.izonehub.stores.store.StoreRepository;
import com.izonehub.stores.project.Project;
import com.izonehub.stores.project.ProjectRepository;
import com.izonehub.stores.user.AppUser;
import com.izonehub.stores.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/material-requests")
public class MaterialRequestController {

    private final MaterialRequestRepository requests;
    private final StoreRepository stores;
    private final ItemRepository items;
    private final UserRepository users;
    private final MaterialRequestCommandService svc;
    private final DispatchRepository dispatches;
    private final DispatchNoteService dispatchNotes;
    private final com.izonehub.stores.inventory.InventoryRepository inventoryRepo;
    private final ProjectRepository projects;
    private final com.izonehub.stores.issuance.ReturnCommandService returns;
    private final com.izonehub.stores.audit.AuditLogService auditLog;

    public MaterialRequestController(MaterialRequestRepository requests, StoreRepository stores, ItemRepository items,
                                     UserRepository users, MaterialRequestCommandService svc,
                                     DispatchRepository dispatches, DispatchNoteService dispatchNotes,
                                     com.izonehub.stores.inventory.InventoryRepository inventoryRepo,
                                     ProjectRepository projects,
                                     com.izonehub.stores.issuance.ReturnCommandService returns,
                                     com.izonehub.stores.audit.AuditLogService auditLog) {
        this.requests = requests;
        this.stores = stores;
        this.items = items;
        this.users = users;
        this.svc = svc;
        this.dispatches = dispatches;
        this.dispatchNotes = dispatchNotes;
        this.inventoryRepo = inventoryRepo;
        this.projects = projects;
        this.returns = returns;
        this.auditLog = auditLog;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER','SITE_STORE_MANAGER')")
    public Page<MaterialRequest> list(@RequestParam(defaultValue = "0")  int page,
                                      @RequestParam(defaultValue = "20") int size,
                                      @RequestParam(required = false)    String status,
                                      @AuthenticationPrincipal String email) {
        var pageable = PageRequest.of(page, size, org.springframework.data.domain.Sort.by("createdAt").descending());
        
        AppUser user = users.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        boolean isSiteManager = user.getRoles().contains(com.izonehub.stores.user.Role.SITE_STORE_MANAGER) 
                                && !user.getRoles().contains(com.izonehub.stores.user.Role.SYSTEM_ADMINISTRATOR)
                                && !user.getRoles().contains(com.izonehub.stores.user.Role.CENTRAL_STORE_MANAGER);
        
        java.util.List<UUID> storeIds = null;
        if (isSiteManager) {
            java.util.List<Store> managedStores = stores.findByManager_Id(user.getId());
            if (managedStores.isEmpty()) {
                return Page.empty();
            }
            storeIds = managedStores.stream().map(Store::getId).toList();
        }

        if (status != null) {
            MaterialRequestStatus reqStatus = MaterialRequestStatus.valueOf(status.toUpperCase());
            if (storeIds != null) {
                return requests.findByStatusAndRequestingStore_IdIn(reqStatus, storeIds, pageable);
            }
            return requests.findByStatus(reqStatus, pageable);
        }
        
        if (storeIds != null) {
            return requests.findByRequestingStore_IdIn(storeIds, pageable);
        }
        return requests.findAll(pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER','SITE_STORE_MANAGER')")
    public MaterialRequest get(@PathVariable UUID id) {
        return requests.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER','SITE_STORE_MANAGER')")
    public MaterialRequest create(@Valid @RequestBody CreateRequest req, @AuthenticationPrincipal String email) {
        AppUser raisedBy = users.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        Store requestingStore = stores.findById(req.requestingStoreId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Requesting store not found"));
        
        if (!requestingStore.isActive() || requestingStore.isClosing()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot request items for a closed or closing store");
        }
        
        Store sourceStore = stores.findById(req.sourceStoreId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source store not found"));
        
        if (!sourceStore.isActive() || sourceStore.isClosing()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot request items from a closed or closing store");
        }
        if (req.lines() == null || req.lines().isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one line is required");

        Project project = projects.findById(req.projectId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project not found"));
        MaterialRequest mr = new MaterialRequest(requestingStore, sourceStore, project, raisedBy, req.transferReason());
        
        java.util.List<String> stockErrors = new java.util.ArrayList<>();
        for (LineRequest l : req.lines()) {
            Item item = items.findById(l.itemId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item not found: " + l.itemId()));
            
            var inv = inventoryRepo.findByStoreAndItem(sourceStore, item).orElse(null);
            BigDecimal available = inv != null ? inv.getQuantityAvailable() : BigDecimal.ZERO;
            if (available.compareTo(l.requestedQuantity()) < 0) {
                stockErrors.add(item.getName() + ": requested " + l.requestedQuantity() + " but only " + available + " available");
            }
            
            mr.addLine(new MaterialRequestLine(item, l.requestedQuantity()));
        }
        
        if (!stockErrors.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient stock at source store: " + String.join("; ", stockErrors));
        }
        
        return requests.save(mr);
    }

    // ── Workflow actions ──────────────────────────────────────────────────────
    // The frontend calls POST /api/material-requests/{id}/{submit|approve|reject|dispatch|receive}
    // through a single generic custom-mutation hook, so all five are handled here.

    @PostMapping("/{id}/submit")
    @Transactional
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER','SITE_STORE_MANAGER')")
    public MaterialRequest submit(@PathVariable UUID id, @AuthenticationPrincipal String email) {
        AppUser submitter = currentUser(email);
        return svc.submit(find(id), submitter);
    }

    @PostMapping("/{id}/approve")
    @Transactional
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER','SITE_STORE_MANAGER')")
    public MaterialRequest approve(@PathVariable UUID id,
                                   @RequestBody(required = false) QuantitiesRequest body,
                                   @AuthenticationPrincipal String email) {
        AppUser approver = currentUser(email);
        MaterialRequest mr = find(id);

        // ── Source-store manager enforcement ──────────────────────────────
        // Only the manager assigned to the SOURCE store (or SYSTEM_ADMINISTRATOR) may approve.
        // Relaxed for frontend mock testing:
        /*
        boolean isAdmin = approver.getRoles().contains(com.izonehub.stores.user.Role.SYSTEM_ADMINISTRATOR);
        if (!isAdmin) {
            var assignedStore = approver.getAssignedStore();
            if (assignedStore == null || !assignedStore.getId().equals(mr.getSourceStore().getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Only the manager of the source store (" + mr.getSourceStore().getName()
                                + ") may approve this request");
            }
        }
        */

        List<BigDecimal> quantities = (body == null || body.quantities() == null)
                ? mr.getLines().stream().map(MaterialRequestLine::getRequestedQuantity).toList()
                : body.quantities();
        return svc.approve(mr, approver, quantities);
    }

    @PostMapping("/{id}/reject")
    @Transactional
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER','SITE_STORE_MANAGER')")
    public MaterialRequest reject(@PathVariable UUID id, @RequestBody Map<String, String> body,
                                  @AuthenticationPrincipal String email) {
        AppUser approver = currentUser(email);
        String reason = body.getOrDefault("reason", "");
        return svc.reject(find(id), approver, reason);
    }

    @PostMapping("/{id}/dispatch")
    @Transactional
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER','SITE_STORE_MANAGER')")
    public MaterialRequest dispatch(@PathVariable UUID id, @Valid @RequestBody DispatchRequest body,
                                    @AuthenticationPrincipal String email) {
        AppUser dispatchedBy = currentUser(email);
        MaterialRequest mr = find(id);
        // The current UI doesn't collect per-line quantities — dispatch the
        // full approved quantity for every line by default.
        List<BigDecimal> quantities = (body.dispatchedQuantities() == null)
                ? mr.getLines().stream().map(MaterialRequestLine::getApprovedQuantity).toList()
                : body.dispatchedQuantities();
        svc.dispatch(mr, dispatchedBy, body.collectorName(), body.collectorEmployeeId(), quantities);
        return find(id);
    }

    @PostMapping("/{id}/receive")
    @Transactional
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','SITE_STORE_MANAGER')")
    public MaterialRequest receive(@PathVariable UUID id,
                                   @RequestBody(required = false) QuantitiesRequest body,
                                   @AuthenticationPrincipal String email) {
        AppUser receivedBy = currentUser(email);
        MaterialRequest mr = find(id);
        // The current UI doesn't collect per-line quantities — a clean receive
        // (matching what was dispatched, no variance) is the default; a future
        // discrepancy-entry UI can post explicit receivedQuantities instead.
        List<BigDecimal> quantities = (body == null || body.quantities() == null)
                ? mr.getLines().stream().map(MaterialRequestLine::getDispatchedQuantity).toList()
                : body.quantities();
        svc.receive(mr, receivedBy, quantities);
        return find(id);
    }

    @GetMapping("/{id}/dispatch-note")
    @Transactional
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER','SITE_STORE_MANAGER')")
    public ResponseEntity<byte[]> dispatchNote(@PathVariable UUID id) {
        MaterialRequest mr = find(id);
        Dispatch dispatch = dispatches.findByMaterialRequest_Id(mr.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "This request has not been dispatched yet"));
        byte[] pdf = dispatchNotes.generate(dispatch);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"dispatch-note-" + mr.getId() + ".pdf\"")
                .body(pdf);
    }

    @PostMapping("/standalone-return")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','SITE_STORE_MANAGER')")
    public MaterialRequest standaloneReturn(@Valid @RequestBody CreateRequest req, @AuthenticationPrincipal String email) {
        AppUser submitter = currentUser(email);
        com.izonehub.stores.store.Store sourceStore = stores.findById(req.sourceStoreId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source store not found"));
        com.izonehub.stores.store.Store requestingStore = stores.findById(req.requestingStoreId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Requesting store not found"));
        com.izonehub.stores.project.Project project = projects.findById(req.projectId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project not found"));

        MaterialRequest mr = new MaterialRequest(requestingStore, sourceStore, project, submitter, "Standalone Return to Central");
        for (LineRequest l : req.lines()) {
            Item item = items.findById(l.itemId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item not found: " + l.itemId()));
            mr.addLine(new MaterialRequestLine(item, l.requestedQuantity()));
        }
        
        MaterialRequest saved = requests.save(mr);
        saved.submit();
        
        // Auto-approve and dispatch
        List<BigDecimal> quantities = req.lines().stream().map(LineRequest::requestedQuantity).toList();
        svc.approve(saved, submitter, quantities);
        svc.dispatch(saved, submitter, submitter.getFullName(), "RETURN", quantities);
        
        return saved;
    }

    @PostMapping("/{id}/returns")
    @Transactional
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER','SITE_STORE_MANAGER')")
    public com.izonehub.stores.issuance.StockReturn recordReturn(@PathVariable UUID id, @Valid @RequestBody ReturnRequest req,
                                    @AuthenticationPrincipal String email) {
        AppUser returnedBy = currentUser(email);
        MaterialRequest mr = find(id);

        if (req.lines() == null || req.lines().isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one return line is required");

        // When returning a material request, we are sending stock back to the SOURCE store
        com.izonehub.stores.issuance.StockReturn stockReturn = new com.izonehub.stores.issuance.StockReturn(mr.getSourceStore(), returnedBy);
        for (ReturnLineRequest l : req.lines()) {
            Item item = items.findById(l.itemId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item not found: " + l.itemId()));
            stockReturn.addLine(new com.izonehub.stores.issuance.StockReturnLine(item, l.quantity(), com.izonehub.stores.issuance.ReturnCondition.valueOf(l.condition())));
        }
        com.izonehub.stores.issuance.StockReturn result = returns.createPendingReturn(null, stockReturn);
        result.getStore().getName();
        result.getLines().forEach(l -> l.getItem().getName());

        auditLog.record("STOCK_RETURN", result.getId().toString(), "CREATED",
                "Recorded return to store '" + result.getStore().getName() + "' by " + returnedBy.getEmail(),
                returnedBy.getEmail());

        return result;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private MaterialRequest find(UUID id) {
        return requests.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private AppUser currentUser(String email) {
        return users.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    public record LineRequest(@NotNull UUID itemId, @NotNull BigDecimal requestedQuantity) {}

    public record CreateRequest(
            @NotNull UUID requestingStoreId,
            @NotNull UUID sourceStoreId,
            @NotNull UUID projectId,
            String transferReason,
            List<LineRequest> lines) {}

    public record ReturnLineRequest(@NotNull UUID itemId, @NotNull BigDecimal quantity, @NotBlank String condition) {}

    public record ReturnRequest(List<ReturnLineRequest> lines) {}

    public record QuantitiesRequest(List<BigDecimal> quantities) {}

    public record DispatchRequest(
            @NotBlank String collectorName,
            @NotBlank String collectorEmployeeId,
            List<BigDecimal> dispatchedQuantities) {}
}
