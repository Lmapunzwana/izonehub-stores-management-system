package com.izonehub.stores.batch;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/batches")
public class BatchController {

    private final BatchRepository batches;

    public BatchController(BatchRepository batches) {
        this.batches = batches;
    }

    @GetMapping
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER','SITE_STORE_MANAGER')")
    public List<BatchResponse> list() {
        return batches.findAll().stream().map(b -> new BatchResponse(
                b.getBatchNo(),
                b.getItem() != null ? b.getItem().getName() : "Unknown Item",
                b.getSerials().stream().map(SerialNumber::getSerialNo).collect(Collectors.toList()),
                b.getReceivedVia(),
                b.getProject(),
                b.getStatus(),
                b.getExpiryDate() != null ? b.getExpiryDate().toString() : null
        )).collect(Collectors.toList());
    }

    public record BatchResponse(
            String batchNo,
            String item,
            List<String> serials,
            String receivedVia,
            String project,
            String status,
            String expiryDate
    ) {}
}
