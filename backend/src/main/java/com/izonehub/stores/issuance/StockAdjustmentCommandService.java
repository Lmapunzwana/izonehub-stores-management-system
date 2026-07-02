package com.izonehub.stores.issuance;

import com.izonehub.stores.audit.AuditLogService;
import com.izonehub.stores.user.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockAdjustmentCommandService {
    private final StockAdjustmentRepository adjustments;
    private final AuditLogService auditLog;

    public StockAdjustmentCommandService(StockAdjustmentRepository adjustments, AuditLogService auditLog) {
        this.adjustments = adjustments;
        this.auditLog = auditLog;
    }

    @Transactional
    public StockAdjustment raise(StockAdjustment adjustment, AppUser performedBy) {
        StockAdjustment saved = adjustments.save(adjustment);
        auditLog.record("StockAdjustment", saved.getId().toString(), "RAISE", null, "{}", performedBy.getEmail());
        return saved;
    }
}
