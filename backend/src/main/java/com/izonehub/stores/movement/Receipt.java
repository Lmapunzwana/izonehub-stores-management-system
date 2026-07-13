package com.izonehub.stores.movement;

import com.izonehub.stores.common.BaseEntity;
import com.izonehub.stores.user.AppUser;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class Receipt extends BaseEntity {
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private MaterialRequest materialRequest;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser receivedBy;

    @Column(nullable = false)
    private Instant receivedAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReceiptStatus status;

    protected Receipt() {
    }

    public Receipt(MaterialRequest materialRequest, AppUser receivedBy, ReceiptStatus status) {
        this.materialRequest = materialRequest;
        this.receivedBy = receivedBy;
        this.status = status;
    }

    public ReceiptStatus getStatus() { return status; }
    public MaterialRequest getMaterialRequest() { return materialRequest; }
}
