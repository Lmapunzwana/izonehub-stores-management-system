package com.izonehub.stores.movement;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface MaterialRequestRepository extends JpaRepository<MaterialRequest, UUID> {
}
