package com.izonehub.stores.store;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.UUID;

import com.izonehub.stores.user.AppUser;
import com.izonehub.stores.user.UserRepository;
import com.izonehub.stores.inventory.InventoryRepository;
import com.izonehub.stores.inventory.StoreInventory;
import com.izonehub.stores.issuance.ReturnCommandService;
import com.izonehub.stores.issuance.StockReturn;
import com.izonehub.stores.issuance.StockReturnLine;
import com.izonehub.stores.issuance.ReturnCondition;
import com.izonehub.stores.item.Item;
import com.izonehub.stores.item.ItemRepository;
import com.izonehub.stores.inventory.InventoryCommandService;
import com.izonehub.stores.audit.AuditLogService;
import com.izonehub.stores.config.CompanySubscriptionRepository;
import com.izonehub.stores.config.CompanySubscription;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;

@RestController
@RequestMapping("/api/stores")
public class StoreController {

    private final StoreRepository repo;
    private final UserRepository users;
    private final InventoryRepository inventoryRepo;
    private final ReturnCommandService returnService;
    private final InventoryCommandService inventoryService;
    private final ItemRepository items;
    private final AuditLogService auditLog;
    private final CompanySubscriptionRepository subRepo;

    public StoreController(StoreRepository repo, UserRepository users, InventoryRepository inventoryRepo, 
                           ReturnCommandService returnService, InventoryCommandService inventoryService,
                           ItemRepository items, AuditLogService auditLog, CompanySubscriptionRepository subRepo) {
        this.repo = repo;
        this.users = users;
        this.inventoryRepo = inventoryRepo;
        this.returnService = returnService;
        this.inventoryService = inventoryService;
        this.items = items;
        this.auditLog = auditLog;
        this.subRepo = subRepo;
    }

    @GetMapping
    public Page<Store> list(@RequestParam(defaultValue = "0") int page,
                            @RequestParam(defaultValue = "50") int size,
                            @RequestParam(defaultValue = "true") boolean active,
                            @RequestParam(defaultValue = "true") boolean managedOnly,
                            @org.springframework.security.core.annotation.AuthenticationPrincipal String email) {
        if (email != null && managedOnly) {
            AppUser user = users.findByEmail(email).orElse(null);
            if (user != null) {
                boolean isSiteManager = user.getRoles().contains(com.izonehub.stores.user.Role.SITE_STORE_MANAGER)
                                        && !user.getRoles().contains(com.izonehub.stores.user.Role.SYSTEM_ADMINISTRATOR)
                                        && !user.getRoles().contains(com.izonehub.stores.user.Role.CENTRAL_STORE_MANAGER);
                if (isSiteManager) {
                    java.util.List<Store> managedStores = repo.findStoresForUser(user.getId());
                    java.util.List<Store> filtered = managedStores.stream()
                        .filter(s -> s.isActive() == active)
                        .toList();
                    int total = filtered.size();
                    int from = Math.min(page * size, total);
                    int to = Math.min(from + size, total);
                    return new org.springframework.data.domain.PageImpl<>(filtered.subList(from, to), org.springframework.data.domain.PageRequest.of(page, size), total);
                }
            }
        }
        return repo.findByActive(active, org.springframework.data.domain.PageRequest.of(page, size));
    }

