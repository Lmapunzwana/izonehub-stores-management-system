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
     *
     * clearAutomatically = true evicts the persistence context after this
     * runs. Without it, any AppUser already loaded earlier in the same
     * transaction keeps its stale in-memory createdBy reference even after
     * this UPDATE lands in the database — and a later findAll() in the same
     * transaction returns those same stale managed instances (Hibernate's
     * session-level identity map), not fresh ones. Flushing that stale state
     * against rows that have since changed is exactly what produces a
     * TransientObjectException here.
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE AppUser u SET u.createdBy = null")
    void detachCreatedByForReset();

    /**
     * Bulk-clears assignedStore on every user. Used only by the system-reset
     * flow, replacing a previous findAll()+setAssignedStore(null)+saveAll()
     * loop — that approach loaded every Store into the session too (via the
     * assignedStore entity graph on findAll() below), leaving those Store
     * objects, including their manager field, resident and stale for the
     * rest of the transaction. This is a bulk update with the same
     * clearAutomatically safety as detachCreatedByForReset().
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE AppUser u SET u.assignedStore = null")
    void detachAssignedStoresForReset();
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
