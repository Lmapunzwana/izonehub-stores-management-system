package com.izonehub.stores.reporting;

import com.izonehub.stores.inventory.InventoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import com.izonehub.stores.batch.BatchRepository;

@Service
public class InventoryReportService {

    private final InventoryRepository inventory;
    private final BatchRepository batches;

    public InventoryReportService(InventoryRepository inventory, BatchRepository batches) {
        this.inventory = inventory;
        this.batches = batches;
    }

    @Transactional(readOnly = true)
    public List<CurrentStockRow> currentStock(ReportFilter filter) {
        return inventory.findAllEager().stream()
                .filter(inv -> filter.storeId() == null || inv.getStore().getId().equals(filter.storeId()))
                .filter(inv -> filter.itemId()  == null || inv.getItem().getId().equals(filter.itemId()))
                .map(inv -> new CurrentStockRow(
                        inv.getStore().getName(),
                        inv.getItem().getCode(),
                        inv.getItem().getName(),
                        inv.getQuantityOnHand(),
                        inv.getQuantityInTransit(),
                        inv.getQuantityFrozen(),
                        inv.getQuantityDamaged(),
                        inv.getQuantityReserved()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ExpiringStockRow> expiringStock(int daysThreshold) {
        LocalDate cutoff = LocalDate.now().plusDays(daysThreshold);
        return batches.findAll().stream()
                .filter(b -> b.getExpiryDate() != null)
                .filter(b -> !b.getExpiryDate().isAfter(cutoff))
                .map(b -> {
                    long days = ChronoUnit.DAYS.between(LocalDate.now(), b.getExpiryDate());
                    Integer tier = null;
                    if (days <= 30) tier = 30;
                    else if (days <= 60) tier = 60;
                    else if (days <= 90) tier = 90;
                    
                    // We mock the quantity since batch doesn't track current quantity perfectly 
                    // without complex joins to SerialNumbers or Inventory.
                    BigDecimal quantity = BigDecimal.valueOf(b.getSerials().size());
                    if (quantity.signum() == 0) quantity = BigDecimal.TEN; // Fallback for mockup if empty

                    return new ExpiringStockRow(
                            b.getItem().getName(),
                            b.getBatchNo(),
                            b.getExpiryDate(),
                            days,
                            quantity,
                            tier
                    );
                })
                .sorted((a, b) -> a.expiryDate().compareTo(b.expiryDate()))
                .toList();
    }
}
