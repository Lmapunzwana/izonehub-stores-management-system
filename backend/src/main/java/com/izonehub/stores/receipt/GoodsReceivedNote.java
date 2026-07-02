package com.izonehub.stores.receipt;

import com.izonehub.stores.common.BaseEntity;
import com.izonehub.stores.store.Store;
import com.izonehub.stores.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import java.time.Instant;

@Entity
public class GoodsReceivedNote extends BaseEntity {
    @Column(nullable = false, unique = true)
    private String referenceNumber;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private ExpectedReceipt expectedReceipt;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Store store;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser receivedBy;

    @Column(nullable = false)
    private Instant receivedAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GrnStatus status;

    protected GoodsReceivedNote() {
    }

    public GoodsReceivedNote(String referenceNumber, ExpectedReceipt expectedReceipt, AppUser receivedBy, GrnStatus status) {
        this.referenceNumber = referenceNumber;
        this.expectedReceipt = expectedReceipt;
        this.store = expectedReceipt.getStore();
        this.receivedBy = receivedBy;
        this.status = status;
    }

    public String getReferenceNumber() { return referenceNumber; }
    public GrnStatus getStatus() { return status; }
}
