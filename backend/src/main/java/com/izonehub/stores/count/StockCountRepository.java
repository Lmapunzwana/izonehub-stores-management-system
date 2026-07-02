package com.izonehub.stores.count;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface StockCountRepository extends JpaRepository<StockCount, UUID> {
}
