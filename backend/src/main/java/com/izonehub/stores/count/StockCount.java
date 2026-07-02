package com.izonehub.stores.count;

import com.izonehub.stores.common.BaseEntity;
import com.izonehub.stores.store.Store;
import com.izonehub.stores.user.AppUser;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
public class StockCount extends BaseEntity {
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Store store;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser initiatedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StockCountStatus status = StockCountStatus.OPEN;

    @OneToMany(mappedBy = "stockCount", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<StockCountLine> lines = new ArrayList<>();

    protected StockCount() {}

    public StockCount(Store store, AppUser initiatedBy) {
        this.store = store;
        this.initiatedBy = initiatedBy;
    }

    public List<StockCountLine> getLines() { return lines; }
    public StockCountStatus getStatus() { return status; }
    public void addLine(StockCountLine line) { line.attachTo(this); lines.add(line); }

    public void completeIfResolved() {
        boolean unresolved = lines.stream().anyMatch(line -> line.getStatus() == StockCountLineStatus.PENDING_COUNT
                || line.getStatus() == StockCountLineStatus.VARIANCE_REQUIRES_ACTION
                || line.getStatus() == StockCountLineStatus.RECOUNT_REQUIRED);
        if (!unresolved) status = StockCountStatus.COMPLETED;
    }
}
