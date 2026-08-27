package com.izonehub.stores.notification;

import com.izonehub.stores.user.AppUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Resend.com HTTP API implementation of EmailNotificationGateway.
 *
 * Activated when app.email.provider=resend.
 * Requires:
 *   - RESEND_API_KEY environment variable (or app.email.resend-api-key in application.yml)
 *   - app.email.from-address (e.g. "Stores System <noreply@yourdomain.com>")
 *
 * No Maven dependency needed — uses Java 11+ built-in HttpClient.
 * Falls back gracefully (logs error, never breaks the primary transaction).
 *
 * Resend docs: https://resend.com/docs/api-reference/emails/send-email
 */
@Component
@ConditionalOnProperty(name = "app.email.provider", havingValue = "resend")
public class ResendEmailNotificationGateway implements EmailNotificationGateway {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailNotificationGateway.class);
    private static final String RESEND_API_URL = "https://api.resend.com/emails";

    private final String apiKey;
    private final String fromAddress;
    private final HttpClient httpClient;

    public ResendEmailNotificationGateway(
            @Value("${app.email.resend-api-key:}") String apiKey,
            @Value("${app.email.from-address:Notifications <notifications@mail.nsv.co.zw>}") String fromAddress) {
        this.apiKey      = apiKey;
        this.fromAddress = fromAddress;
        this.httpClient  = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public void send(AppUser user, String subject, String message) {
        sendWithAttachments(user, subject, message, null);
    }

    @Override
    public void sendWithAttachments(AppUser user, String subject, String message, java.util.List<EmailAttachment> attachments) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            log.warn("Resend: skipping email — recipient has no email address");
            return;
        }
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Resend: RESEND_API_KEY not set — email not sent to {}", user.getEmail());
            return;
        }

        // Build an HTML body for a nicer look in email clients
        String htmlBody = EmailTemplateHelper.buildHtmlEmail(user.getFullName(), subject, message);

        // JSON payload — Resend accepts "html", "text", and "attachments" fields
        StringBuilder jsonPayload = new StringBuilder();
        jsonPayload.append("{")
                .append("\"from\": ").append(jsonString(fromAddress)).append(",")
                .append("\"to\": [").append(jsonString(user.getEmail())).append("],")
                .append("\"subject\": ").append(jsonString(subject)).append(",")
                .append("\"html\": ").append(jsonString(htmlBody)).append(",")
                .append("\"text\": ").append(jsonString(message));

        if (attachments != null && !attachments.isEmpty()) {
            jsonPayload.append(",\"attachments\": [");
            for (int i = 0; i < attachments.size(); i++) {
                EmailAttachment att = attachments.get(i);
                if (i > 0) jsonPayload.append(",");
                String b64 = java.util.Base64.getEncoder().encodeToString(att.content());
                jsonPayload.append("{")
                        .append("\"filename\": ").append(jsonString(att.filename())).append(",")
                        .append("\"content\": ").append(jsonString(b64))
                        .append("}");
            }
            jsonPayload.append("]");
        }
        jsonPayload.append("}");

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(RESEND_API_URL))
                    .timeout(Duration.ofSeconds(25))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload.toString()))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Resend email (attachments: {}) sent to {} — subject: {}",
                        attachments != null ? attachments.size() : 0, user.getEmail(), subject);
            } else {
                log.error("Resend API error {} for {}: {}", response.statusCode(), user.getEmail(), response.body());
            }
        } catch (Exception e) {
            // Email failure must never crash the primary business transaction
            log.error("Resend send failed to {}: {}", user.getEmail(), e.getMessage(), e);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Minimal JSON string escaping for the inline payload. */
    private String jsonString(String value) {
        if (value == null) return "null";
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                + "\"";
    }
}
