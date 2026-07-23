package com.izonehub.stores.movement;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MaterialRequestRepository extends JpaRepository<MaterialRequest, UUID> {

    @EntityGraph(attributePaths = {"requestingStore", "sourceStore", "project", "lines", "lines.item"})
    List<MaterialRequest> findAll();

    @EntityGraph(attributePaths = {"requestingStore", "sourceStore", "project", "lines", "lines.item"})
    Optional<MaterialRequest> findById(UUID id);

    // These push both the status filter and pagination to the database
    // (the EntityGraph avoids N+1, but a plain findAll() still means every
    // row comes back before Java slices out one page — the below don't).
    @EntityGraph(attributePaths = {"requestingStore", "sourceStore", "project", "lines", "lines.item"})
    @Override
    Page<MaterialRequest> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"requestingStore", "sourceStore", "project", "lines", "lines.item"})
    Page<MaterialRequest> findByStatus(MaterialRequestStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"requestingStore", "sourceStore", "project", "lines", "lines.item"})
    Page<MaterialRequest> findByRequestingStore_IdIn(java.util.List<UUID> requestingStoreIds, Pageable pageable);

    @EntityGraph(attributePaths = {"requestingStore", "sourceStore", "project", "lines", "lines.item"})
    Page<MaterialRequest> findByStatusAndRequestingStore_IdIn(MaterialRequestStatus status, java.util.List<UUID> requestingStoreIds, Pageable pageable);

    @EntityGraph(attributePaths = {"requestingStore", "sourceStore", "project", "lines", "lines.item"})
    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT r FROM MaterialRequest r WHERE (r.requestingStore.id IN :storeIds OR r.sourceStore.id IN :storeIds OR r.raisedBy.id = :userId) AND (:status IS NULL OR r.status = :status)")
    Page<MaterialRequest> findForSiteManager(
            @org.springframework.data.repository.query.Param("storeIds") java.util.List<UUID> storeIds,
            @org.springframework.data.repository.query.Param("userId") UUID userId,
            @org.springframework.data.repository.query.Param("status") MaterialRequestStatus status,
            Pageable pageable);
}
