package com.izonehub.stores.inventory;

import com.izonehub.stores.batch.Batch;
import com.izonehub.stores.batch.BatchRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/inventory/expiring")
public class ExpiryController {

    private final BatchRepository batches;

    public ExpiryController(BatchRepository batches) {
        this.batches = batches;
    }

    @GetMapping
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER','SITE_STORE_MANAGER')")
    public List<ExpiryResponse> listExpiringItems() {
        return batches.findAll().stream()
                .filter(b -> b.getExpiryDate() != null)
                .map(b -> {
                    long daysRemaining = ChronoUnit.DAYS.between(LocalDate.now(), b.getExpiryDate());
                    Integer tier = daysRemaining <= 30 ? 30 : (daysRemaining <= 60 ? 60 : (daysRemaining <= 90 ? 90 : null));
                    
                    return new ExpiryResponse(
                            b.getItem() != null ? b.getItem().getName() : "Unknown Item",
                            b.getBatchNo(),
                            b.getExpiryDate().toString(),
                            (int) daysRemaining,
                            b.getSerials().size(), // Simple mock for quantity: number of serials
                            tier
                    );
                })
                .sorted((a, b) -> Integer.compare(a.daysRemaining(), b.daysRemaining()))
                .collect(Collectors.toList());
    }

    public record ExpiryResponse(
            String item,
            String batchNo,
            String expiryDate,
            int daysRemaining,
            int quantity,
            Integer tier
    ) {}
}
