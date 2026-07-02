package com.izonehub.stores.issuance;

import com.izonehub.stores.common.BaseEntity;
import com.izonehub.stores.item.Item;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
public class MivLine extends BaseEntity {
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private MaterialIssueVoucher miv;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Item item;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal issuedQuantity;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal returnedQuantity = BigDecimal.ZERO;

    protected MivLine() {}

    public MivLine(Item item, BigDecimal issuedQuantity) {
        if (issuedQuantity == null || issuedQuantity.signum() <= 0) throw new IllegalArgumentException("Issued quantity must be positive");
        this.item = item;
        this.issuedQuantity = issuedQuantity;
    }

    void attachTo(MaterialIssueVoucher miv) { this.miv = miv; }
    public Item getItem() { return item; }
    public BigDecimal getIssuedQuantity() { return issuedQuantity; }
    public BigDecimal getReturnedQuantity() { return returnedQuantity; }

    public void addReturn(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0) throw new IllegalArgumentException("Return quantity must be positive");
        if (returnedQuantity.add(quantity).compareTo(issuedQuantity) > 0) throw new IllegalArgumentException("Returned quantity cannot exceed issued quantity");
        returnedQuantity = returnedQuantity.add(quantity);
    }
}
