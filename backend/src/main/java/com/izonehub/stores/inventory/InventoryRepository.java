package com.izonehub.stores.inventory;

import com.izonehub.stores.item.Item;
import com.izonehub.stores.store.Store;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryRepository extends JpaRepository<StoreInventory, UUID> {

    Optional<StoreInventory> findByStoreAndItem(Store store, Item item);
    List<StoreInventory> findByStoreAndQuantityOnHandGreaterThan(Store store, java.math.BigDecimal qty);

    /**
     * All inventory rows for a given store, with store and item eagerly fetched
     * in one query to avoid N+1 when iterating in DashboardService / InventoryReportService.
     */
    @Query("select si from StoreInventory si join fetch si.store join fetch si.item where si.store = :store")
    List<StoreInventory> findByStoreEager(@Param("store") Store store);

    /**
     * All inventory rows with store and item eagerly fetched.
     * Use this instead of the inherited findAll() in any service that reads both
     * si.getStore() and si.getItem() in a loop.
     */
    @Query("select si from StoreInventory si join fetch si.store join fetch si.item")
    List<StoreInventory> findAllEager();
}
