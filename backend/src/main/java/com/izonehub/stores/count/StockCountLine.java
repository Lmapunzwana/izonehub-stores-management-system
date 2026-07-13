package com.izonehub.stores.count;

import com.izonehub.stores.common.BaseEntity;
import com.izonehub.stores.item.Item;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
public class StockCountLine extends BaseEntity {
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private StockCount stockCount;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Item item;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal systemQuantitySnapshot;

    @Column(precision = 19, scale = 4)
    private BigDecimal physicalQuantity;

    @Column(precision = 19, scale = 4)
    private BigDecimal varianceQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StockCountLineStatus status = StockCountLineStatus.PENDING_COUNT;

    protected StockCountLine() {}

    public StockCountLine(Item item, BigDecimal systemQuantitySnapshot) {
        this.item = item;
        this.systemQuantitySnapshot = systemQuantitySnapshot;
    }

    void attachTo(StockCount stockCount) { this.stockCount = stockCount; }
    public Item getItem() { return item; }
    public BigDecimal getSystemQuantitySnapshot() { return systemQuantitySnapshot; }
    public BigDecimal getPhysicalQuantity() { return physicalQuantity; }
    public BigDecimal getVarianceQuantity() { return varianceQuantity; }
    public StockCountLineStatus getStatus() { return status; }

    public void enterPhysicalCount(BigDecimal physicalQuantity) {
        if (physicalQuantity == null || physicalQuantity.signum() < 0) throw new IllegalArgumentException("Physical quantity cannot be negative");
        this.physicalQuantity = physicalQuantity;
        this.varianceQuantity = physicalQuantity.subtract(systemQuantitySnapshot);
        this.status = varianceQuantity.signum() == 0 ? StockCountLineStatus.ZERO_VARIANCE_CONFIRMED : StockCountLineStatus.VARIANCE_REQUIRES_ACTION;
    }

    public void flagForRecount() { status = StockCountLineStatus.RECOUNT_REQUIRED; }
    public void markAdjustmentRaised() { status = StockCountLineStatus.ADJUSTMENT_RAISED; }
}
