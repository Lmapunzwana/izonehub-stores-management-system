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
        if (!List.of(args).contains("--create-admin")) {
            return; // normal boot — no-op, does not touch the DB
        }

        String email = System.getenv("ADMIN_EMAIL");
        String password = System.getenv("ADMIN_PASSWORD");

        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            System.err.println("--create-admin requires ADMIN_EMAIL and ADMIN_PASSWORD env vars to be set.");
            System.exit(1);
        }

        String normalizedEmail = email.toLowerCase();
        if (users.existsByEmail(normalizedEmail)) {
            System.err.println("A user with email " + normalizedEmail + " already exists. Refusing to create a duplicate.");
            System.exit(1);
        }

        AppUser admin = new AppUser("System Administrator", normalizedEmail,
                encoder.encode(password), java.util.Set.of(Role.SYSTEM_ADMINISTRATOR), null, null);
        users.save(admin);

        System.out.println("Admin created: " + normalizedEmail);
        System.exit(0);
    }
}
