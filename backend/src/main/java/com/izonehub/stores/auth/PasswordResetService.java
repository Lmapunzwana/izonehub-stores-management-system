package com.izonehub.stores.auth;

import com.izonehub.stores.notification.EmailNotificationGateway;
import com.izonehub.stores.user.AppUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Service
public class PasswordResetService {

    private final PasswordResetTokenRepository tokens;
    private final EmailNotificationGateway     email;
    private final String                       appBaseUrl;
    private final SecureRandom                 rng = new SecureRandom();

    public PasswordResetService(
            PasswordResetTokenRepository tokens,
            EmailNotificationGateway email,
            @Value("${app.base-url:http://localhost:3000}") String appBaseUrl) {
        this.tokens     = tokens;
        this.email      = email;
        this.appBaseUrl = appBaseUrl;
    }

    @Transactional
    public void initiateReset(AppUser user) {
        // Generate a URL-safe 32-byte token
        byte[] bytes = new byte[32];
        rng.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        tokens.save(new PasswordResetToken(token, user, Instant.now().plusSeconds(3600)));

        String link    = appBaseUrl + "/reset-password?token=" + token;
        String subject = "NSV Stores — Password Reset";
        String body    = "Click the link below to reset your password. It expires in 1 hour.\n\n" + link
                       + "\n\nIf you did not request this, ignore this email.";

        email.send(user, subject, body);
    }

    @Transactional
    public void consumeToken(String rawToken, String encodedNewPassword) {
        PasswordResetToken prt = tokens.findByToken(rawToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid or expired token"));

        if (prt.isUsed() || prt.isExpired())
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Invalid or expired token");

        AppUser user = prt.getUser();
        user.changePassword(encodedNewPassword);
        prt.markUsed();
        tokens.save(prt);
    }
}
