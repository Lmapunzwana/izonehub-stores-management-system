package com.izonehub.stores.inventory;

import com.izonehub.stores.item.Item;
import com.izonehub.stores.store.Store;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LowStockThresholdRepository extends JpaRepository<LowStockThreshold, UUID> {
    Optional<LowStockThreshold> findByStoreAndItem(Store store, Item item);
}
