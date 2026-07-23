package com.izonehub.stores.inventory;

import com.izonehub.stores.common.BaseEntity;
import com.izonehub.stores.item.Item;
import com.izonehub.stores.store.Store;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"store_id", "item_id"}))
public class StoreInventory extends BaseEntity {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Store store;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Item item;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantityOnHand = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantityInTransit = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantityReserved = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantityFrozen = BigDecimal.ZERO;

    /** Quantity received in damaged condition (e.g. from a GRN with DAMAGED lines). */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantityDamaged = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantityConsumed = BigDecimal.ZERO;

    @Column(nullable = false)
    private Instant lastUpdated = Instant.now();

    protected StoreInventory() {}

    public StoreInventory(Store store, Item item) {
        this.store = store;
        this.item = item;
    }

    // --- Accessors ---

    public Store getStore() { return store; }
    public Item getItem() { return item; }
    public BigDecimal getQuantityOnHand() { return quantityOnHand; }
    public BigDecimal getQuantityInTransit() { return quantityInTransit; }
    public BigDecimal getQuantityFrozen() { return quantityFrozen; }
    public BigDecimal getQuantityDamaged() { return quantityDamaged; }
    public BigDecimal getQuantityReserved() { return quantityReserved; }
    public BigDecimal getQuantityConsumed() { return quantityConsumed; }
    public Instant getLastUpdated() { return lastUpdated; }

    // --- Mutations ---

    /** Receive usable stock directly onto the shelf. */
    public void receiveUsable(BigDecimal qty) {
        requirePositive(qty);
        quantityOnHand = quantityOnHand.add(qty);
        touch();
    }

    /** Consume stock (deduct from on-hand and add to consumed). */
    public void consume(BigDecimal qty) {
        requirePositive(qty);
        if (quantityOnHand.compareTo(qty) < 0) throw new IllegalStateException("Insufficient available stock to consume");
        quantityOnHand = quantityOnHand.subtract(qty);
        quantityConsumed = quantityConsumed.add(qty);
        touch();
    }

    /** Record damaged stock received from a supplier — does not add to on-hand. */
    public void receiveDamaged(BigDecimal qty) {
        requirePositive(qty);
        quantityDamaged = quantityDamaged.add(qty);
        touch();
    }

    /** Move stock from on-hand into in-transit when dispatching to a site store. */
    public void dispatch(BigDecimal qty) {
        requirePositive(qty);
        if (getQuantityAvailable().compareTo(qty) < 0) throw new IllegalStateException("Insufficient available stock");
        quantityOnHand = quantityOnHand.subtract(qty);
        quantityInTransit = quantityInTransit.add(qty);
        touch();
    }

    /** Mark stock as reserved when a material request is approved. */
    public void reserve(BigDecimal qty) {
        requirePositive(qty);
        if (getQuantityAvailable().compareTo(qty) < 0) throw new IllegalStateException("Insufficient available stock");
        quantityReserved = quantityReserved.add(qty);
        touch();
    }

    /** Un-reserve stock (e.g. if an approved request is later cancelled/rejected). */
    public void unreserve(BigDecimal qty) {
        requirePositive(qty);
        if (quantityReserved.compareTo(qty) < 0) throw new IllegalStateException("Insufficient reserved stock");
        quantityReserved = quantityReserved.subtract(qty);
        touch();
    }

    /** Dispatch reserved stock. Moves from on-hand to in-transit, and reduces reserved. */
    public void dispatchReserved(BigDecimal qty) {
        requirePositive(qty);
        if (quantityReserved.compareTo(qty) < 0) throw new IllegalStateException("Insufficient reserved stock");
        if (quantityOnHand.compareTo(qty) < 0) throw new IllegalStateException("Insufficient on-hand stock");
        quantityOnHand = quantityOnHand.subtract(qty);
        quantityReserved = quantityReserved.subtract(qty);
        quantityInTransit = quantityInTransit.add(qty);
        touch();
    }

    public BigDecimal getQuantityAvailable() {
        return quantityOnHand.subtract(quantityReserved);
    }



    /**
     * Confirm transit arrival: remove from in-transit, add to on-hand.
     */
    public void receiveFromTransit(BigDecimal qty) {
        requirePositive(qty);
        if (quantityInTransit.compareTo(qty) >= 0) {
            quantityInTransit = quantityInTransit.subtract(qty);
        } else {
            quantityInTransit = BigDecimal.ZERO;
        }
        touch();
    }

    /**
     * Move the unaccounted variance portion from in-transit to frozen
     * while a discrepancy investigation is open.
     */
    public void freezeTransitVariance(BigDecimal qty) {
        requirePositive(qty);
        if (quantityInTransit.compareTo(qty) < 0) throw new IllegalStateException("Insufficient in-transit stock");
        quantityInTransit = quantityInTransit.subtract(qty);
        quantityFrozen = quantityFrozen.add(qty);
        touch();
    }

    /**
     * Move unaccounted variance from incoming supplier expected receipt directly to frozen
     * (Expected receipts do not utilize in-transit state).
     */
    public void freezeGrnVariance(BigDecimal qty) {
        requirePositive(qty);
        quantityFrozen = quantityFrozen.add(qty);
        touch();
    }

    /**
     * Release previously-frozen stock once a discrepancy is resolved.
     * If {@code recovered} is true the stock is found to genuinely exist and is
     * returned to on-hand; otherwise it is a permanent write-off and simply
     * leaves frozen without landing anywhere else.
     */
    public void releaseFrozen(BigDecimal qty, boolean recovered) {
        requirePositive(qty);
        if (quantityFrozen.compareTo(qty) < 0) throw new IllegalStateException("Insufficient frozen stock");
        quantityFrozen = quantityFrozen.subtract(qty);
        if (recovered) quantityOnHand = quantityOnHand.add(qty);
        touch();
    }

    /**
     * Directly set on-hand quantity to a new value following a physical stock
     * count. Used by stock adjustments raised off the back of a count variance.
     */
    public void adjustTo(BigDecimal newQuantity) {
        if (newQuantity == null || newQuantity.signum() < 0) throw new IllegalArgumentException("Quantity cannot be negative");
        quantityOnHand = newQuantity;
        touch();
    }

    // --- Helpers ---

    private static void requirePositive(BigDecimal qty) {
        if (qty == null || qty.signum() <= 0) throw new IllegalArgumentException("Quantity must be positive");
    }

    private void touch() { lastUpdated = Instant.now(); }
}
