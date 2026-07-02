package com.izonehub.stores.issuance;

import com.izonehub.stores.common.BaseEntity;
import com.izonehub.stores.item.Item;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
public class StockReturnLine extends BaseEntity {
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private StockReturn stockReturn;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Item item;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReturnCondition condition;

    protected StockReturnLine() {}

    public StockReturnLine(Item item, BigDecimal quantity, ReturnCondition condition) {
        if (quantity == null || quantity.signum() <= 0) throw new IllegalArgumentException("Return quantity must be positive");
        this.item = item;
        this.quantity = quantity;
        this.condition = condition;
    }

    void attachTo(StockReturn stockReturn) { this.stockReturn = stockReturn; }
    public Item getItem() { return item; }
    public BigDecimal getQuantity() { return quantity; }
    public ReturnCondition getCondition() { return condition; }
}
