package com.izonehub.stores.user;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Explicit, one-off admin creation — analogous to Django's `createsuperuser`.
 * Does NOT run on normal application boot. Only fires when the app is
 * launched with the --create-admin flag, then exits immediately.
 *
 * Usage (docker compose):
 *   docker compose run --rm \
 *     -e ADMIN_EMAIL=admin@newsahara.com \
 *     -e ADMIN_PASSWORD=ChangeMeImmediately123! \
 *     backend --create-admin
 */
@Component
public class AdminBootstrapRunner implements CommandLineRunner {

    private final UserRepository users;
    private final PasswordEncoder encoder;

    public AdminBootstrapRunner(UserRepository users, PasswordEncoder encoder) {
        this.users = users;
        this.encoder = encoder;
    }

    @Override
    public void run(String... args) {
        String email = System.getenv("ADMIN_EMAIL");
        if (email == null || email.isBlank()) {
            email = "lmapunzwana@gmail.com"; // Default admin email
        }
        String password = System.getenv("ADMIN_PASSWORD");

        String normalizedEmail = email.toLowerCase();
        AppUser admin = users.findByEmail(normalizedEmail).orElse(null);

        if (admin != null) {
            boolean changed = false;
            if (!admin.getRoles().contains(Role.SYSTEM_ADMINISTRATOR)) {
                admin.getRoles().add(Role.SYSTEM_ADMINISTRATOR);
                changed = true;
            }
            if (!admin.isActive() || admin.isLocked()) {
                admin.unlock();
                changed = true;
            }
            if (password != null && !password.isBlank() && List.of(args).contains("--create-admin")) {
                admin.changePassword(encoder.encode(password));
                changed = true;
            }
            if (changed) {
                users.save(admin);
                System.out.println("Admin account synced with SYSTEM_ADMINISTRATOR role: " + normalizedEmail);
            }
        } else if (password != null && !password.isBlank()) {
            admin = new AppUser("System Administrator", normalizedEmail,
                    encoder.encode(password), java.util.Set.of(Role.SYSTEM_ADMINISTRATOR), null, null);
            users.save(admin);
            System.out.println("Admin account created with SYSTEM_ADMINISTRATOR role: " + normalizedEmail);
        }

        if (List.of(args).contains("--create-admin")) {
            System.exit(0);
        }
    }
}
