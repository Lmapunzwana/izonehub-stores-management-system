package com.izonehub.stores.movement;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ReceiptRepository extends JpaRepository<Receipt, UUID> {
}
