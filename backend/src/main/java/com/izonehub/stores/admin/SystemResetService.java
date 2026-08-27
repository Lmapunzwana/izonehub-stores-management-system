package com.izonehub.stores.admin;

import com.izonehub.stores.audit.AuditLog;
import com.izonehub.stores.audit.AuditLogRepository;
import com.izonehub.stores.auth.PasswordResetTokenRepository;
import com.izonehub.stores.batch.BatchRepository;
import com.izonehub.stores.count.StockCountRepository;
import com.izonehub.stores.inventory.InventoryRepository;
import com.izonehub.stores.inventory.LowStockThresholdRepository;
import com.izonehub.stores.issuance.MaterialIssueVoucherRepository;
import com.izonehub.stores.issuance.StockAdjustmentRepository;
import com.izonehub.stores.issuance.StockReturnRepository;
import com.izonehub.stores.item.ItemRepository;
import com.izonehub.stores.movement.DiscrepancyRepository;
import com.izonehub.stores.movement.DispatchRepository;
import com.izonehub.stores.movement.MaterialRequestRepository;
import com.izonehub.stores.movement.ReceiptRepository;
import com.izonehub.stores.notification.NotificationRepository;
import com.izonehub.stores.project.ProjectRepository;
import com.izonehub.stores.receipt.ExpectedReceiptRepository;
import com.izonehub.stores.receipt.GoodsReceivedNoteRepository;
import com.izonehub.stores.store.StoreRepository;
import com.izonehub.stores.supplier.SupplierRepository;
import com.izonehub.stores.user.AppUser;
import com.izonehub.stores.user.Role;
import com.izonehub.stores.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Wipes ALL business/transactional data (every store, item, project, material
 * request and every downstream record) while preserving SYSTEM_ADMINISTRATOR
 * accounts, so the platform can be reset to a clean slate before go-live.
 *
 * This is intentionally NOT exposed as per-resource "delete" buttons: hard
 * deletes on Items/Stores/Projects/MaterialRequests that already have real
 * movement history would either violate FK constraints or silently destroy
 * audit trail. This is a single, explicit, all-or-nothing operation instead.
 *
 * Deletion order matters — it walks the FK graph leaf-to-root. Entities with
 * JPA cascade=ALL/orphanRemoval children (MaterialRequest -> lines,
 * MaterialIssueVoucher -> lines, StockReturn -> lines, StockCount -> lines,
 * ExpectedReceipt -> lines, Batch -> serials) are removed via deleteAll() so
 * Hibernate cascades to the child rows; deleteAllInBatch() is only used for
 * entities with no cascading children, since batch deletes bypass cascade.
 */
@Service
public class SystemResetService {

    private final UserRepository users;
    private final StoreRepository stores;
    private final ItemRepository items;
    private final ProjectRepository projects;
    private final SupplierRepository suppliers;
    private final MaterialRequestRepository materialRequests;
    private final DispatchRepository dispatches;
    private final ReceiptRepository receipts;
    private final DiscrepancyRepository discrepancies;
    private final GoodsReceivedNoteRepository grns;
    private final ExpectedReceiptRepository expectedReceipts;
    private final StockAdjustmentRepository stockAdjustments;
    private final StockReturnRepository stockReturns;
    private final MaterialIssueVoucherRepository mivs;
    private final StockCountRepository stockCounts;
    private final InventoryRepository storeInventory;
    private final LowStockThresholdRepository lowStockThresholds;
    private final BatchRepository batches;
    private final NotificationRepository notifications;
    private final PasswordResetTokenRepository passwordResetTokens;
    private final AuditLogRepository auditLogs;

