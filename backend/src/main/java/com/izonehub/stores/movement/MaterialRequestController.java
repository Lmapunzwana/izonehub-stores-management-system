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
    @Transactional(readOnly = true)
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
        
        Page<MaterialRequest> result;
        if (isSiteManager) {
            java.util.List<Store> managedStores = stores.findStoresForUser(user.getId());
            java.util.List<UUID> userStoreIds = managedStores.stream().map(Store::getId).toList();
            if (userStoreIds.isEmpty()) {
                userStoreIds = java.util.List.of(UUID.randomUUID());
            }
            MaterialRequestStatus reqStatus = status != null ? parseStatus(status) : null;
            result = requests.findForSiteManager(userStoreIds, user.getId(), reqStatus, pageable);
        } else if (status != null) {
            MaterialRequestStatus reqStatus = parseStatus(status);
            result = requests.findByStatus(reqStatus, pageable);
        } else {
            result = requests.findAll(pageable);
        }

        result.forEach(this::resolveLazy);
        return result;
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER','SITE_STORE_MANAGER')")
    public MaterialRequest get(@PathVariable UUID id) {
        MaterialRequest mr = requests.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        resolveLazy(mr);
        return mr;
    }

    private void resolveLazy(MaterialRequest mr) {
        if (mr.getRequestingStore() != null) mr.getRequestingStore().getName();
        if (mr.getSourceStore() != null) mr.getSourceStore().getName();
        if (mr.getProject() != null) {
            mr.getProject().getName();
            mr.getProject().getCode();
        }
        if (mr.getRaisedBy() != null) mr.getRaisedBy().getFullName();
        if (mr.getApprovedBy() != null) mr.getApprovedBy().getFullName();
        if (mr.getLines() != null) {
            mr.getLines().forEach(l -> {
                if (l != null && l.getItem() != null) {
                    l.getItem().getName();
                    l.getItem().getUnitOfMeasure();
                }
            });
        }
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('SYSTEM_ADMINISTRATOR')")
    public void delete(@PathVariable UUID id) {
        MaterialRequest mr = requests.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        requests.delete(mr);
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
        
        boolean isSiteManager = raisedBy.getRoles().contains(com.izonehub.stores.user.Role.SITE_STORE_MANAGER) 
                                && !raisedBy.getRoles().contains(com.izonehub.stores.user.Role.SYSTEM_ADMINISTRATOR)
                                && !raisedBy.getRoles().contains(com.izonehub.stores.user.Role.CENTRAL_STORE_MANAGER);
        if (isSiteManager) {
            java.util.List<Store> allowedStores = stores.findStoresForUser(raisedBy.getId());
            boolean allowed = allowedStores.stream().anyMatch(s -> s.getId().equals(req.requestingStoreId()));
            if (!allowed) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only request materials for your assigned site store.");
            }
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
        boolean isAdmin = approver.getRoles().contains(com.izonehub.stores.user.Role.SYSTEM_ADMINISTRATOR);
        if (!isAdmin) {
            var assignedStore = approver.getAssignedStore();
            if (assignedStore == null || !assignedStore.getId().equals(mr.getSourceStore().getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Only the manager of the source store may approve this request");
            }
        }

        List<BigDecimal> quantities = (body == null || body.quantities() == null)
                ? mr.getLines().stream().filter(java.util.Objects::nonNull).map(MaterialRequestLine::getRequestedQuantity).toList()
                : body.quantities();
        return svc.approve(mr, approver, quantities);
    }

    /**
     * Second approval gate for site-to-site transfers (neither store is Central).
     * MaterialRequestCommandService.approve() routes those requests to
     * PENDING_CENTRAL_APPROVAL instead of APPROVED; dispatch() refuses to run until
     * this endpoint moves it the rest of the way to APPROVED. For any other request
     * (source or requesting store is Central) this will 422 — there's nothing to
     * central-approve because it never left the normal single-approval flow.
     */
    @PostMapping("/{id}/central-approve")
    @Transactional
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER')")
    public MaterialRequest centralApprove(@PathVariable UUID id, @AuthenticationPrincipal String email) {
        AppUser approver = currentUser(email);
        MaterialRequest mr = find(id);
        return svc.centralApprove(mr, approver);
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
                ? mr.getLines().stream().filter(java.util.Objects::nonNull).map(MaterialRequestLine::getApprovedQuantity).toList()
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
                ? mr.getLines().stream().filter(java.util.Objects::nonNull).map(MaterialRequestLine::getDispatchedQuantity).toList()
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

        List<Item> lineItems = req.lines().stream()
                .map(l -> items.findById(l.itemId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item not found: " + l.itemId())))
                .toList();
        List<BigDecimal> quantities = req.lines().stream().map(LineRequest::requestedQuantity).toList();

        return createAutoApprovedReturn(sourceStore, requestingStore, project, submitter,
                lineItems, quantities, "Standalone Return to Central");
    }

    /**
     * Returning against an already-fulfilled Material Request now goes through the same
     * document trail as a standalone return (create → submit → auto-approve → dispatch,
     * which produces a real dispatch note) instead of the old ad-hoc StockReturn object,
     * which updated inventory directly with no paper trail at all. Every return, however
     * it's started, always lands at Central per how the business actually runs this —
     * the goods physically sitting at the requesting store (mr.getRequestingStore()) are
     * what's being given back, so that's the "source" of this reverse leg; Central is the
     * "requesting" side receiving them.
     */
    @PostMapping("/{id}/returns")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER','SITE_STORE_MANAGER')")
    public MaterialRequest recordReturn(@PathVariable UUID id, @Valid @RequestBody ReturnRequest req,
                                        @AuthenticationPrincipal String email) {
        AppUser returnedBy = currentUser(email);
        MaterialRequest mr = find(id);

        if (req.lines() == null || req.lines().isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one return line is required");

        com.izonehub.stores.store.Store centralStore = stores.findByType(com.izonehub.stores.store.StoreType.CENTRAL)
                .stream().findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "No Central store is configured — cannot process a return."));

        if (mr.getRequestingStore().getId().equals(centralStore.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This request already originated from Central — there's nothing to return via this path.");
        }

        List<Item> lineItems = req.lines().stream()
                .map(l -> items.findById(l.itemId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item not found: " + l.itemId())))
                .toList();
        List<BigDecimal> quantities = req.lines().stream().map(ReturnLineRequest::quantity).toList();

        return createAutoApprovedReturn(mr.getRequestingStore(), centralStore, mr.getProject(), returnedBy,
                lineItems, quantities, "Return to Central against request " + mr.getId());
    }

    /** Shared by standaloneReturn and recordReturn — see recordReturn's javadoc for why. */
    private MaterialRequest createAutoApprovedReturn(com.izonehub.stores.store.Store givingStore,
                                                      com.izonehub.stores.store.Store centralStore,
                                                      com.izonehub.stores.project.Project project,
                                                      AppUser actor,
                                                      List<Item> lineItems,
                                                      List<BigDecimal> quantities,
                                                      String notes) {
        MaterialRequest mr = new MaterialRequest(centralStore, givingStore, project, actor, notes);
        for (int i = 0; i < lineItems.size(); i++) {
            mr.addLine(new MaterialRequestLine(lineItems.get(i), quantities.get(i)));
        }

        MaterialRequest saved = requests.save(mr);
        saved.submit();
        svc.approve(saved, actor, quantities);
        svc.dispatch(saved, actor, actor.getFullName(), "RETURN", quantities);

        auditLog.record("MATERIAL_REQUEST", saved.getId().toString(), "RETURN_DISPATCHED",
                "Return dispatched from " + givingStore.getName() + " to " + centralStore.getName()
                        + " by " + actor.getEmail() + " — " + notes,
                actor.getEmail());

        return saved;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private MaterialRequest find(UUID id) {
        return requests.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private AppUser currentUser(String email) {
        return users.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    /** Safe valueOf — throws BAD_REQUEST (not 500) for unrecognised status strings. */
    private static MaterialRequestStatus parseStatus(String raw) {
        try {
            return MaterialRequestStatus.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid status value: " + raw);
        }
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
