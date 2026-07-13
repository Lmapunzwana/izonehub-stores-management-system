package com.izonehub.stores.issuance;

import com.izonehub.stores.common.BaseEntity;
import com.izonehub.stores.store.Store;
import com.izonehub.stores.user.AppUser;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
public class StockReturn extends BaseEntity {
    @ManyToOne(optional = true, fetch = FetchType.LAZY)
    private MaterialIssueVoucher miv;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Store store;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser returnedBy;

    @Column(nullable = false)
    private Instant returnedAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReturnStatus status = ReturnStatus.PENDING_CONFIRMATION;

    @OneToMany(mappedBy = "stockReturn", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StockReturnLine> lines = new ArrayList<>();

    protected StockReturn() {}

    public StockReturn(MaterialIssueVoucher miv, Store store, AppUser returnedBy) {
        this.miv = miv;
        this.store = store;
        this.returnedBy = returnedBy;
    }

    public StockReturn(Store store, AppUser returnedBy) {
        this.miv = null;
        this.store = store;
        this.returnedBy = returnedBy;
    }

    public List<StockReturnLine> getLines() { return lines; }
    public void addLine(StockReturnLine line) { line.attachTo(this); lines.add(line); }

    public MaterialIssueVoucher getMiv() { return miv; }
    public Store getStore() { return store; }
    public AppUser getReturnedBy() { return returnedBy; }
    public Instant getReturnedAt() { return returnedAt; }
    public ReturnStatus getStatus() { return status; }
    public void setStatus(ReturnStatus status) { this.status = status; }
}
