package com.izonehub.stores.issuance;

import com.izonehub.stores.audit.AuditLogService;
import com.izonehub.stores.inventory.InventoryCommandService;
import com.izonehub.stores.user.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockAdjustmentCommandService {
    private final StockAdjustmentRepository adjustments;
    private final AuditLogService auditLog;
    private final InventoryCommandService inventory;

    public StockAdjustmentCommandService(StockAdjustmentRepository adjustments, AuditLogService auditLog,
                                         InventoryCommandService inventory) {
        this.adjustments = adjustments;
        this.auditLog = auditLog;
        this.inventory = inventory;
    }

    @Transactional
    public StockAdjustment raise(StockAdjustment adjustment, AppUser performedBy) {
        StockAdjustment saved = adjustments.save(adjustment);
        // Actually apply the corrected quantity to the store's inventory —
        // previously this method only persisted the adjustment record without
        // ever touching StoreInventory, so counts could never actually be
        // reconciled against real stock levels.
        inventory.adjustTo(saved.getStore(), saved.getItem(), saved.getQuantityAfter());
        auditLog.record("StockAdjustment", saved.getId().toString(), "RAISE", null, "{}", performedBy.getEmail());
        return saved;
    }

    @Transactional
    public StockAdjustment countersign(StockAdjustment adjustment, AppUser countersignedBy) {
        adjustment.countersign(countersignedBy);
        StockAdjustment saved = adjustments.save(adjustment);
        auditLog.record("StockAdjustment", saved.getId().toString(), "COUNTERSIGN", null, "{}", countersignedBy.getEmail());
        return saved;
    }
}
