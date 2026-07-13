package com.izonehub.stores.issuance;

import com.izonehub.stores.common.BaseEntity;
import com.izonehub.stores.item.Item;
import com.izonehub.stores.store.Store;
import com.izonehub.stores.user.AppUser;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
public class StockAdjustment extends BaseEntity {
    @Column(nullable = false, unique = true)
    private String referenceNumber;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Store store;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Item item;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser adjustedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AdjustmentReasonCode reasonCode;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantityBefore;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal quantityAfter;

    @Column(length = 2000)
    private String notes;

    @Column(nullable = false)
    private boolean requiresCountersignature;

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser countersignedBy;

    protected StockAdjustment() {}

    public StockAdjustment(String referenceNumber, Store store, Item item, AppUser adjustedBy, AdjustmentReasonCode reasonCode,
                           BigDecimal quantityBefore, BigDecimal quantityAfter, String notes, BigDecimal threshold) {
        this.referenceNumber = referenceNumber;
        this.store = store;
        this.item = item;
        this.adjustedBy = adjustedBy;
        this.reasonCode = reasonCode;
        this.quantityBefore = quantityBefore;
        this.quantityAfter = quantityAfter;
        this.notes = notes;
        this.requiresCountersignature = quantityAfter.subtract(quantityBefore).abs().compareTo(threshold) > 0;
    }

    public boolean isRequiresCountersignature() { return requiresCountersignature; }
    public boolean isCountersigned() { return countersignedBy != null; }
    public String getReferenceNumber() { return referenceNumber; }
    public Store getStore() { return store; }
    public Item getItem() { return item; }
    public AppUser getAdjustedBy() { return adjustedBy; }
    public AdjustmentReasonCode getReasonCode() { return reasonCode; }
    public BigDecimal getQuantityBefore() { return quantityBefore; }
    public BigDecimal getQuantityAfter() { return quantityAfter; }
    public String getNotes() { return notes; }
    public AppUser getCountersignedBy() { return countersignedBy; }
    public void countersign(AppUser countersignedBy) { this.countersignedBy = countersignedBy; }
}
