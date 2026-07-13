package com.izonehub.stores.project;

import com.izonehub.stores.common.BaseEntity;
import com.izonehub.stores.store.Store;
import com.izonehub.stores.user.AppUser;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Project extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Store siteStore;

    @Column(precision = 19, scale = 4)
    private BigDecimal budgetCeiling;

    @Column(nullable = false)
    private boolean active = true;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "project_employees",
        joinColumns = @JoinColumn(name = "project_id"),
        inverseJoinColumns = @JoinColumn(name = "app_user_id")
    )
    private List<AppUser> assignedEmployees = new ArrayList<>();

    protected Project() {}

    public Project(String code, String name, Store siteStore, BigDecimal budgetCeiling) {
        this.code = code;
        this.name = name;
        this.siteStore = siteStore;
        this.budgetCeiling = budgetCeiling;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public Store getSiteStore() { return siteStore; }
    public BigDecimal getBudgetCeiling() { return budgetCeiling; }
    public boolean isActive() { return active; }
    public List<AppUser> getAssignedEmployees() { return assignedEmployees; }

    public void assignEmployee(AppUser employee) {
        if (!assignedEmployees.contains(employee)) {
            assignedEmployees.add(employee);
        }
    }

    public void removeEmployee(AppUser employee) {
        assignedEmployees.remove(employee);
    }

    public void close() {
        active = false;
    }
}
