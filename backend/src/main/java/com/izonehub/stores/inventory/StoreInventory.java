package com.izonehub.stores.inventory;

import com.izonehub.stores.common.BaseEntity;
import com.izonehub.stores.item.Item;
import com.izonehub.stores.store.Store;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
    private BigDecimal quantityFrozen = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantityDamaged = BigDecimal.ZERO;

    @Column(nullable = false)
    private Instant lastUpdated = Instant.now();

    protected StoreInventory() {
    }

    public StoreInventory(Store store, Item item) {
        this.store = store;
        this.item = item;
    }

    public Store getStore() { return store; }
    public Item getItem() { return item; }
    public BigDecimal getQuantityOnHand() { return quantityOnHand; }
    public BigDecimal getQuantityInTransit() { return quantityInTransit; }
    public BigDecimal getQuantityFrozen() { return quantityFrozen; }
    public BigDecimal getQuantityDamaged() { return quantityDamaged; }

    public void receiveUsable(BigDecimal quantity) {
        requirePositive(quantity);
        quantityOnHand = quantityOnHand.add(quantity);
        touch();
    }

    public void receiveDamaged(BigDecimal quantity) {
        requirePositive(quantity);
        quantityDamaged = quantityDamaged.add(quantity);
        touch();
    }

    public void dispatch(BigDecimal quantity) {
        requirePositive(quantity);
        if (quantityOnHand.compareTo(quantity) < 0) {
            throw new IllegalStateException("Insufficient on-hand stock");
        }
        quantityOnHand = quantityOnHand.subtract(quantity);
        quantityInTransit = quantityInTransit.add(quantity);
        touch();
    }

    public void receiveFromTransit(BigDecimal quantity) {
        completeTransit(quantity);
        quantityOnHand = quantityOnHand.add(quantity);
        touch();
    }

    public void completeTransit(BigDecimal quantity) {
        requirePositive(quantity);
        if (quantityInTransit.compareTo(quantity) < 0) {
            throw new IllegalStateException("Insufficient in-transit stock");
        }
        quantityInTransit = quantityInTransit.subtract(quantity);
        touch();
    }

    public void freezeTransitVariance(BigDecimal quantity) {
        requirePositive(quantity);
        if (quantityInTransit.compareTo(quantity) < 0) {
            throw new IllegalStateException("Insufficient in-transit stock");
        }
        quantityInTransit = quantityInTransit.subtract(quantity);
        quantityFrozen = quantityFrozen.add(quantity);
        touch();
    }

    private static void requirePositive(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
    }

    private void touch() {
        lastUpdated = Instant.now();
    }
}
