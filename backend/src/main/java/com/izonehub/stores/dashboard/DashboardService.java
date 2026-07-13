package com.izonehub.stores.dashboard;

import com.izonehub.stores.inventory.InventoryRepository;
import com.izonehub.stores.movement.DiscrepancyRepository;
import com.izonehub.stores.movement.DiscrepancyStatus;
import com.izonehub.stores.movement.MaterialRequestRepository;
import com.izonehub.stores.movement.MaterialRequestStatus;
import com.izonehub.stores.receipt.ExpectedReceiptRepository;
import com.izonehub.stores.receipt.ExpectedReceiptStatus;
import com.izonehub.stores.receipt.GoodsReceivedNoteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private final InventoryRepository        inventory;
    private final DiscrepancyRepository      discrepancies;
    private final ExpectedReceiptRepository  expectedReceipts;
    private final GoodsReceivedNoteRepository grns;
    private final MaterialRequestRepository  materialRequests;

    public DashboardService(InventoryRepository inventory,
                            DiscrepancyRepository discrepancies,
                            ExpectedReceiptRepository expectedReceipts,
                            GoodsReceivedNoteRepository grns,
                            MaterialRequestRepository materialRequests) {
        this.inventory        = inventory;
        this.discrepancies    = discrepancies;
        this.expectedReceipts = expectedReceipts;
        this.grns             = grns;
        this.materialRequests = materialRequests;
    }

    // ── Operations summary (existing) ─────────────────────────────────────────

    @Transactional(readOnly = true)
    public DashboardSummary summary() {
        long lowStock = inventory.findAllEager().stream()
                .filter(inv -> inv.getQuantityOnHand().compareTo(inv.getItem().getReorderThreshold()) <= 0)
                .count();
        long inTransit = inventory.findAllEager().stream()
                .filter(inv -> inv.getQuantityInTransit().compareTo(BigDecimal.ZERO) > 0)
                .count();
        long openDiscrepancies = discrepancies
                .findByStatusAndCreatedAtBefore(DiscrepancyStatus.OPEN, Instant.now())
                .size();
        long overdue = expectedReceipts
                .findByStatusAndExpectedDateBefore(ExpectedReceiptStatus.AWAITING_GRN, LocalDate.now())
                .size();
        return new DashboardSummary(lowStock, openDiscrepancies, inTransit, overdue);
    }

    // ── Finance dashboard ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public FinanceDashboardSummary financeSummary() {
        var allInventory = inventory.findAllEager();

        // Total on-hand units (quantity-based proxy for value since no unit cost field yet)
        BigDecimal totalOnHandUnits = allInventory.stream()
                .map(inv -> inv.getQuantityOnHand())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Frozen units (discrepancy-held stock)
        BigDecimal frozenUnits = allInventory.stream()
                .map(inv -> inv.getQuantityFrozen())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // GRN count by month (last 6 months)
        DateTimeFormatter monthFmt = DateTimeFormatter.ofPattern("MMM yyyy").withZone(ZoneId.systemDefault());
        Map<String, Long> grnByMonth = grns.findAll().stream()
                .collect(Collectors.groupingBy(
                        grn -> monthFmt.format(grn.getCreatedAt()),
                        TreeMap::new,
                        Collectors.counting()));

        // Pending requests
        long pendingApprovals = materialRequests.findAll().stream()
                .filter(r -> r.getStatus() == MaterialRequestStatus.PENDING_APPROVAL)
                .count();

        long totalGrns = grns.count();

        return new FinanceDashboardSummary(
                totalOnHandUnits,
                frozenUnits,
                pendingApprovals,
                totalGrns,
                grnByMonth);
    }

    // ── Executive dashboard ───────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public ExecutiveDashboardSummary executiveSummary() {
        var allInventory = inventory.findAllEager();

        BigDecimal totalOnHandUnits = allInventory.stream()
                .map(inv -> inv.getQuantityOnHand())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal inTransitUnits = allInventory.stream()
                .map(inv -> inv.getQuantityInTransit())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long lowStockItems = allInventory.stream()
                .filter(inv -> inv.getQuantityOnHand().compareTo(inv.getItem().getReorderThreshold()) <= 0)
                .count();

        long openDiscrepancyCount = discrepancies
                .findByStatusAndCreatedAtBefore(DiscrepancyStatus.OPEN, Instant.now())
                .size();

        long pendingRequests = materialRequests.findAll().stream()
                .filter(r -> r.getStatus() == MaterialRequestStatus.PENDING_APPROVAL
                        || r.getStatus() == MaterialRequestStatus.APPROVED)
                .count();

        long overdueReceipts = expectedReceipts
                .findByStatusAndExpectedDateBefore(ExpectedReceiptStatus.AWAITING_GRN, LocalDate.now())
                .size();

        Map<String, Long> requestsByStatus = materialRequests.findAll().stream()
                .collect(Collectors.groupingBy(
                        r -> r.getStatus().name(),
                        Collectors.counting()));

        return new ExecutiveDashboardSummary(
                totalOnHandUnits,
                inTransitUnits,
                lowStockItems,
                openDiscrepancyCount,
                pendingRequests,
                overdueReceipts,
                requestsByStatus);
    }
}
