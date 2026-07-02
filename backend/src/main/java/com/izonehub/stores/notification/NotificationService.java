package com.izonehub.stores.notification;

import com.izonehub.stores.user.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class NotificationService {
    private final NotificationRepository repository;
    private final EmailNotificationGateway emailGateway;

    public NotificationService(NotificationRepository repository, EmailNotificationGateway emailGateway) {
        this.repository = repository;
        this.emailGateway = emailGateway;
    }

    @Transactional
    public Notification notify(AppUser user, NotificationType type, String message) {
        Notification notification = repository.save(new Notification(user, type, message));
        emailGateway.send(user, type.name(), message);
        return notification;
    }

    @Transactional(readOnly = true)
    public List<Notification> listUnread() {
        return repository.findByReadFalse();
    }

    @Transactional
    public Notification markRead(Notification notification) {
        notification.markRead();
        return repository.save(notification);
    }
}
