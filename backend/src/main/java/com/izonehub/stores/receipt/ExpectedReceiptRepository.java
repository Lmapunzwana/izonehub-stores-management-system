package com.izonehub.stores.receipt;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ExpectedReceiptRepository extends JpaRepository<ExpectedReceipt, UUID> {
    List<ExpectedReceipt> findByStatusAndExpectedDateBefore(ExpectedReceiptStatus status, LocalDate date);
}
