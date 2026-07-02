package com.izonehub.stores.notification;

import com.izonehub.stores.user.AppUser;

public interface EmailNotificationGateway {
    void send(AppUser user, String subject, String message);
}
