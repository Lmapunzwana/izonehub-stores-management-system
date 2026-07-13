package com.izonehub.stores.inventory;

import com.izonehub.stores.common.BaseEntity;
import com.izonehub.stores.item.Item;
import com.izonehub.stores.store.Store;
import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * Per-store reorder threshold override.
 * When present, this value takes precedence over Item.reorderThreshold
 * when LowStockPolicy evaluates a StoreInventory row.
 */
@Entity
@Table(
    name = "low_stock_threshold",
    uniqueConstraints = @UniqueConstraint(columnNames = {"store_id", "item_id"})
)
public class LowStockThreshold extends BaseEntity {

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Store store;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Item item;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal threshold;

    protected LowStockThreshold() {}

    public LowStockThreshold(Store store, Item item, BigDecimal threshold) {
        this.store     = store;
        this.item      = item;
        this.threshold = threshold;
    }

    public Store      getStore()     { return store; }
    public Item       getItem()      { return item; }
    public BigDecimal getThreshold() { return threshold; }

    public void updateThreshold(BigDecimal threshold) {
        if (threshold == null || threshold.signum() < 0)
            throw new IllegalArgumentException("Threshold cannot be negative");
        this.threshold = threshold;
    }
}
