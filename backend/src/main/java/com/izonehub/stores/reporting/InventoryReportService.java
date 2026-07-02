package com.izonehub.stores.reporting;

import com.izonehub.stores.inventory.InventoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class InventoryReportService {
    private final InventoryRepository inventory;

    public InventoryReportService(InventoryRepository inventory) {
        this.inventory = inventory;
    }

    @Transactional(readOnly = true)
    public List<CurrentStockRow> currentStock(ReportFilter filter) {
        return inventory.findAll().stream()
                .filter(inv -> filter.storeId() == null || inv.getStore().getId().equals(filter.storeId()))
                .filter(inv -> filter.itemId() == null || inv.getItem().getId().equals(filter.itemId()))
                .map(inv -> new CurrentStockRow(inv.getStore().getName(), inv.getItem().getCode(), inv.getItem().getName(),
                        inv.getQuantityOnHand(), inv.getQuantityInTransit(), inv.getQuantityFrozen(), inv.getQuantityDamaged()))
                .toList();
    }
}
