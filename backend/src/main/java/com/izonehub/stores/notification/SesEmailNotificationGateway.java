package com.izonehub.stores.notification;

import com.izonehub.stores.user.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.*;

/**
 * AWS SES implementation of EmailNotificationGateway.
 * Active when app.email.provider=ses (set via env var APP_EMAIL_PROVIDER=ses).
 * Falls back to LoggingEmailNotificationGateway when not configured.
 */
@Component
@ConditionalOnProperty(name = "app.email.provider", havingValue = "ses")
public class SesEmailNotificationGateway implements EmailNotificationGateway {

    private static final Logger log = LoggerFactory.getLogger(SesEmailNotificationGateway.class);

    private final SesClient sesClient;
    private final String    fromAddress;

    public SesEmailNotificationGateway(
            @Value("${app.email.ses-region:us-east-1}") String region,
            @Value("${app.email.from-address}")          String fromAddress) {
        this.fromAddress = fromAddress;
        this.sesClient = SesClient.builder()
                .region(Region.of(region))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Override
    public void send(AppUser user, String subject, String message) {
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            log.warn("Skipping email to user {} — no email address", user.getClass().getSimpleName());
            return;
        }
        try {
            sesClient.sendEmail(SendEmailRequest.builder()
                    .source(fromAddress)
                    .destination(Destination.builder().toAddresses(user.getEmail()).build())
                    .message(Message.builder()
                            .subject(Content.builder().data(subject).charset("UTF-8").build())
                            .body(Body.builder()
                                    .text(Content.builder().data(message).charset("UTF-8").build())
                                    .build())
                            .build())
                    .build());
            log.debug("SES email sent to {}: {}", user.getEmail(), subject);
        } catch (SesException e) {
            // Log and continue — email failure must not break the primary transaction
            log.error("SES send failed to {}: {}", user.getEmail(), e.awsErrorDetails().errorMessage(), e);
        }
    }
}
