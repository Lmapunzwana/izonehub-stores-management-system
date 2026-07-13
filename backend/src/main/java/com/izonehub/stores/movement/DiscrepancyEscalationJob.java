package com.izonehub.stores.movement;

import com.izonehub.stores.notification.NotificationService;
import com.izonehub.stores.notification.NotificationType;
import com.izonehub.stores.user.Role;
import com.izonehub.stores.user.UserRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class DiscrepancyEscalationJob {
    private final DiscrepancyRepository discrepancies;
    private final UserRepository users;
    private final NotificationService notifications;

    public DiscrepancyEscalationJob(DiscrepancyRepository discrepancies, UserRepository users, NotificationService notifications) {
        this.discrepancies = discrepancies;
        this.users = users;
        this.notifications = notifications;
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void escalateOpenDiscrepancies() {
        Instant cutoff = Instant.now().minus(48, ChronoUnit.HOURS);
        discrepancies.findByStatusAndCreatedAtBefore(DiscrepancyStatus.OPEN, cutoff).forEach(discrepancy ->
                users.findAll().stream()
                        .filter(user -> user.getRoles().contains(Role.CENTRAL_STORE_MANAGER) && user.isActive())
                        .forEach(user -> notifications.notify(user, NotificationType.DISCREPANCY_OPENED,
                                "Discrepancy unresolved after 48 hours")));
    }
}
