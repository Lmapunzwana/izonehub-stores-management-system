package com.izonehub.stores.issuance;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface StockAdjustmentRepository extends JpaRepository<StockAdjustment, UUID> {
}
