package com.izonehub.stores.issuance;

import com.izonehub.stores.common.BaseEntity;
import com.izonehub.stores.project.Project;
import com.izonehub.stores.store.Store;
import com.izonehub.stores.user.AppUser;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
public class MaterialIssueVoucher extends BaseEntity {
    @Column(nullable = false, unique = true)
    private String referenceNumber;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Store store;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Project project;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser issuedBy;

    @Column(nullable = false)
    private Instant issuedAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MivStatus status = MivStatus.ACTIVE;

    @OneToMany(mappedBy = "miv", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MivLine> lines = new ArrayList<>();

    protected MaterialIssueVoucher() {}

    public MaterialIssueVoucher(String referenceNumber, Store store, Project project, AppUser issuedBy) {
        this.referenceNumber = referenceNumber;
        this.store = store;
        this.project = project;
        this.issuedBy = issuedBy;
    }

    public Store getStore() { return store; }
    public String getReferenceNumber() { return referenceNumber; }
    public Project getProject() { return project; }
    public AppUser getIssuedBy() { return issuedBy; }
    public Instant getIssuedAt() { return issuedAt; }
    public List<MivLine> getLines() { return lines; }
    public MivStatus getStatus() { return status; }

    public void addLine(MivLine line) {
        line.attachTo(this);
        lines.add(line);
    }

    public void markPartiallyReturned() { status = MivStatus.PARTIALLY_RETURNED; }
    public void close() { status = MivStatus.CLOSED; }
}
