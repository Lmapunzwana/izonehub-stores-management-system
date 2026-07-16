package com.izonehub.stores.notification;

import com.izonehub.stores.user.AppUser;
import com.izonehub.stores.user.Role;
import com.izonehub.stores.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class NotificationService {
    private final NotificationRepository repository;
    private final EmailNotificationGateway emailGateway;
    private final UserRepository userRepository;

    public NotificationService(NotificationRepository repository, EmailNotificationGateway emailGateway, UserRepository userRepository) {
        this.repository = repository;
        this.emailGateway = emailGateway;
        this.userRepository = userRepository;
    }

    /**
     * Create an in-app notification and send an email.
     * Subject defaults to the NotificationType name for backward compatibility.
     */
    @Transactional
    public Notification notify(AppUser user, NotificationType type, String message) {
        return notifyWithSubject(user, type, formatSubject(type), message);
    }

    /**
     * Create an in-app notification and send an email with an explicit subject line.
     */
    @Transactional
    public Notification notifyWithSubject(AppUser user, NotificationType type, String subject, String message) {
        Notification notification = repository.save(new Notification(user, type, message));
        emailGateway.send(user, subject, message);
        

        
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

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Maps a NotificationType to a human-readable email subject line. */
    private String formatSubject(NotificationType type) {
        return switch (type) {
            case MATERIAL_REQUEST         -> "Stores System — Material Request Update";
            case LOW_STOCK                -> "Stores System — Low Stock Alert";
            case DISCREPANCY_OPENED       -> "Stores System — Discrepancy Detected";
            case GRN_VARIANCE             -> "Stores System — GRN Variance Recorded";
            case EXPECTED_RECEIPT_OVERDUE -> "Stores System — Expected Receipt Overdue";
            case STOCK_RETURN             -> "Stores System — Stock Return Notification";
            case STOCK_COUNT              -> "Stores System — Stock Count Action Required";
        };
    }
}

