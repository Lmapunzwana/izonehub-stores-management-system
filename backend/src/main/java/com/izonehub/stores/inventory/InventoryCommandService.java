package com.izonehub.stores.inventory;

import com.izonehub.stores.item.Item;
import com.izonehub.stores.store.Store;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

@Service
public class InventoryCommandService {
    private final InventoryRepository repository;
    private final LowStockPolicy lowStockPolicy;

    public InventoryCommandService(InventoryRepository repository, LowStockPolicy lowStockPolicy) {
        this.repository = repository;
        this.lowStockPolicy = lowStockPolicy;
    }

    @Transactional
    public StoreInventory receive(Store store, Item item, BigDecimal quantity) {
        StoreInventory inventory = findOrCreate(store, item);
        inventory.receiveUsable(quantity);
        return repository.save(inventory);
    }

    @Transactional
    public StoreInventory receiveDamaged(Store store, Item item, BigDecimal quantity) {
        StoreInventory inventory = findOrCreate(store, item);
        inventory.receiveDamaged(quantity);
        return repository.save(inventory);
    }

    @Transactional
    public StoreInventory dispatch(Store store, Item item, BigDecimal quantity) {
        StoreInventory inventory = findOrCreate(store, item);
        inventory.dispatch(quantity);
        StoreInventory saved = repository.save(inventory);
        lowStockPolicy.evaluate(saved);
        return saved;
    }

    private StoreInventory findOrCreate(Store store, Item item) {
        return repository.findByStoreAndItem(store, item).orElseGet(() -> new StoreInventory(store, item));
    }
}
