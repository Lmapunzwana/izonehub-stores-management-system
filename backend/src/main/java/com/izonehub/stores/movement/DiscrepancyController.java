package com.izonehub.stores.movement;

import com.izonehub.stores.inventory.InventoryCommandService;
import com.izonehub.stores.store.Store;
import com.izonehub.stores.store.StoreRepository;
import com.izonehub.stores.user.AppUser;
import com.izonehub.stores.user.UserRepository;
import com.izonehub.stores.audit.AuditLogService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/discrepancies")
public class DiscrepancyController {

    private final DiscrepancyRepository discrepancies;
    private final UserRepository users;
    private final StoreRepository stores;
    private final InventoryCommandService inventory;
    private final AuditLogService auditLog;

    public DiscrepancyController(DiscrepancyRepository discrepancies, UserRepository users, StoreRepository stores,
                                 InventoryCommandService inventory, AuditLogService auditLog) {
        this.discrepancies = discrepancies;
        this.users = users;
        this.stores = stores;
        this.inventory = inventory;
        this.auditLog = auditLog;
    }

    /** The store a discrepancy is "against" — it's never a direct field, only reachable via whichever of receipt/GRN/return/count raised it. */
    private Store storeOf(Discrepancy d) {
        if (d.getReceipt() != null) return d.getReceipt().getMaterialRequest().getSourceStore();
        if (d.getGrn() != null) return d.getGrn().getExpectedReceipt().getStore();
        if (d.getStockCount() != null) return d.getStockCount().getStore();
        if (d.getStockReturn() != null) return d.getStockReturn().getStore();
        return null;
    }

    /** See IssuanceController for the identical site-manager scoping pattern. */
    private java.util.List<UUID> siteManagerAllowedStoreIds(String email) {
        AppUser user = users.findByEmail(email).orElse(null);
        if (user == null) return java.util.List.of();
        boolean isSiteManager = user.getRoles().contains(com.izonehub.stores.user.Role.SITE_STORE_MANAGER)
                && !user.getRoles().contains(com.izonehub.stores.user.Role.SYSTEM_ADMINISTRATOR)
                && !user.getRoles().contains(com.izonehub.stores.user.Role.CENTRAL_STORE_MANAGER);
        if (!isSiteManager) return null;
        return stores.findStoresForUser(user.getId()).stream().map(Store::getId).toList();
    }

