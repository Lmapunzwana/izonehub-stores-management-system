package com.izonehub.stores.receipt;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import java.util.Optional;

public interface ExpectedReceiptRepository extends JpaRepository<ExpectedReceipt, UUID> {
    
    @EntityGraph(attributePaths = {"store", "lines", "lines.item"})
    List<ExpectedReceipt> findAll();
    
    @EntityGraph(attributePaths = {"store", "lines", "lines.item"})
    Optional<ExpectedReceipt> findById(UUID id);
    
    List<ExpectedReceipt> findByStatusAndExpectedDateBefore(ExpectedReceiptStatus status, LocalDate date);
}
