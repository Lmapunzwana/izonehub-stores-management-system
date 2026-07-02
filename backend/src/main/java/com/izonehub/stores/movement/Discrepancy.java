package com.izonehub.stores.movement;

import com.izonehub.stores.common.BaseEntity;
import com.izonehub.stores.item.Item;
import com.izonehub.stores.user.AppUser;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
public class Discrepancy extends BaseEntity {
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Receipt receipt;

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
        this.receipt = receipt;
        this.item = item;
        this.dispatchedQuantity = dispatchedQuantity;
        this.receivedQuantity = receivedQuantity;
        this.frozenQuantity = dispatchedQuantity.subtract(receivedQuantity).abs();
    }

    public BigDecimal getFrozenQuantity() { return frozenQuantity; }
    public DiscrepancyStatus getStatus() { return status; }

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
