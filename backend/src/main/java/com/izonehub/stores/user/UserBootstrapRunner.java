package com.izonehub.stores.user;

import com.izonehub.stores.store.Store;
import com.izonehub.stores.store.StoreRepository;
import com.izonehub.stores.store.StoreType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class UserBootstrapRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(UserBootstrapRunner.class);

    private final UserRepository userRepository;
    private final StoreRepository storeRepository;
    private final UserCommandService userCommandService;

    public UserBootstrapRunner(UserRepository userRepository, StoreRepository storeRepository, UserCommandService userCommandService) {
        this.userRepository = userRepository;
        this.storeRepository = storeRepository;
        this.userCommandService = userCommandService;
    }

    @Override
    @Transactional
    public void run(String... args) {
        // Do not run if --create-admin flag is present, to avoid interfering with it
        if (List.of(args).contains("--create-admin")) {
            return;
        }

        log.info("Starting UserBootstrapRunner to seed new team members...");

        // Ensure "Athlone and Head Office" type is CENTRAL
        Store athlone = findStoreByNamePart("athlone");
        if (athlone != null && athlone.getType() != StoreType.CENTRAL) {
            athlone.setType(StoreType.CENTRAL);
            storeRepository.save(athlone);
            log.info("Updated store '{}' type to CENTRAL", athlone.getName());
        }

        // 1. Petronelah Majaja -> Central Store Manager at Athlone and Head Office
        bootstrapUser("Petronelah Majaja", "petronelah@newsaharaventures.com", Role.CENTRAL_STORE_MANAGER, "athlone");

        // 2. Artwell Kanjanda -> Site Store Manager at Site 1 - SABI
        bootstrapUser("Artwell Kanjanda", "artwell@newsaharaventures.com", Role.SITE_STORE_MANAGER, "sabi");

        // 3. Benedict Kwashira -> Site Store Manager at SITE 2 - SUNPORTS
        bootstrapUser("Benedict Kwashira", "benedict@newsaharaventures.com", Role.SITE_STORE_MANAGER, "sunports");

        // 4. Ronald Tsatsi -> Site Store Manager at SITE 3 - Murombedzi
        bootstrapUser("Ronald Tsatsi", "ronald@newsaharaventures.com", Role.SITE_STORE_MANAGER, "murombedzi");

        // 5. Leroy Mapunzwana -> Site Store Manager (testing account) at Site 1 - SABI
        bootstrapUser("Leroy Mapunzwana", "lmapunzwana@talksal.com", Role.SITE_STORE_MANAGER, "sabi");

        // 6. Leroy Mapunzwana -> Central Store Manager (testing account) at Athlone and Head Office
        bootstrapUser("Leroy Mapunzwana", "leroymapunzwana@gmail.com", Role.CENTRAL_STORE_MANAGER, "athlone");

        log.info("UserBootstrapRunner seeding complete.");
    }

    private void bootstrapUser(String fullName, String email, Role role, String storeNamePart) {
        String normalizedEmail = email.toLowerCase();
        Optional<AppUser> existing = userRepository.findByEmail(normalizedEmail);
        if (existing.isPresent()) {
            log.info("User '{}' ({}) already exists. Skipping bootstrap.", fullName, normalizedEmail);
            return;
        }

        Store store = findStoreByNamePart(storeNamePart);
        if (store == null) {
            log.warn("Could not find store matching '{}' for user '{}'. Skipping creation.", storeNamePart, fullName);
            return;
        }

        try {
            // Using a temporary password that satisfies the policy (length >= 8, has digit, has special char)
            String tempPassword = "ChangeMe123!";
            AppUser created = userCommandService.createUser(fullName, normalizedEmail, tempPassword, Set.of(role), store, null);
            log.info("Created bootstrapped user '{}' ({}) and sent welcome email", fullName, normalizedEmail);

            // Assign this user as the manager of their assigned store
            store.setManager(created);
            storeRepository.save(store);
            log.info("Assigned user '{}' as manager of store '{}'", fullName, store.getName());
        } catch (Exception e) {
            log.error("Failed to bootstrap user '{}' ({}): {}", fullName, normalizedEmail, e.getMessage(), e);
        }
    }

    private Store findStoreByNamePart(String part) {
        return storeRepository.findAll().stream()
                .filter(s -> s.getName().toLowerCase().contains(part.toLowerCase()))
                .findFirst()
                .orElse(null);
    }
}
