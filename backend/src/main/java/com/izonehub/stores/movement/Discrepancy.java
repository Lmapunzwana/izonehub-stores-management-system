package com.izonehub.stores.movement;

import com.izonehub.stores.common.BaseEntity;
import com.izonehub.stores.item.Item;
import com.izonehub.stores.user.AppUser;
import com.izonehub.stores.receipt.GoodsReceivedNote;
import com.izonehub.stores.issuance.StockReturn;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
public class Discrepancy extends BaseEntity {
    @ManyToOne(optional = true, fetch = FetchType.LAZY)
    private Receipt receipt;

    @ManyToOne(optional = true, fetch = FetchType.LAZY)
    private GoodsReceivedNote grn;

    @ManyToOne(optional = true, fetch = FetchType.LAZY)
    private StockReturn stockReturn;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Item item;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal dispatchedQuantity;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal receivedQuantity;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal frozenQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DiscrepancyStatus status = DiscrepancyStatus.OPEN;

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser resolvedBy;

    @Column(length = 2000)
    private String resolutionNotes;

    private Instant resolvedAt;

    protected Discrepancy() {
    }

    public Discrepancy(Receipt receipt, Item item, BigDecimal dispatchedQuantity, BigDecimal receivedQuantity) {
        if (receipt == null) throw new IllegalArgumentException("Receipt cannot be null");
        this.receipt = receipt;
        this.item = item;
        this.dispatchedQuantity = dispatchedQuantity;
        this.receivedQuantity = receivedQuantity;
        this.frozenQuantity = dispatchedQuantity.subtract(receivedQuantity).abs();
    }

    public Discrepancy(GoodsReceivedNote grn, Item item, BigDecimal expectedQuantity, BigDecimal receivedQuantity) {
        if (grn == null) throw new IllegalArgumentException("GRN cannot be null");
        this.grn = grn;
        this.item = item;
        this.dispatchedQuantity = expectedQuantity;
        this.receivedQuantity = receivedQuantity;
        this.frozenQuantity = expectedQuantity.subtract(receivedQuantity).abs();
    }

    public Discrepancy(StockReturn stockReturn, Item item, BigDecimal expectedQuantity, BigDecimal receivedQuantity) {
        if (stockReturn == null) throw new IllegalArgumentException("StockReturn cannot be null");
        this.stockReturn = stockReturn;
        this.item = item;
        this.dispatchedQuantity = expectedQuantity;
        this.receivedQuantity = receivedQuantity;
        this.frozenQuantity = expectedQuantity.subtract(receivedQuantity).abs();
    }

    public Receipt getReceipt() { return receipt; }
    public GoodsReceivedNote getGrn() { return grn; }
    public StockReturn getStockReturn() { return stockReturn; }
    public Item getItem() { return item; }
    public BigDecimal getDispatchedQuantity() { return dispatchedQuantity; }
    public BigDecimal getReceivedQuantity() { return receivedQuantity; }
    public BigDecimal getFrozenQuantity() { return frozenQuantity; }
    public DiscrepancyStatus getStatus() { return status; }
    public AppUser getResolvedBy() { return resolvedBy; }
    public String getResolutionNotes() { return resolutionNotes; }
    public Instant getResolvedAt() { return resolvedAt; }

    public void resolve(AppUser resolvedBy, String resolutionNotes) {
        if (resolutionNotes == null || resolutionNotes.isBlank()) {
            throw new IllegalArgumentException("Resolution notes are required");
        }
        this.resolvedBy = resolvedBy;
        this.resolutionNotes = resolutionNotes;
        this.status = DiscrepancyStatus.RESOLVED;
        this.resolvedAt = Instant.now();
    }
}
