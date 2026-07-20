package com.izonehub.stores.user;

import com.izonehub.stores.auth.PasswordPolicy;
import com.izonehub.stores.store.Store;
import com.izonehub.stores.notification.EmailNotificationGateway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;

@Service
public class UserCommandService {
    private final UserRepository repo;
    private final PasswordEncoder encoder;
    private final PasswordPolicy policy;
    private final EmailNotificationGateway emailGateway;

    @Value("${app.url:https://stores-app-b96j.onrender.com/}")
    private String appUrl;

    public UserCommandService(UserRepository repo, PasswordEncoder encoder, PasswordPolicy policy, EmailNotificationGateway emailGateway) {
        this.repo = repo;
        this.encoder = encoder;
        this.policy = policy;
        this.emailGateway = emailGateway;
    }

    @Transactional
    public AppUser createUser(String fullName, String email, String temporaryPassword, Set<Role> roles, Store assignedStore, AppUser createdBy) {
        if (roles.contains(Role.SYSTEM_ADMINISTRATOR)) {
            throw new IllegalArgumentException("System Administrator accounts cannot be created through application user management");
        }
        if (!policy.isValid(temporaryPassword)) {
            throw new IllegalArgumentException("Password does not meet policy");
        }
        if (repo.existsByEmail(email.toLowerCase())) {
            throw new IllegalArgumentException("Email already exists");
        }

        AppUser savedUser = repo.save(new AppUser(fullName, email.toLowerCase(), encoder.encode(temporaryPassword), roles, assignedStore, createdBy));

        // Send welcome email
        String subject = "Account Created - Sahara Ventures Stores Management System";
        String message = String.format(
            "Hie %s, your account has been created for the new sahara ventures stores management system, and your login details are as follows:\n\n" +
            "Email: %s\n" +
            "Password: %s\n\n" +
            "Currently you can access it at %s",
            fullName, email, temporaryPassword, appUrl
        );
        emailGateway.send(savedUser, subject, message);

        return savedUser;
    }
}
