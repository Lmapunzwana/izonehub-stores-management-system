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
        boolean creatorIsAdmin = createdBy != null && createdBy.getRoles().contains(Role.SYSTEM_ADMINISTRATOR);
        if (roles.contains(Role.SYSTEM_ADMINISTRATOR) && !creatorIsAdmin) {
            throw new IllegalArgumentException(
                    "Only System Administrators can create other System Administrator accounts");
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

        // Send welcome email with login credentials, attached User Guide PDF & video training instructions
        try {
            String subject = "Welcome to NSV Stores — Credentials & Getting Started Guide";
            String body = "Welcome to the NSV Stores Management System.\n\n"
                    + "Your user account has been successfully created with the following credentials:\n\n"
                    + "  - Email / Username: " + savedUser.getEmail() + "\n"
                    + "  - Temporary Password: " + temporaryPassword + "\n\n"
                    + "Direct Login Link: " + appBaseUrl + "/login\n\n"
                    + "Security Notice: For security compliance, please log in and update your password immediately using the 'Change Password' option.\n\n"
                    + "========================================================\n"
                    + "ONBOARDING RESOURCES & ATTACHMENTS\n"
                    + "========================================================\n\n"
                    + "1. PDF USER GUIDE (SEE ATTACHMENT BELOW)\n"
                    + "Please see the attached document ('NSV_Stores_User_Guide.pdf') at the bottom of this email. It contains step-by-step documentation covering store navigation, material requests, dispatches, receiving items, stock counts, and system management.\n\n"
                    + "2. VIDEO TRAINING TUTORIALS\n"
                    + "The training video package ('NSV_Stores_Training_Videos.zip') is too large to attach here. Once you've logged in, click 'Training Videos' in the left-hand sidebar to download it directly:\n"
                    + "  - Site Store Managers: Watch videos on requesting materials, receiving shipments, and logging daily material consumption.\n"
                    + "  - Central Store Managers: Watch videos on item catalog setup, expected receipts, MIV approval, and dispatching.\n\n"
                    + "If you have any questions or require support, please reach out to your system administrator.\n\n"
                    + "Best regards,\n"
                    + "NSV Stores Team";

            java.util.List<com.izonehub.stores.notification.EmailAttachment> attachments = new java.util.ArrayList<>();
            try {
                byte[] pdfBytes = null;
                try (java.io.InputStream is = new org.springframework.core.io.ClassPathResource("NSV_Stores_User_Guide.pdf").getInputStream()) {
                    pdfBytes = is.readAllBytes();
                } catch (Exception e1) {
                    java.io.File fileCur = new java.io.File("NSV_Stores_User_Guide.pdf");
                    if (fileCur.exists() && fileCur.isFile()) {
                        pdfBytes = java.nio.file.Files.readAllBytes(fileCur.toPath());
                    }
                }
                if (pdfBytes != null && pdfBytes.length > 0) {
                    attachments.add(new com.izonehub.stores.notification.EmailAttachment(
                            "NSV_Stores_User_Guide.pdf", pdfBytes, "application/pdf"));
                }
            } catch (Exception ex) {
                log.warn("Could not load NSV_Stores_User_Guide.pdf attachment: {}", ex.getMessage());
            }

            emailGateway.sendWithAttachments(savedUser, subject, body, attachments);
            savedUser.setWelcomeEmailSent(true);
            repo.save(savedUser);
        } catch (Exception e) {
            log.error("Failed to send welcome email to {}: {}", savedUser.getEmail(), e.getMessage(), e);
        }

        return savedUser;
    }
}
