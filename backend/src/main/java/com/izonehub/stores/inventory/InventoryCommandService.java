package com.izonehub.stores.inventory;

import com.izonehub.stores.item.Item;
import com.izonehub.stores.store.Store;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;

@Service
public class InventoryCommandService {

    private final InventoryRepository repo;

    public InventoryCommandService(InventoryRepository repo) {
        this.repo = repo;
    }

    /** Receive usable (good condition) stock directly onto shelf — used by GRN and returns. */
    @Transactional
    public StoreInventory receive(Store store, Item item, BigDecimal qty) {
        StoreInventory inv = findOrCreate(store, item);
        inv.receiveUsable(qty);
        return repo.save(inv);
    }

    /** Consume stock (deduct from on-hand, add to consumed). */
    @Transactional
    public StoreInventory consume(Store store, Item item, BigDecimal qty) {
        StoreInventory inv = findOrCreate(store, item);
        inv.consume(qty);
        return repo.save(inv);
    }

    /** Receive damaged stock — increments quantityDamaged, not on-hand. Used by GRN DAMAGED lines. */
    @Transactional
    public StoreInventory receiveDamaged(Store store, Item item, BigDecimal qty) {
        StoreInventory inv = findOrCreate(store, item);
        inv.receiveDamaged(qty);
        return repo.save(inv);
    }

    /** Move stock from on-hand to in-transit when dispatching a material request. */
    @Transactional
    public StoreInventory dispatch(Store store, Item item, BigDecimal qty) {
        StoreInventory inv = findOrCreate(store, item);
        inv.dispatch(qty);
        return repo.save(inv);
    }

    /** Move stock from on-hand to reserved when a material request is approved. */
    @Transactional
    public StoreInventory reserve(Store store, Item item, BigDecimal qty) {
        StoreInventory inv = findOrCreate(store, item);
        inv.reserve(qty);
        return repo.save(inv);
    }

    /** Un-reserve stock (e.g. if rejected or cancelled). */
    @Transactional
    public StoreInventory unreserve(Store store, Item item, BigDecimal qty) {
        StoreInventory inv = findOrCreate(store, item);
        inv.unreserve(qty);
        return repo.save(inv);
    }

    /** Dispatch reserved stock. Moves from reserved to in-transit. */
    @Transactional
    public StoreInventory dispatchReserved(Store store, Item item, BigDecimal qty) {
        StoreInventory inv = findOrCreate(store, item);
        inv.dispatchReserved(qty);
        return repo.save(inv);
    }

    /**
     * Confirm clean transit arrival — remove from in-transit and add to on-hand.
     * Called per received line when materialRequest.receive() completes.
     */
    @Transactional
    public StoreInventory completeTransit(Store store, Item item, BigDecimal qty) {
        StoreInventory inv = findOrCreate(store, item);
        inv.receiveFromTransit(qty);
        return repo.save(inv);
    }

    /**
     * Move the unaccounted variance from in-transit to frozen while a
     * discrepancy investigation is open.
     */
    @Transactional
    public StoreInventory freezeTransitVariance(Store store, Item item, BigDecimal qty) {
        StoreInventory inv = findOrCreate(store, item);
        inv.freezeTransitVariance(qty);
        return repo.save(inv);
    }

    @Transactional
    public StoreInventory freezeGrnVariance(Store store, Item item, BigDecimal qty) {
        StoreInventory inv = findOrCreate(store, item);
        inv.freezeGrnVariance(qty);
        return repo.save(inv);
    }

    /** Release frozen stock once a discrepancy investigation is resolved. */
    @Transactional
    public StoreInventory releaseFrozen(Store store, Item item, BigDecimal qty, boolean recovered) {
        StoreInventory inv = repo.findByStoreAndItem(store, item)
                .orElseThrow(() -> new IllegalStateException("No inventory row to release frozen stock from"));
        inv.releaseFrozen(qty, recovered);
        return repo.save(inv);
    }

    /** Set on-hand quantity to a new value following a stock adjustment. */
    @Transactional
    public StoreInventory adjustTo(Store store, Item item, BigDecimal newQuantity) {
        StoreInventory inv = findOrCreate(store, item);
        inv.adjustTo(newQuantity);
        return repo.save(inv);
    }

    // --- helpers ---

    private StoreInventory findOrCreate(Store store, Item item) {
        return repo.findByStoreAndItem(store, item)
                   .orElseGet(() -> new StoreInventory(store, item));
    }
}
