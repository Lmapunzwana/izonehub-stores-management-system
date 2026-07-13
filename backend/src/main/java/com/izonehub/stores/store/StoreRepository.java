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
}