    @GetMapping("/{id}")
    public Store get(@PathVariable UUID id) {
        return repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR', 'CENTRAL_STORE_MANAGER')")
    public Store create(@Valid @RequestBody StoreRequest req) {
        AppUser manager = users.findById(req.managerId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Manager not found"));

        if (repo.countByActiveTrueAndManager_Id(manager.getId()) >= 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Manager already has the maximum of 5 active stores.");
        }

        StoreType type = StoreType.valueOf(req.type());
        if (type == StoreType.CENTRAL && repo.existsByType(StoreType.CENTRAL)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "There can be only ONE central store.");
        }

        int allowedSlots = subRepo.findAll().stream().findFirst().map(CompanySubscription::getAllowedStoreSlots).orElse(3);
        if (repo.countByActiveTrueAndClosingFalse() >= allowedSlots) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Company subscription limit reached (" + allowedSlots + " active stores).");
        }

        Store savedStore = repo.save(new Store(req.name(), type, req.location(), manager));

        if (req.assignedUsers() != null && !req.assignedUsers().isEmpty()) {
            java.util.List<AppUser> assignees = users.findAllById(req.assignedUsers());
            for (AppUser u : assignees) {
                u.setAssignedStore(savedStore);
            }
            users.saveAll(assignees);
        }

        return savedStore;
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR', 'CENTRAL_STORE_MANAGER')")
    public Store update(@PathVariable UUID id, @Valid @RequestBody StoreRequest req) {
        Store s = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        
        AppUser manager = users.findById(req.managerId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Manager not found"));
        
        // If manager changes, verify limits
        if (!manager.getId().equals(s.getManager().getId()) && repo.countByActiveTrueAndManager_Id(manager.getId()) >= 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Manager already has the maximum of 5 active stores.");
        }

        StoreType type = StoreType.valueOf(req.type());
        if (type == StoreType.CENTRAL && s.getType() != StoreType.CENTRAL && repo.existsByType(StoreType.CENTRAL)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "There can be only ONE central store.");
        }

        s.update(req.name(), type, req.location(), manager);
        Store savedStore = repo.save(s);

        if (req.assignedUsers() != null) {
            java.util.List<AppUser> currentAssignees = users.findByAssignedStore(s);
            for (AppUser u : currentAssignees) {
                u.setAssignedStore(null);
            }
            users.saveAll(currentAssignees);

            if (!req.assignedUsers().isEmpty()) {
                java.util.List<AppUser> newAssignees = users.findAllById(req.assignedUsers());
                for (AppUser u : newAssignees) {
                    u.setAssignedStore(savedStore);
                }
                users.saveAll(newAssignees);
            }
        }

        return savedStore;
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR', 'CENTRAL_STORE_MANAGER')")
    public Store close(@PathVariable UUID id, @AuthenticationPrincipal String email) {
        Store s = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        
        if (!s.isActive()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Store is already closed");
        if (s.isClosing()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Store is already in the process of closing");
        
        s.markClosing();
        Store saved = repo.save(s);

        AppUser closedBy = users.findByEmail(email).orElse(null);
        
        // Prepare items to be returned (generate a StockReturn of all on-hand stock)
        java.util.List<StoreInventory> inventory = inventoryRepo.findByStoreAndQuantityOnHandGreaterThan(s, java.math.BigDecimal.ZERO);
        if (!inventory.isEmpty() && closedBy != null) {
            StockReturn sr = new StockReturn(s, closedBy);
            for (StoreInventory inv : inventory) {
                sr.addLine(new StockReturnLine(inv.getItem(), inv.getQuantityOnHand(), ReturnCondition.SERVICEABLE));
            }
            returnService.createPendingReturn(null, sr);
        }

        return saved;
    }

    @PutMapping("/{id}/reopen")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR', 'CENTRAL_STORE_MANAGER')")
    public Store reopen(@PathVariable UUID id, @AuthenticationPrincipal String email) {
        Store s = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        
        if (s.isActive() && !s.isClosing()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Store is already active");

        int allowedSlots = subRepo.findAll().stream().findFirst().map(CompanySubscription::getAllowedStoreSlots).orElse(3);
        if (repo.countByActiveTrueAndClosingFalse() >= allowedSlots) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Company subscription limit reached. Cannot reopen until a slot is freed or purchased.");
        }
        
        s.reopen();
        return repo.save(s);
    }

    @PostMapping("/{id}/consume")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','SITE_STORE_MANAGER')")
    @Transactional
    public void consumeItems(@PathVariable UUID id, @Valid @RequestBody ConsumeRequest req, @AuthenticationPrincipal String email) {
        Store store = repo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Store not found"));
        
        if (!store.isActive() || store.isClosing()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot consume items in a closed or closing store");
        }

        AppUser user = users.findByEmail(email).orElse(null);

        for (ConsumeLineRequest l : req.lines()) {
            Item item = items.findById(l.itemId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item not found: " + l.itemId()));
            inventoryService.consume(store, item, l.quantity());
            // Build a human-readable audit message that includes the consumption date if provided
            String consumedOnText = (l.consumedAt() != null) ? " on " + l.consumedAt() : "";
            String notesText = (l.notes() != null && !l.notes().isBlank()) ? " — " + l.notes() : "";
            auditLog.record("INVENTORY", store.getId().toString(), "CONSUMED",
                    "Consumed " + l.quantity() + " of " + item.getName()
                            + " at store " + store.getName() + consumedOnText + notesText,
                    email);
        }
    }

    public record StoreRequest(@NotBlank String name,
                               @NotBlank String type,
                               @NotBlank String location,
                               @NotNull UUID managerId,
                               java.util.List<UUID> assignedUsers) {}

    /**
     * consumedAt — optional LocalDate the material was actually used on site
     *              (defaults to today if omitted). This is recorded in the audit log
     *              to support backdated consumption entries.
     * notes      — optional free-text reason/reference.
     */
    public record ConsumeLineRequest(
            @NotNull UUID itemId,
            @NotNull java.math.BigDecimal quantity,
            LocalDate consumedAt,
            String notes) {}

    public record ConsumeRequest(@NotNull java.util.List<ConsumeLineRequest> lines) {}
}
