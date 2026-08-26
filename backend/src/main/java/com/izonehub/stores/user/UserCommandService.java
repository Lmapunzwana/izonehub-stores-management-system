package com.izonehub.stores.user;

import com.izonehub.stores.auth.PasswordPolicy;
import com.izonehub.stores.auth.PasswordResetService;
import com.izonehub.stores.store.Store;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Creates new application users.
 *
 * On creation we trigger a password-reset flow (one-time link) rather than
 * emailing a plaintext temporary password. This means:
 *   1. The plaintext credential is never stored anywhere.
 *   2. The credential is never transmitted in cleartext via email.
 *   3. The user sets their own password via the secure reset flow.
 */
@Service
public class UserCommandService {

    private static final Logger log = LoggerFactory.getLogger(UserCommandService.class);

    private final UserRepository      repo;
    private final PasswordEncoder     encoder;
    private final PasswordPolicy      policy;
    private final PasswordResetService resetService;

    public UserCommandService(UserRepository repo, PasswordEncoder encoder,
                              PasswordPolicy policy, PasswordResetService resetService) {
        this.repo         = repo;
        this.encoder      = encoder;
        this.policy       = policy;
        this.resetService = resetService;
    }

    @Transactional
    public AppUser createUser(String fullName, String email, String temporaryPassword,
                              Set<Role> roles, Store assignedStore, AppUser createdBy) {
        if (roles.contains(Role.SYSTEM_ADMINISTRATOR)) {
            throw new IllegalArgumentException(
                    "System Administrator accounts cannot be created through application user management");
        }
        if (!policy.isValid(temporaryPassword)) {
            throw new IllegalArgumentException("Password does not meet policy requirements");
        }
        if (repo.existsByEmail(email.toLowerCase())) {
            throw new IllegalArgumentException("Email already exists");
        }

        AppUser savedUser = repo.save(new AppUser(
                fullName, email.toLowerCase(), encoder.encode(temporaryPassword),
                roles, assignedStore, createdBy));

        // Send a one-time password-set link via the password reset flow.
        // We never email the plaintext password — it's already encoded above.
        try {
            resetService.initiateReset(savedUser);
            savedUser.setWelcomeEmailSent(true);
            repo.save(savedUser);
        } catch (Exception e) {
            // Email failure must NOT roll back the user creation transaction.
            // The admin can resend manually via the password reset endpoint.
            log.error("Failed to send welcome email to {}: {}", savedUser.getEmail(), e.getMessage(), e);
        }

        return savedUser;
    }
}
