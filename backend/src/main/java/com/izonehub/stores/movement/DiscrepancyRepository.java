package com.izonehub.stores.movement;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DiscrepancyRepository extends JpaRepository<Discrepancy, UUID> {
    List<Discrepancy> findByStatusAndCreatedAtBefore(DiscrepancyStatus status, Instant before);

    // The old controller loaded every discrepancy, then manually touched
    // 5-7 lazy associations per row (item, receipt->materialRequest->
    // project/stores, grn, resolvedBy) *before* slicing out one page — an
    // N+1 that got worse every time this table grew. One EntityGraph query
    // fetches all of it up front instead.
    @EntityGraph(attributePaths = {
            "item", "grn", "receipt", "receipt.materialRequest", "receipt.materialRequest.project",
            "receipt.materialRequest.sourceStore", "receipt.materialRequest.requestingStore", "resolvedBy"
    })
    @Override
    Page<Discrepancy> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {
            "item", "grn", "receipt", "receipt.materialRequest", "receipt.materialRequest.project",
            "receipt.materialRequest.sourceStore", "receipt.materialRequest.requestingStore", "resolvedBy"
    })
    Page<Discrepancy> findByStatus(DiscrepancyStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {
            "item", "grn", "receipt", "receipt.materialRequest", "receipt.materialRequest.project",
            "receipt.materialRequest.sourceStore", "receipt.materialRequest.requestingStore", "resolvedBy"
    })
    @Override
    java.util.Optional<Discrepancy> findById(UUID id);
}
