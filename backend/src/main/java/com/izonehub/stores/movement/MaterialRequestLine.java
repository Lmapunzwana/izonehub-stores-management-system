package com.izonehub.stores.movement;

import com.izonehub.stores.common.BaseEntity;
import com.izonehub.stores.item.Item;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
public class MaterialRequestLine extends BaseEntity {
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private MaterialRequest materialRequest;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Item item;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal requestedQuantity;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal approvedQuantity = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal dispatchedQuantity = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal receivedQuantity = BigDecimal.ZERO;

    protected MaterialRequestLine() {
    }

    public MaterialRequestLine(Item item, BigDecimal requestedQuantity) {
        requirePositive(requestedQuantity);
        this.item = item;
        this.requestedQuantity = requestedQuantity;
    }

    void attachTo(MaterialRequest materialRequest) { this.materialRequest = materialRequest; }
    public Item getItem() { return item; }
    public BigDecimal getRequestedQuantity() { return requestedQuantity; }
    public BigDecimal getApprovedQuantity() { return approvedQuantity; }
    public BigDecimal getDispatchedQuantity() { return dispatchedQuantity; }
    public BigDecimal getReceivedQuantity() { return receivedQuantity; }

    public void approve(BigDecimal quantity) {
        requireNonNegative(quantity);
        if (quantity.compareTo(requestedQuantity) > 0) {
            throw new IllegalArgumentException("Approved quantity cannot exceed requested quantity");
        }
        approvedQuantity = quantity;
    }

    public void dispatch(BigDecimal quantity) {
        requirePositive(quantity);
        if (quantity.compareTo(approvedQuantity) > 0) {
            throw new IllegalArgumentException("Dispatched quantity cannot exceed approved quantity");
        }
        dispatchedQuantity = quantity;
    }

    public void receive(BigDecimal quantity) {
        requireNonNegative(quantity);
        if (quantity.compareTo(dispatchedQuantity) > 0) {
            throw new IllegalArgumentException("Received quantity cannot exceed dispatched quantity");
        }
        receivedQuantity = quantity;
    }

    public boolean hasReceiptVariance() {
        return dispatchedQuantity.compareTo(receivedQuantity) != 0;
    }

    public BigDecimal varianceQuantity() {
        return dispatchedQuantity.subtract(receivedQuantity).abs();
    }

    private static void requirePositive(BigDecimal quantity) {
        requireNonNegative(quantity);
        if (quantity.signum() == 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
    }

    private static void requireNonNegative(BigDecimal quantity) {
        if (quantity == null || quantity.signum() < 0) {
            throw new IllegalArgumentException("Quantity cannot be negative");
        }
    }
}
