package com.izonehub.stores.store;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

public interface StoreRepository extends JpaRepository<Store, UUID> {
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"manager"})
    Page<Store> findByActive(boolean active, Pageable pageable);
    long countByActiveTrueAndManager_Id(UUID managerId);
    long countByActiveTrueAndClosingFalse();
    boolean existsByType(StoreType type);
    java.util.List<Store> findByType(StoreType type);
    java.util.List<Store> findByManager_Id(UUID managerId);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"manager"})
    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT s FROM Store s WHERE s.manager.id = :userId OR s.id IN (SELECT u.assignedStore.id FROM com.izonehub.stores.user.AppUser u WHERE u.id = :userId AND u.assignedStore IS NOT NULL) OR s.id IN (SELECT p.siteStore.id FROM com.izonehub.stores.project.Project p JOIN p.assignedEmployees emp WHERE emp.id = :userId)")
    java.util.List<Store> findStoresForUser(@org.springframework.data.repository.query.Param("userId") UUID userId);

    /**
     * Bulk-clears the manager reference on every store. Used only by the
     * system-reset flow, to break the Store -> AppUser edge before any user
     * is deleted. clearAutomatically = true evicts every Store already
     * loaded into the persistence context this transaction, so nothing
     * downstream can flush a stale in-memory reference to a manager that no
     * longer exists — without it, a Store object loaded earlier in the same
     * transaction keeps its old in-memory `manager` field even after this
     * UPDATE lands in the database, and Hibernate trips over that
     * inconsistency at the next flush (TransientObjectException).
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("UPDATE Store s SET s.manager = null")
    void detachManagersForReset();
}
