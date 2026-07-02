package com.izonehub.stores.dashboard;

import com.izonehub.stores.inventory.InventoryRepository;
import com.izonehub.stores.movement.DiscrepancyRepository;
import com.izonehub.stores.movement.DiscrepancyStatus;
import com.izonehub.stores.receipt.ExpectedReceiptRepository;
import com.izonehub.stores.receipt.ExpectedReceiptStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class DashboardService {
    private final InventoryRepository inventory;
    private final DiscrepancyRepository discrepancies;
    private final ExpectedReceiptRepository expectedReceipts;

    public DashboardService(InventoryRepository inventory, DiscrepancyRepository discrepancies, ExpectedReceiptRepository expectedReceipts) {
        this.inventory = inventory;
        this.discrepancies = discrepancies;
        this.expectedReceipts = expectedReceipts;
    }

    @Transactional(readOnly = true)
    public DashboardSummary summary() {
        long lowStock = inventory.findAll().stream()
                .filter(inv -> inv.getQuantityOnHand().compareTo(inv.getItem().getReorderThreshold()) <= 0)
                .count();
        long inTransit = inventory.findAll().stream()
                .filter(inv -> inv.getQuantityInTransit().compareTo(BigDecimal.ZERO) > 0)
                .count();
        long openDiscrepancies = discrepancies.findByStatusAndCreatedAtBefore(DiscrepancyStatus.OPEN, java.time.Instant.now()).size();
        long overdue = expectedReceipts.findByStatusAndExpectedDateBefore(ExpectedReceiptStatus.PENDING, LocalDate.now()).size();
        return new DashboardSummary(lowStock, openDiscrepancies, inTransit, overdue);
    }
}
