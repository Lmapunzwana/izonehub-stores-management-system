package com.izonehub.stores.movement;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface DiscrepancyRepository extends JpaRepository<Discrepancy, UUID> {
    List<Discrepancy> findByStatusAndCreatedAtBefore(DiscrepancyStatus status, Instant before);
}