    @GetMapping
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER','SITE_STORE_MANAGER')")
    public Page<Discrepancy> list(@RequestParam(defaultValue = "0")  int page,
                                  @RequestParam(defaultValue = "20") int size,
                                  @RequestParam(required = false)    String status,
                                  @AuthenticationPrincipal String email) {
        java.util.List<UUID> allowedStoreIds = siteManagerAllowedStoreIds(email);
        if (allowedStoreIds == null) {
            // Admin/central: no restriction, use the efficient paged/EntityGraph query directly.
            var pageable = PageRequest.of(page, size, org.springframework.data.domain.Sort.by("createdAt").descending());
            if (status != null) {
                return discrepancies.findByStatus(DiscrepancyStatus.valueOf(status.toUpperCase()), pageable);
            }
            return discrepancies.findAll(pageable);
        }

        // Site manager: the store is only reachable via a lazy chain, not a query-able column,
        // so this has to filter in memory rather than push the restriction into the query.
        var all = discrepancies.findAll(PageRequest.of(0, Integer.MAX_VALUE, org.springframework.data.domain.Sort.by("createdAt").descending()))
                .stream()
                .filter(d -> status == null || d.getStatus() == DiscrepancyStatus.valueOf(status.toUpperCase()))
                .filter(d -> { Store s = storeOf(d); return s != null && allowedStoreIds.contains(s.getId()); })
                .toList();
        int total = all.size(), from = Math.min(page * size, total), to = Math.min(from + size, total);
        return new PageImpl<>(all.subList(from, to), PageRequest.of(page, size), total);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER','SITE_STORE_MANAGER')")
    public Discrepancy get(@PathVariable UUID id, @AuthenticationPrincipal String email) {
        Discrepancy d = discrepancies.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        java.util.List<UUID> allowed = siteManagerAllowedStoreIds(email);
        if (allowed != null) {
            Store s = storeOf(d);
            if (s == null || !allowed.contains(s.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only view discrepancies for your own store.");
            }
        }
        return d;
    }

    /** See IssuanceController for why this is necessary with open-in-view=false. */
    private void resolveLazy(Discrepancy d) {
        d.getItem().getName();
        if (d.getReceipt() != null) {
            MaterialRequest mr = d.getReceipt().getMaterialRequest();
            if (mr.getProject() != null) mr.getProject().getCode();
            mr.getSourceStore().getName();
            mr.getRequestingStore().getName();
        } else if (d.getGrn() != null) {
            d.getGrn().getReferenceNumber();
        } else if (d.getStockReturn() != null) {
            d.getStockReturn().getId();
        } else if (d.getStockCount() != null) {
            d.getStockCount().getId();
            if (d.getStockCount().getStore() != null) d.getStockCount().getStore().getName();
        }
        if (d.getResolvedBy() != null) d.getResolvedBy().getFullName();
    }

    /**
     * Resolve a discrepancy and release the stock that was frozen when it was
     * opened. "recovered" means the missing stock was actually found (it's
     * returned to on-hand); otherwise it's a permanent write-off.
     */
    @PostMapping("/{id}/resolve")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER')")
    @Transactional
    public Discrepancy resolve(@PathVariable UUID id, @RequestBody ResolveRequest req,
                               @AuthenticationPrincipal String email) {
        AppUser resolver = users.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        Discrepancy discrepancy = discrepancies.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        // Stock-count variances never actually freeze stock (there's no in-transit or
        // GRN movement to freeze — the "variance" is just a physical-vs-system mismatch
        // recorded on the count line). The real correction for those happens via
        // StockCountController.raiseAdjustment(), which calls inventory.adjustTo()
        // directly and auto-resolves the linked discrepancy itself. Calling
        // releaseFrozen() here for a count-sourced discrepancy would fail with
        // "Insufficient frozen stock" (nothing was ever frozen), so require the
        // adjustment to have been raised first instead of touching inventory here.
        if (discrepancy.getStockCount() != null) {
            boolean adjustmentRaised = discrepancy.getStockCount().getLines().stream()
                    .anyMatch(line -> line != null
                            && line.getItem().getId().equals(discrepancy.getItem().getId())
                            && line.getStatus() == com.izonehub.stores.count.StockCountLineStatus.ADJUSTMENT_RAISED);
            if (!adjustmentRaised) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "This discrepancy came from a stock count — raise a stock adjustment for the "
                                + "item on that count first (Stock Counts screen), then it will resolve automatically.");
            }
            discrepancy.resolve(resolver, req.resolutionNotes());
            Discrepancy saved = discrepancies.save(discrepancy);
            resolveLazy(saved);
            auditLog.record("DISCREPANCY", saved.getId().toString(), "RESOLVED",
                    "Resolved by " + resolver.getEmail() + " (already corrected via stock adjustment)",
                    resolver.getEmail());
            return saved;
        }

        discrepancy.resolve(resolver, req.resolutionNotes());
        com.izonehub.stores.store.Store store;
        if (discrepancy.getReceipt() != null) {
            store = discrepancy.getReceipt().getMaterialRequest().getSourceStore();
        } else if (discrepancy.getGrn() != null) {
            store = discrepancy.getGrn().getExpectedReceipt().getStore();
        } else {
            store = discrepancy.getStockReturn().getStore();
        }

        inventory.releaseFrozen(
                store,
                discrepancy.getItem(),
                discrepancy.getFrozenQuantity(),
                req.recovered());
        Discrepancy saved = discrepancies.save(discrepancy);
        resolveLazy(saved);
        
        auditLog.record("DISCREPANCY", saved.getId().toString(), "RESOLVED",
                "Resolved by " + resolver.getEmail() + " (" + (req.recovered() ? "Recovered" : "Written off") + ")",
                resolver.getEmail());
                
        return saved;
    }

    public record ResolveRequest(@NotBlank String resolutionNotes, boolean recovered) {}
}
