package com.izonehub.stores.count;

import com.izonehub.stores.issuance.AdjustmentReasonCode;
import com.izonehub.stores.issuance.StockAdjustment;
import com.izonehub.stores.issuance.StockAdjustmentCommandService;
import com.izonehub.stores.store.Store;
import com.izonehub.stores.store.StoreRepository;
import com.izonehub.stores.user.AppUser;
import com.izonehub.stores.user.UserRepository;
import com.izonehub.stores.audit.AuditLogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/stock-counts")
public class StockCountController {

    private final StockCountRepository counts;
    private final StoreRepository stores;
    private final UserRepository users;
    private final StockCountCommandService svc;
    private final StockAdjustmentCommandService adjustments;
    private final AuditLogService auditLog;
    private final com.izonehub.stores.reporting.StockCountPdfService pdfService;
    private final com.izonehub.stores.movement.DiscrepancyRepository discrepancyRepo;
    private final com.izonehub.stores.movement.DiscrepancyStatus discrepancyOpenStatus = com.izonehub.stores.movement.DiscrepancyStatus.OPEN;

    public StockCountController(StockCountRepository counts, StoreRepository stores, UserRepository users,
                                StockCountCommandService svc, StockAdjustmentCommandService adjustments,
                                AuditLogService auditLog, com.izonehub.stores.reporting.StockCountPdfService pdfService,
                                com.izonehub.stores.movement.DiscrepancyRepository discrepancyRepo) {
        this.counts = counts;
        this.stores = stores;
        this.users = users;
        this.svc = svc;
        this.adjustments = adjustments;
        this.auditLog = auditLog;
        this.pdfService = pdfService;
        this.discrepancyRepo = discrepancyRepo;
    }

