package com.izonehub.stores.notification;

import com.izonehub.stores.user.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Fallback no-op gateway — activated only when no other EmailNotificationGateway
 * bean is registered (i.e. when app.email.provider is not set to "ses" or "resend").
 * Logs the notification at INFO level so it is still visible in the application log.
 */
@Component
@ConditionalOnMissingBean(value = EmailNotificationGateway.class, ignored = LoggingEmailNotificationGateway.class)
public class LoggingEmailNotificationGateway implements EmailNotificationGateway {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailNotificationGateway.class);

    @Override
    public void send(AppUser user, String subject, String message) {
        log.info("[EMAIL NOT SENT — no real provider configured] To: {} | Subject: {} | Body: {}",
                user != null ? user.getEmail() : "null",
                subject,
                message);
    }
}