    public SystemResetService(UserRepository users, StoreRepository stores, ItemRepository items,
                               ProjectRepository projects, SupplierRepository suppliers,
                               MaterialRequestRepository materialRequests, DispatchRepository dispatches,
                               ReceiptRepository receipts, DiscrepancyRepository discrepancies,
                               GoodsReceivedNoteRepository grns, ExpectedReceiptRepository expectedReceipts,
                               StockAdjustmentRepository stockAdjustments, StockReturnRepository stockReturns,
                               MaterialIssueVoucherRepository mivs, StockCountRepository stockCounts,
                               InventoryRepository storeInventory, LowStockThresholdRepository lowStockThresholds,
                               BatchRepository batches, NotificationRepository notifications,
                               PasswordResetTokenRepository passwordResetTokens, AuditLogRepository auditLogs) {
        this.users = users;
        this.stores = stores;
        this.items = items;
        this.projects = projects;
        this.suppliers = suppliers;
        this.materialRequests = materialRequests;
        this.dispatches = dispatches;
        this.receipts = receipts;
        this.discrepancies = discrepancies;
        this.grns = grns;
        this.expectedReceipts = expectedReceipts;
        this.stockAdjustments = stockAdjustments;
        this.stockReturns = stockReturns;
        this.mivs = mivs;
        this.stockCounts = stockCounts;
        this.storeInventory = storeInventory;
        this.lowStockThresholds = lowStockThresholds;
        this.batches = batches;
        this.notifications = notifications;
        this.passwordResetTokens = passwordResetTokens;
        this.auditLogs = auditLogs;
    }

    @Transactional
    public void resetToCleanSlate(String performedByEmail) {
        // 1. Break the two circular FKs (Store.manager <-> AppUser.assignedStore,
        //    AppUser.createdBy self-reference) before anything is deleted.
        List<AppUser> allUsers = users.findAll();
        for (AppUser u : allUsers) {
            u.setAssignedStore(null);
        }
        users.saveAll(allUsers);
        // createdBy has no public setter by design (it's an immutable audit field),
        // so we clear it with a bulk update rather than adding a mutator for a
        // one-off reset path.
        users.detachCreatedByForReset();

        // 2. Leaf records that reference AppUser only.
        notifications.deleteAllInBatch();
        passwordResetTokens.deleteAllInBatch();

        // 3. Records that reference Receipt/GRN/StockReturn/StockCount + Item
        //    (must go before those parents).
        discrepancies.deleteAllInBatch();

        // 4. GRN references ExpectedReceipt + Store + AppUser; must precede both.
        grns.deleteAllInBatch();

        // 5. Dispatch/Receipt reference MaterialRequest + AppUser; must precede
        //    MaterialRequest.
        dispatches.deleteAllInBatch();
        receipts.deleteAllInBatch();

        // 6. StockAdjustment references Store + Item + AppUser.
        stockAdjustments.deleteAllInBatch();

        // 7. Parents with cascading line-item children — deleteAll() (not
        //    deleteAllInBatch()) so Hibernate cascades to the child tables.
        stockReturns.deleteAll();       // -> StockReturnLine
        mivs.deleteAll();               // -> MivLine
        materialRequests.deleteAll();   // -> MaterialRequestLine
        expectedReceipts.deleteAll();   // -> ExpectedReceiptLine
        batches.deleteAll();            // -> SerialNumber
        stockCounts.deleteAll();        // -> StockCountLine

        // 8. Plain per-store/per-item records.
        storeInventory.deleteAllInBatch();
        lowStockThresholds.deleteAllInBatch();

        // 9. Project (references Store; ManyToMany join table project_employees
        //    is cleared automatically when the owning Project rows are removed).
        //    Everything that references Project (MaterialIssueVoucher,
        //    MaterialRequest) is already gone by this point.
        projects.deleteAllInBatch();

        // 10. Store (referenced by everything above; all of that is gone now).
        stores.deleteAllInBatch();

        // 11. Master data with no remaining dependents.
        items.deleteAllInBatch();
        suppliers.deleteAllInBatch();

        // 12. Audit trail itself.
        auditLogs.deleteAllInBatch();

        // 13. Every non-system-administrator user. assignedStore/createdBy were
        //     already cleared in step 1, so this can't violate any FK.
        List<AppUser> nonAdmins = users.findAll().stream()
                .filter(u -> !u.getRoles().contains(Role.SYSTEM_ADMINISTRATOR))
                .toList();
        users.deleteAll(nonAdmins);

        // 14. Leave a single fresh audit trail entry recording the reset itself.
        auditLogs.save(new AuditLog("SYSTEM", "ALL", "SYSTEM_RESET",
                "Platform reset to a clean slate: all stores, items, projects, "
                        + "material requests and downstream records were deleted. "
                        + "System administrator accounts were preserved.",
                null, null, performedByEmail));
    }
}