    @GetMapping
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER','SITE_STORE_MANAGER')")
    public Page<StockCount> list(@RequestParam(defaultValue = "0")  int page,
                                 @RequestParam(defaultValue = "20") int size,
                                 @AuthenticationPrincipal String email) {
        AppUser user = users.findByEmail(email).orElse(null);
        java.util.List<java.util.UUID> storeIds = null;
        if (user != null) {
            boolean isSiteManager = user.getRoles().contains(com.izonehub.stores.user.Role.SITE_STORE_MANAGER)
                                    && !user.getRoles().contains(com.izonehub.stores.user.Role.SYSTEM_ADMINISTRATOR)
                                    && !user.getRoles().contains(com.izonehub.stores.user.Role.CENTRAL_STORE_MANAGER);
            if (isSiteManager) {
                java.util.List<Store> managedStores = stores.findByManager_Id(user.getId());
                if (managedStores.isEmpty()) {
                    return new PageImpl<>(java.util.List.of(), PageRequest.of(page, size), 0);
                }
                storeIds = managedStores.stream().map(Store::getId).toList();
            }
        }
        final java.util.List<java.util.UUID> finalStoreIds = storeIds;

        var all = counts.findAll().stream()
                .filter(c -> finalStoreIds == null || finalStoreIds.contains(c.getStore().getId()))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .toList();
        all.forEach(this::resolveLazy);
        int total = all.size(), from = Math.min(page * size, total), to = Math.min(from + size, total);
        return new PageImpl<>(all.subList(from, to), PageRequest.of(page, size), total);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER','SITE_STORE_MANAGER')")
    public StockCount get(@PathVariable UUID id) {
        StockCount count = counts.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        resolveLazy(count);
        return count;
    }

    /** Initiate a count — snapshots current system quantities for every item at the store. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER','SITE_STORE_MANAGER')")
    public StockCount initiate(@Valid @RequestBody InitiateRequest req, @AuthenticationPrincipal String email) {
        AppUser initiatedBy = users.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        Store store = stores.findById(req.storeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Store not found"));
                
        boolean isSiteManager = initiatedBy.getRoles().contains(com.izonehub.stores.user.Role.SITE_STORE_MANAGER)
                                && !initiatedBy.getRoles().contains(com.izonehub.stores.user.Role.SYSTEM_ADMINISTRATOR)
                                && !initiatedBy.getRoles().contains(com.izonehub.stores.user.Role.CENTRAL_STORE_MANAGER);
        if (isSiteManager) {
            java.util.List<Store> managedStores = stores.findByManager_Id(initiatedBy.getId());
            if (managedStores.stream().noneMatch(s -> s.getId().equals(store.getId()))) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not manage this store");
            }
        }

        StockCount count = svc.initiate(store, initiatedBy);
        auditLog.record("STOCK_COUNT", count.getId().toString(), "INITIATED",
                "Initiated by " + initiatedBy.getEmail() + " for store '" + store.getName() + "'",
                initiatedBy.getEmail());
        resolveLazy(count);
        return count;
    }

    /** Enter a physical count for one line. Flags variance automatically. */
    @PutMapping("/{countId}/lines/{lineId}")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER','SITE_STORE_MANAGER')")
    @Transactional
    public StockCount enterCount(@PathVariable UUID countId, @PathVariable UUID lineId,
                                 @RequestBody EnterCountRequest req) {
        StockCount count = counts.findById(countId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        StockCountLine line = count.getLines().stream()
                .filter(java.util.Objects::nonNull)
                .filter(l -> l.getId().equals(lineId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Line not found on this count"));
        line.enterPhysicalCount(req.physicalQuantity());
        count.completeIfResolved();
        StockCount saved = counts.save(count);
        String email = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();

        // Auto-create a Discrepancy when this line has a non-zero variance.
        // Skip if a stock-count discrepancy already exists for this item on this count
        // (prevents duplicates when re-entering a count for the same line).
        java.math.BigDecimal variance = line.getVarianceQuantity();
        if (variance != null && variance.compareTo(java.math.BigDecimal.ZERO) != 0) {
            boolean alreadyExists = discrepancyRepo.findAll().stream()
                    .anyMatch(d -> d.getStockCount() != null
                            && d.getStockCount().getId().equals(saved.getId())
                            && d.getItem().getId().equals(line.getItem().getId())
                            && d.getStatus() == com.izonehub.stores.movement.DiscrepancyStatus.OPEN);
            if (!alreadyExists) {
                discrepancyRepo.save(new com.izonehub.stores.movement.Discrepancy(
                        saved, line.getItem(),
                        line.getSystemQuantitySnapshot(), line.getPhysicalQuantity()));
                auditLog.record("DISCREPANCY", saved.getId().toString(), "STOCK_COUNT_VARIANCE",
                        "Variance of " + variance + " detected for item '" + line.getItem().getName()
                                + "' during stock count at store '" + saved.getStore().getName() + "'. Discrepancy raised automatically.",
                        email);
            }
        }

        auditLog.record("STOCK_COUNT", saved.getId().toString(), "COUNT_ENTERED",
                "Entered physical count of " + req.physicalQuantity() + " for item '" + line.getItem().getName() + "'",
                email);
        return saved;
    }

    /** Flag a line for recount instead of accepting the variance. */
    @PostMapping("/{countId}/lines/{lineId}/recount")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER','SITE_STORE_MANAGER')")
    @Transactional
    public StockCount flagRecount(@PathVariable UUID countId, @PathVariable UUID lineId) {
        StockCount count = counts.findById(countId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        StockCountLine line = count.getLines().stream()
                .filter(java.util.Objects::nonNull)
                .filter(l -> l.getId().equals(lineId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Line not found on this count"));
        line.flagForRecount();
        StockCount saved = counts.save(count);
        String email = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
        auditLog.record("STOCK_COUNT", saved.getId().toString(), "RECOUNT_FLAGGED",
                "Flagged item '" + line.getItem().getName() + "' for recount",
                email);
        return saved;
    }

    /**
     * Raise a stock adjustment off the back of a variance line — this is the
     * step that was completely missing: a count could flag
     * VARIANCE_REQUIRES_ACTION but nothing could ever move it forward, so the
     * count could never be completed.
     */
    @PostMapping("/{countId}/lines/{lineId}/adjustment")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER')")
    @Transactional
    public StockAdjustment raiseAdjustment(@PathVariable UUID countId, @PathVariable UUID lineId,
                                           @RequestBody(required = false) AdjustmentNotesRequest req,
                                           @AuthenticationPrincipal String email) {
        AppUser performedBy = users.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        StockCount count = counts.findById(countId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        StockCountLine line = count.getLines().stream()
                .filter(java.util.Objects::nonNull)
                .filter(l -> l.getId().equals(lineId)).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Line not found on this count"));
        if (line.getPhysicalQuantity() == null)
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Line has not been physically counted yet");

        String reference = "ADJ-" + line.getId().toString().substring(0, 8).toUpperCase();
        String notes = (req == null || req.notes() == null)
                ? "Raised from stock count " + count.getId()
                : req.notes();
        StockAdjustment adjustment = new StockAdjustment(reference, count.getStore(), line.getItem(), performedBy,
                AdjustmentReasonCode.COUNT_VARIANCE, line.getSystemQuantitySnapshot(), line.getPhysicalQuantity(),
                notes, BigDecimal.ZERO);
        StockAdjustment saved = adjustments.raise(adjustment, performedBy);

        line.markAdjustmentRaised();
        count.completeIfResolved();
        counts.save(count);
        auditLog.record("STOCK_COUNT", count.getId().toString(), "ADJUSTMENT_RAISED",
                "Raised adjustment for item '" + line.getItem().getName() + "' due to variance",
                performedBy.getEmail());
        return saved;
    }

    public record InitiateRequest(@NotNull UUID storeId) {}
    public record EnterCountRequest(@NotNull BigDecimal physicalQuantity) {}
    public record AdjustmentNotesRequest(String notes) {}

    @GetMapping("/{id}/audit-report")
    @Transactional
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER','SITE_STORE_MANAGER')")
    public ResponseEntity<byte[]> downloadAuditReport(@PathVariable UUID id) {
        StockCount count = counts.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        byte[] pdf = pdfService.generate(count);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"stock-count-audit-" + count.getId() + ".pdf\"")
                .body(pdf);
    }

    @GetMapping("/{id}/full-audit-pdf")
    @Transactional
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER','SITE_STORE_MANAGER')")
    public ResponseEntity<byte[]> downloadFullAuditReport(@PathVariable UUID id) {
        StockCount count = counts.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        byte[] pdf = pdfService.generateFullAudit(count);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"stock-count-full-audit-" + id + ".pdf\"")
                .body(pdf);
    }

    private void resolveLazy(StockCount count) {
        if (count.getStore() != null) count.getStore().getName();
        if (count.getInitiatedBy() != null) count.getInitiatedBy().getFullName();
        if (count.getLines() != null) {
            count.getLines().forEach(l -> {
                if (l != null && l.getItem() != null) l.getItem().getName();
            });
        }
    }
}
