package com.izonehub.stores.movement;

import com.izonehub.stores.inventory.InventoryCommandService;
import com.izonehub.stores.user.AppUser;
import com.izonehub.stores.user.UserRepository;
import com.izonehub.stores.audit.AuditLogService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
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
    private final InventoryCommandService inventory;
    private final AuditLogService auditLog;

    public DiscrepancyController(DiscrepancyRepository discrepancies, UserRepository users, InventoryCommandService inventory, AuditLogService auditLog) {
        this.discrepancies = discrepancies;
        this.users = users;
        this.inventory = inventory;
        this.auditLog = auditLog;
    }

    @GetMapping
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER','SITE_STORE_MANAGER')")
    public Page<Discrepancy> list(@RequestParam(defaultValue = "0")  int page,
                                  @RequestParam(defaultValue = "20") int size,
                                  @RequestParam(required = false)    String status) {
        var pageable = PageRequest.of(page, size, org.springframework.data.domain.Sort.by("createdAt").descending());
        if (status != null) {
            return discrepancies.findByStatus(DiscrepancyStatus.valueOf(status.toUpperCase()), pageable);
        }
        return discrepancies.findAll(pageable);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER','SITE_STORE_MANAGER')")
    public Discrepancy get(@PathVariable UUID id) {
        return discrepancies.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
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

        discrepancy.resolve(resolver, req.resolutionNotes());
        com.izonehub.stores.store.Store store;
        if (discrepancy.getReceipt() != null) {
            store = discrepancy.getReceipt().getMaterialRequest().getSourceStore();
        } else if (discrepancy.getGrn() != null) {
            store = discrepancy.getGrn().getExpectedReceipt().getStore();
        } else {
            store = discrepancy.getStockReturn().getStore(); // Central Store confirming the return
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
