package com.izonehub.stores.user;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.izonehub.stores.common.BaseEntity;
import com.izonehub.stores.store.Store;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name="app_users")

public class AppUser extends BaseEntity {

    @Column(nullable=false)
    private String fullName;

    @Column(nullable=false,unique=true)
    private String email;

    @JsonIgnore
    @Column(nullable=false)
    private String passwordHash;

    @ElementCollection(fetch = FetchType.EAGER)
    @Enumerated(EnumType.STRING)
    @CollectionTable(name = "app_user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role", nullable = false)
    private Set<Role> roles = new HashSet<>();

    @ManyToOne(fetch=FetchType.LAZY)
    private Store assignedStore;

    @Column(nullable=false)
    private boolean active=true;

    @Column(nullable=false)
    private boolean forcePasswordChange=true;

    @Column(nullable=false)
    private int failedLoginAttempts=0;

    @Column(nullable=false)
    private boolean locked=false;

    @JsonIgnore
    @ManyToOne(fetch=FetchType.LAZY)
    private AppUser createdBy;

    private Instant lastLogin;

    protected AppUser() {}

    public AppUser(String fullName, String email, String passwordHash, Set<Role> roles, Store assignedStore, AppUser createdBy) {
        this.fullName = fullName;
        this.email = email.toLowerCase();
        this.passwordHash = passwordHash;
        this.roles = roles;
        this.assignedStore = assignedStore;
        this.createdBy = createdBy;
    }

    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Set<Role> getRoles() { return roles; }
    public Store getAssignedStore() { return assignedStore; }
    public boolean isActive() { return active; }
    public boolean isForcePasswordChange() { return forcePasswordChange; }
    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public boolean isLocked() { return locked; }
    public Instant getLastLogin() { return lastLogin; }

    public void deactivate() { active = false; }
    public void unlock() { locked = false; failedLoginAttempts = 0; }

    public void recordFailedLogin(int max) {
        failedLoginAttempts++;
        if(failedLoginAttempts >= max) locked = true;
    }

    public void setAssignedStore(Store store) {
        this.assignedStore = store;
    }

    public void recordSuccessfulLogin() {
        failedLoginAttempts = 0;
        locked = false;
        lastLogin = Instant.now();
    }

    public void changePassword(String hash) {
        passwordHash = hash;
        forcePasswordChange = false;
    }
}
