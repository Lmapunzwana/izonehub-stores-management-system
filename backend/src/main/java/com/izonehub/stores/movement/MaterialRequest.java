package com.izonehub.stores.movement;

import com.izonehub.stores.common.BaseEntity;
import com.izonehub.stores.project.Project;
import com.izonehub.stores.store.Store;
import com.izonehub.stores.user.AppUser;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
public class MaterialRequest extends BaseEntity {
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Store requestingStore;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Store sourceStore;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MaterialRequestStatus status = MaterialRequestStatus.DRAFT;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser raisedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser approvedBy;

    // Set only for site-to-site transfers (neither side is Central), where Central Store
    // Manager sign-off is a second, separate gate after the source store manager's own
    // approval. Null for every other request — including central-sourced requests and
    // returns-to-central, which never pass through PENDING_CENTRAL_APPROVAL.
    @ManyToOne(fetch = FetchType.LAZY)
    private AppUser centralApprovedBy;

    @Column(length = 1000)
    private String rejectionReason;

    @Column(length = 1000)
    private String transferReason;

    @OneToMany(mappedBy = "materialRequest", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<MaterialRequestLine> lines = new ArrayList<>();

    protected MaterialRequest() {
    }

    public MaterialRequest(Store requestingStore, Store sourceStore, Project project, AppUser raisedBy, String transferReason) {
        this.requestingStore = requestingStore;
        this.sourceStore = sourceStore;
        this.project = project;
        this.raisedBy = raisedBy;
        this.transferReason = transferReason;
    }

    public Store getRequestingStore() { return requestingStore; }
    public Store getSourceStore() { return sourceStore; }
    public Project getProject() { return project; }
    public MaterialRequestStatus getStatus() { return status; }
    public AppUser getRaisedBy() { return raisedBy; }
    public AppUser getApprovedBy() { return approvedBy; }
    public AppUser getCentralApprovedBy() { return centralApprovedBy; }
    public String getRejectionReason() { return rejectionReason; }
    public String getTransferReason() { return transferReason; }
    public List<MaterialRequestLine> getLines() { return lines; }

    public void addLine(MaterialRequestLine line) {
        line.attachTo(this);
        lines.add(line);
    }

    public void submit() {
        requireStatus(MaterialRequestStatus.DRAFT);
        if (lines.isEmpty()) {
            throw new IllegalStateException("Material request must have at least one line before submission");
        }
        status = MaterialRequestStatus.PENDING_APPROVAL;
    }

    /**
     * @param requiresCentralApproval true for a site-to-site transfer (neither store is
     *                                Central) — moves to PENDING_CENTRAL_APPROVAL instead
     *                                of straight to APPROVED, so dispatch stays blocked
     *                                until centralApprove() is also called.
     */
    public void approve(AppUser approver, boolean requiresCentralApproval) {
        requireStatus(MaterialRequestStatus.PENDING_APPROVAL);
        approvedBy = approver;
        status = requiresCentralApproval
                ? MaterialRequestStatus.PENDING_CENTRAL_APPROVAL
                : MaterialRequestStatus.APPROVED;
    }

    public void centralApprove(AppUser approver) {
        requireStatus(MaterialRequestStatus.PENDING_CENTRAL_APPROVAL);
        centralApprovedBy = approver;
        status = MaterialRequestStatus.APPROVED;
    }

    public void reject(AppUser approver, String reason) {
        if (status != MaterialRequestStatus.PENDING_APPROVAL && status != MaterialRequestStatus.PENDING_CENTRAL_APPROVAL) {
            throw new IllegalStateException("Expected status PENDING_APPROVAL or PENDING_CENTRAL_APPROVAL but was " + status);
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Rejection reason is required");
        }
        approvedBy = approver;
        rejectionReason = reason;
        status = MaterialRequestStatus.REJECTED;
    }

    public void markInTransit() {
        requireStatus(MaterialRequestStatus.APPROVED);
        status = MaterialRequestStatus.IN_TRANSIT;
    }

    public void markReceiptResult(boolean hasDiscrepancy) {
        requireStatus(MaterialRequestStatus.IN_TRANSIT);
        status = hasDiscrepancy ? MaterialRequestStatus.DISCREPANCY : MaterialRequestStatus.COMPLETED;
    }

    private void requireStatus(MaterialRequestStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Expected status " + expected + " but was " + status);
        }
    }
}
