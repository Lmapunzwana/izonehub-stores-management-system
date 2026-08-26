package com.izonehub.stores.user;

import com.izonehub.stores.auth.PasswordResetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Sends pending welcome emails to users who were imported (e.g. via admin tooling)
 * before the API-based welcome email flow existed.
 *
 * Rather than emailing a plaintext temporary password (which is never available at
 * this point — it was already bcrypt-encoded at creation time), we trigger a
 * password reset flow so each user receives a one-time link to set their own
 * password. This avoids sending "guessed" credentials over email.
 *
 * This runner is a no-op if --create-admin is passed (handled by AdminBootstrapRunner).
 */
@Component
public class UserBootstrapRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(UserBootstrapRunner.class);

    private final UserRepository userRepository;
    private final PasswordResetService passwordResetService;

    public UserBootstrapRunner(UserRepository userRepository, PasswordResetService passwordResetService) {
        this.userRepository     = userRepository;
        this.passwordResetService = passwordResetService;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (List.of(args).contains("--create-admin")) {
            return; // handled by AdminBootstrapRunner
        }

        List<AppUser> unsentUsers = userRepository.findByWelcomeEmailSentFalse();
        if (unsentUsers.isEmpty()) {
            log.info("UserBootstrapRunner: no pending welcome emails.");
            return;
        }

        log.info("UserBootstrapRunner: dispatching password-set links for {} user(s).", unsentUsers.size());

        for (AppUser user : unsentUsers) {
            try {
                // Send a password reset link rather than a plaintext credential.
                // The plaintext temporary password is not available here (already encoded).
                passwordResetService.initiateReset(user);
                user.setWelcomeEmailSent(true);
                userRepository.save(user);
                log.info("Sent welcome / password-set email to {}", user.getEmail());
            } catch (Exception e) {
                log.error("Failed to send welcome email to {}: {}", user.getEmail(), e.getMessage(), e);
            }
        }
    }
}
