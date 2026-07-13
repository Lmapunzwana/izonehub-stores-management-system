package com.izonehub.stores.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/audit-log")
@PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER')")
public class AuditLogController {

    private final AuditLogRepository repo;

    public AuditLogController(AuditLogRepository repo) { this.repo = repo; }

    @GetMapping
    public Page<AuditLog> list(
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "50")  int size,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String performedBy) {

        List<AuditLog> all = repo.findAll().stream()
                .filter(a -> entityType   == null || a.getEntityType().equalsIgnoreCase(entityType))
                .filter(a -> action       == null || a.getAction().equalsIgnoreCase(action))
                .filter(a -> performedBy  == null || a.getPerformedBy().equalsIgnoreCase(performedBy))
                .sorted((a, b) -> b.getPerformedAt().compareTo(a.getPerformedAt()))
                .toList();

        int total = all.size(), from = Math.min(page * size, total), to = Math.min(from + size, total);
        return new PageImpl<>(all.subList(from, to), PageRequest.of(page, size), total);
    }
}
