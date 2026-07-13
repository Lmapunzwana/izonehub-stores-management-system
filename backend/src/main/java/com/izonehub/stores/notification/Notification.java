package com.izonehub.stores.notification;

import com.izonehub.stores.common.BaseEntity;
import com.izonehub.stores.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;

@Entity
public class Notification extends BaseEntity {
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false, length = 1000)
    private String message;

    @Column(nullable = false)
    private boolean read = false;

    protected Notification() {
    }

    public Notification(AppUser user, NotificationType type, String message) {
        this.user = user;
        this.type = type;
        this.message = message;
    }

    public com.izonehub.stores.user.AppUser getUser() { return user; }
    public NotificationType getType() { return type; }
    public String getMessage() { return message; }
    public boolean isRead() { return read; }
    public void markRead() { read = true; }
}
