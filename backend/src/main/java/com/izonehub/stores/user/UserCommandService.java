package com.izonehub.stores.user;

import com.izonehub.stores.auth.PasswordPolicy;
import com.izonehub.stores.notification.EmailNotificationGateway;
import com.izonehub.stores.store.Store;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * Creates new application users.
 *
 * Sends a welcome email to the newly created user containing their username,
 * initial password, and login link.
 */
@Service
public class UserCommandService {

    private static final Logger log = LoggerFactory.getLogger(UserCommandService.class);

    private final UserRepository           repo;
    private final PasswordEncoder          encoder;
    private final PasswordPolicy           policy;
    private final EmailNotificationGateway emailGateway;
    private final String                   appBaseUrl;

    public UserCommandService(UserRepository repo, PasswordEncoder encoder,
                              PasswordPolicy policy, EmailNotificationGateway emailGateway,
                              @Value("${app.base-url:https://stores.nsv.co.zw}") String appBaseUrl) {
        this.repo         = repo;
        this.encoder      = encoder;
        this.policy       = policy;
        this.emailGateway = emailGateway;
        this.appBaseUrl   = appBaseUrl;
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

        // Send welcome email with login credentials and direct login link
        try {
            String subject = "Welcome to NSV Stores — Account Credentials";
            String body = "Welcome to NSV Stores Management System.\n\n"
                    + "Your account has been created with the following login credentials:\n\n"
                    + "  Email / Username: " + savedUser.getEmail() + "\n"
                    + "  Password: " + temporaryPassword + "\n\n"
                    + "Please log in at: " + appBaseUrl + "/login\n\n"
                    + "We recommend updating your password after logging in.";

            emailGateway.send(savedUser, subject, body);
            savedUser.setWelcomeEmailSent(true);
            repo.save(savedUser);
        } catch (Exception e) {
            log.error("Failed to send welcome email to {}: {}", savedUser.getEmail(), e.getMessage(), e);
        }

        return savedUser;
    }
}
