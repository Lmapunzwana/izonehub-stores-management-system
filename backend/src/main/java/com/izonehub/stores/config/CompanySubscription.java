package com.izonehub.stores.config;

import com.izonehub.stores.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;

@Entity
public class CompanySubscription extends BaseEntity {

    @Column(nullable = false)
    private int allowedStoreSlots = 3;

    protected CompanySubscription() {}

    public CompanySubscription(int allowedStoreSlots) {
        this.allowedStoreSlots = allowedStoreSlots;
    }

    public int getAllowedStoreSlots() {
        return allowedStoreSlots;
    }

    public void setAllowedStoreSlots(int allowedStoreSlots) {
        this.allowedStoreSlots = allowedStoreSlots;
    }
}
