package com.izonehub.stores.audit;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "idx_audit_entity",    columnList = "entity_type, entity_id"),
        @Index(name = "idx_audit_performed", columnList = "performed_at DESC"),
        @Index(name = "idx_audit_actor",     columnList = "performed_by")
})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Domain entity type, e.g. "MATERIAL_REQUEST", "GRN", "STOCK_ADJUSTMENT". */
    @Column(name = "entity_type", nullable = false, length = 60)
    private String entityType;

    @Column(name = "entity_id", nullable = false, length = 36)
    private String entityId;

    /** Upper-case verb, e.g. "CREATED", "APPROVED", "DISPATCHED", "ACCESS_DENIED". */
    @Column(nullable = false, length = 60)
    private String action;

    /** Human-readable summary shown in the audit log table. */
    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "old_state", columnDefinition = "text")
    private String oldState;

    @Column(name = "new_state", columnDefinition = "text")
    private String newState;

    @Column(name = "performed_by", nullable = false, length = 255)
    private String performedBy;

    @Column(name = "performed_at", nullable = false)
    private Instant performedAt = Instant.now();

    protected AuditLog() {}

    public AuditLog(String entityType, String entityId, String action,
                    String description, String oldState, String newState, String performedBy) {
        this.entityType  = entityType;
        this.entityId    = entityId;
        this.action      = action;
        this.description = description;
        this.oldState    = oldState;
        this.newState    = newState;
        this.performedBy = performedBy;
    }

    // ── Backward-compatible 6-arg constructor (old code path) ─────────────────
    public AuditLog(String entityType, String entityId, String action,
                    String oldState, String newState, String performedBy) {
        this(entityType, entityId, action, null, oldState, newState, performedBy);
    }

    // ── Getters ────────────────────────────────────────────────────────────────
    public UUID    getId()          { return id; }
    public String  getEntityType()  { return entityType; }
    public String  getEntityId()    { return entityId; }
    public String  getAction()      { return action; }
    public String  getDescription() { return description; }
    public String  getOldState()    { return oldState; }
    public String  getNewState()    { return newState; }
    public String  getPerformedBy() { return performedBy; }
    public Instant getPerformedAt() { return performedAt; }
}
