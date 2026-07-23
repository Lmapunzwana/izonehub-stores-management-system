package com.izonehub.stores.store;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StoreRepository extends JpaRepository<Store, UUID> {
    Page<Store> findByActive(boolean active, Pageable pageable);
    long countByActiveTrueAndManager_Id(UUID managerId);
    long countByActiveTrueAndClosingFalse();
    boolean existsByType(StoreType type);
    java.util.List<Store> findByManager_Id(UUID managerId);

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT s FROM Store s WHERE s.manager.id = :userId OR s.id IN (SELECT u.assignedStore.id FROM AppUser u WHERE u.id = :userId AND u.assignedStore IS NOT NULL)")
    java.util.List<Store> findStoresForUser(@org.springframework.data.repository.query.Param("userId") UUID userId);
}
