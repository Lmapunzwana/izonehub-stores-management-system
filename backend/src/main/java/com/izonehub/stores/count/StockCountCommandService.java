package com.izonehub.stores.count;

import com.izonehub.stores.inventory.InventoryRepository;
import com.izonehub.stores.store.Store;
import com.izonehub.stores.user.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockCountCommandService {
    private final StockCountRepository stockCounts;
    private final InventoryRepository inventory;

    public StockCountCommandService(StockCountRepository stockCounts, InventoryRepository inventory) {
        this.stockCounts = stockCounts;
        this.inventory = inventory;
    }

    @Transactional
    public StockCount initiate(Store store, AppUser initiatedBy) {
        StockCount count = new StockCount(store, initiatedBy);
        inventory.findAll().stream()
                .filter(inv -> inv.getStore().equals(store))
                .forEach(inv -> count.addLine(new StockCountLine(inv.getItem(), inv.getQuantityOnHand())));
        return stockCounts.save(count);
    }
}
