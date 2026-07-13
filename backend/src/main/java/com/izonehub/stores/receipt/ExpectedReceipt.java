package com.izonehub.stores.receipt;

import com.izonehub.stores.common.BaseEntity;
import com.izonehub.stores.store.Store;
import com.izonehub.stores.user.AppUser;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
public class ExpectedReceipt extends BaseEntity {
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Store store;

    @Column(nullable = false)
    private String supplierName;

    @Column(nullable = false)
    private LocalDate expectedDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExpectedReceiptStatus status = ExpectedReceiptStatus.AWAITING_GRN;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser createdBy;

    @OneToMany(mappedBy = "expectedReceipt", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<ExpectedReceiptLine> lines = new ArrayList<>();

    protected ExpectedReceipt() {
    }

    public ExpectedReceipt(Store store, String supplierName, LocalDate expectedDate, AppUser createdBy) {
        this.store = store;
        this.supplierName = supplierName;
        this.expectedDate = expectedDate;
        this.createdBy = createdBy;
    }

    public Store getStore() { return store; }
    public String getSupplierName() { return supplierName; }
    public LocalDate getExpectedDate() { return expectedDate; }
    public ExpectedReceiptStatus getStatus() { return status; }
    public List<ExpectedReceiptLine> getLines() { return lines; }
    public int getLineCount() { return lines.size(); }

    public void addLine(ExpectedReceiptLine line) {
        line.attachTo(this);
        lines.add(line);
    }

    public void markOverdue(LocalDate today) {
        if (status == ExpectedReceiptStatus.AWAITING_GRN && expectedDate.isBefore(today)) {
            status = ExpectedReceiptStatus.DELAYED;
        }
    }

    public void setStatus(ExpectedReceiptStatus newStatus) {
        // Prevent manual override back to a lower state if it's already received
        if (this.status == ExpectedReceiptStatus.COMPLETED || this.status == ExpectedReceiptStatus.PARTIALLY_RECEIVED) {
            throw new IllegalStateException("Cannot change status of a receipt that has already been partially or fully received");
        }
        this.status = newStatus;
    }

    public void markCompleted(boolean hasVariance) {
        status = hasVariance ? ExpectedReceiptStatus.PARTIALLY_RECEIVED : ExpectedReceiptStatus.COMPLETED;
    }
}
