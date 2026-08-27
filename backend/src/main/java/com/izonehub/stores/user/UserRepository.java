package com.izonehub.stores.user;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

public interface UserRepository extends JpaRepository<AppUser,UUID>{

    /**
     * Bulk-clears the self-referencing createdBy column on every user.
     * Used only by the system-reset flow: createdBy has no public setter
     * (it's an immutable audit field in normal operation), and a reset needs
     * to break this self-reference before any user rows can be deleted.
     */
    @Modifying
    @Transactional
    @Query("UPDATE AppUser u SET u.createdBy = null")
    void detachCreatedByForReset();
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"assignedStore", "roles"})
    List<AppUser> findAll();

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"assignedStore", "roles"})
    Optional<AppUser> findById(UUID id);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"assignedStore", "roles"})
    Optional<AppUser> findByEmail(String email);
    boolean existsByEmail(String email);
    
    List<AppUser> findByAssignedStore(com.izonehub.stores.store.Store store);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"assignedStore", "roles"})
    List<AppUser> findByWelcomeEmailSentFalse();
}
