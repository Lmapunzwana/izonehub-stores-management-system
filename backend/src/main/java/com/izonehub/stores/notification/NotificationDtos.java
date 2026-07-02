package com.izonehub.stores.notification;

import java.util.UUID;

public final class NotificationDtos {
    private NotificationDtos() {}

    public record NotificationResponse(UUID id, NotificationType type, String message, boolean read) {}
}
