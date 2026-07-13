package com.izonehub.stores.movement;

import com.izonehub.stores.common.BaseEntity;
import com.izonehub.stores.user.AppUser;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
public class Dispatch extends BaseEntity {
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private MaterialRequest materialRequest;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser dispatchedBy;

    @Column(nullable = false)
    private String collectorName;

    @Column(nullable = false)
    private String collectorEmployeeId;

    @Column(nullable = false)
    private Instant dispatchedAt = Instant.now();

    protected Dispatch() {
    }

    public Dispatch(MaterialRequest materialRequest, AppUser dispatchedBy, String collectorName, String collectorEmployeeId) {
        this.materialRequest = materialRequest;
        this.dispatchedBy = dispatchedBy;
        this.collectorName = collectorName;
        this.collectorEmployeeId = collectorEmployeeId;
    }

    public MaterialRequest getMaterialRequest() { return materialRequest; }
    public AppUser getDispatchedBy() { return dispatchedBy; }
    public String getCollectorName() { return collectorName; }
    public String getCollectorEmployeeId() { return collectorEmployeeId; }
    public Instant getDispatchedAt() { return dispatchedAt; }
}
