package com.izonehub.stores.audit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {

    private final AuditLogRepository repo;

    public AuditLogService(AuditLogRepository repo) {
        this.repo = repo;
    }

    /**
     * Record an audit event. Uses REQUIRES_NEW so audit entries are always
     * persisted even if the calling transaction rolls back (e.g. failed access).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog record(String entityType, String entityId, String action,
                           String description, String oldState, String newState, String performedBy) {
        return repo.save(new AuditLog(entityType, entityId, action, description, oldState, newState, performedBy));
    }

    /** Backward-compatible 6-arg overload (no description). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog record(String entityType, String entityId, String action,
                           String oldState, String newState, String performedBy) {
        return repo.save(new AuditLog(entityType, entityId, action, oldState, newState, performedBy));
    }

    /** Convenience: action-only log (no state snapshot needed). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AuditLog record(String entityType, String entityId, String action,
                           String description, String performedBy) {
        return repo.save(new AuditLog(entityType, entityId, action, description, null, null, performedBy));
    }
}
