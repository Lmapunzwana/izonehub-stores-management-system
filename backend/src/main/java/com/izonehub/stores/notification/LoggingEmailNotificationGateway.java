package com.izonehub.stores.notification;

import com.izonehub.stores.user.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingEmailNotificationGateway implements EmailNotificationGateway {
    private static final Logger log = LoggerFactory.getLogger(LoggingEmailNotificationGateway.class);

    @Override
    public void send(AppUser user, String subject, String message) {
        log.info("Email notification queued for {} with subject {}", user.getEmail(), subject);
    }
}
