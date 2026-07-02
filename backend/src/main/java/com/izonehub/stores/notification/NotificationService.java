package com.izonehub.stores.notification;

import com.izonehub.stores.user.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    private final NotificationRepository repository;

    public NotificationService(NotificationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Notification notify(AppUser user, NotificationType type, String message) {
        return repository.save(new Notification(user, type, message));
    }
}
