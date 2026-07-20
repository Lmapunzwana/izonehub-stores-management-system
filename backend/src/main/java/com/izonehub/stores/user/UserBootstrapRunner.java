package com.izonehub.stores.user;

import com.izonehub.stores.notification.EmailNotificationGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Component
public class UserBootstrapRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(UserBootstrapRunner.class);

    private final UserRepository userRepository;
    private final EmailNotificationGateway emailGateway;

    @Value("${app.url:https://stores-app-b96j.onrender.com/}")
    private String appUrl;

    public UserBootstrapRunner(UserRepository userRepository, EmailNotificationGateway emailGateway) {
        this.userRepository = userRepository;
        this.emailGateway = emailGateway;
    }

    @Override
    @Transactional
    public void run(String... args) {
        // Do not run if --create-admin flag is present
        if (List.of(args).contains("--create-admin")) {
            return;
        }

        log.info("Starting UserBootstrapRunner to scan for welcome emails that need to be sent...");

        List<AppUser> unsentUsers = userRepository.findByWelcomeEmailSentFalse();
        if (unsentUsers.isEmpty()) {
            log.info("No unsent welcome emails found.");
            return;
        }

        log.info("Found {} user(s) with pending welcome emails. Dispatched emails starting...", unsentUsers.size());

        for (AppUser user : unsentUsers) {
            try {
                String subject = "Account Created - Sahara Ventures Stores Management System";
                // The temporary password seeded in V24 is 'password123'
                String tempPassword = "password123";
                
                String message = String.format(
                    "Hie %s, your account has been created for the new sahara ventures stores management system, and your login details are as follows:\n\n" +
                    "Email: %s\n" +
                    "Password: %s\n\n" +
                    "Currently you can access it at %s",
                    user.getFullName(), user.getEmail(), tempPassword, appUrl
                );

                emailGateway.send(user, subject, message);
                user.setWelcomeEmailSent(true);
                userRepository.save(user);
                
                log.info("Successfully sent welcome email and marked welcome_email_sent=true for {}", user.getEmail());
            } catch (Exception e) {
                log.error("Failed to send welcome email to {}: {}", user.getEmail(), e.getMessage(), e);
            }
        }

        log.info("UserBootstrapRunner welcome email dispatch complete.");
    }
}
