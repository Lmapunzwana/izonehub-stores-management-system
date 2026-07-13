package com.izonehub.stores.movement;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface DispatchRepository extends JpaRepository<Dispatch, UUID> {
    Optional<Dispatch> findByMaterialRequest_Id(UUID materialRequestId);
}
