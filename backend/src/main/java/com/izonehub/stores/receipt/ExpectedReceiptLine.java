package com.izonehub.stores.receipt;

import com.izonehub.stores.common.BaseEntity;
import com.izonehub.stores.item.Item;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import java.math.BigDecimal;

@Entity
public class ExpectedReceiptLine extends BaseEntity {
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private ExpectedReceipt expectedReceipt;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Item item;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal expectedQuantity;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal receivedQuantity = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReceiptLineCondition condition = ReceiptLineCondition.GOOD;

    protected ExpectedReceiptLine() {
    }

    public ExpectedReceiptLine(Item item, BigDecimal expectedQuantity) {
        this.item = item;
        this.expectedQuantity = expectedQuantity;
    }

    void attachTo(ExpectedReceipt expectedReceipt) { this.expectedReceipt = expectedReceipt; }
    public Item getItem() { return item; }
    public BigDecimal getExpectedQuantity() { return expectedQuantity; }
    public BigDecimal getReceivedQuantity() { return receivedQuantity; }
    public ReceiptLineCondition getCondition() { return condition; }

    public void recordReceived(BigDecimal receivedQuantity, ReceiptLineCondition condition) {
        if (receivedQuantity == null || receivedQuantity.signum() < 0) {
            throw new IllegalArgumentException("Received quantity cannot be negative");
        }
        this.receivedQuantity = receivedQuantity;
        this.condition = condition;
    }

    public boolean hasVariance() {
        return condition != ReceiptLineCondition.GOOD || receivedQuantity.compareTo(expectedQuantity) != 0;
    }
}
