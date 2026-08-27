package com.izonehub.stores.notification;

import com.izonehub.stores.user.AppUser;
import java.util.List;

public interface EmailNotificationGateway {
    void send(AppUser user, String subject, String message);

    default void sendWithAttachments(AppUser user, String subject, String message, List<EmailAttachment> attachments) {
        send(user, subject, message);
    }
}

