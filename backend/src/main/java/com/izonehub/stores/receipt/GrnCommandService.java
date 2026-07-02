package com.izonehub.stores.receipt;

import com.izonehub.stores.inventory.InventoryCommandService;
import com.izonehub.stores.notification.NotificationService;
import com.izonehub.stores.notification.NotificationType;
import com.izonehub.stores.user.AppUser;
import com.izonehub.stores.user.Role;
import com.izonehub.stores.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GrnCommandService {
    private final ExpectedReceiptRepository expectedReceipts;
    private final GoodsReceivedNoteRepository grns;
    private final InventoryCommandService inventory;
    private final NotificationService notifications;
    private final UserRepository users;

    public GrnCommandService(ExpectedReceiptRepository expectedReceipts, GoodsReceivedNoteRepository grns,
                             InventoryCommandService inventory, NotificationService notifications, UserRepository users) {
        this.expectedReceipts = expectedReceipts;
        this.grns = grns;
        this.inventory = inventory;
        this.notifications = notifications;
        this.users = users;
    }

    @Transactional
    public GoodsReceivedNote confirm(ExpectedReceipt expectedReceipt, AppUser receivedBy) {
        boolean hasVariance = expectedReceipt.getLines().stream().anyMatch(ExpectedReceiptLine::hasVariance);
        expectedReceipt.getLines().forEach(line -> {
            if (line.getReceivedQuantity().signum() == 0) {
                return;
            }
            if (line.getCondition() == ReceiptLineCondition.GOOD) {
                inventory.receive(expectedReceipt.getStore(), line.getItem(), line.getReceivedQuantity());
            } else if (line.getCondition() == ReceiptLineCondition.DAMAGED) {
                inventory.receiveDamaged(expectedReceipt.getStore(), line.getItem(), line.getReceivedQuantity());
            }
        });
        expectedReceipt.markCompleted(hasVariance);
        expectedReceipts.save(expectedReceipt);
        GoodsReceivedNote grn = grns.save(new GoodsReceivedNote(referenceFor(expectedReceipt), expectedReceipt, receivedBy,
                hasVariance ? GrnStatus.VARIANCE : GrnStatus.CLEAN));
        if (hasVariance) {
            notifyProcurement("GRN variance on " + grn.getReferenceNumber());
        }
        return grn;
    }

    private String referenceFor(ExpectedReceipt expectedReceipt) {
        String suffix = expectedReceipt.getId() == null ? String.valueOf(System.nanoTime()) : expectedReceipt.getId().toString().substring(0, 8);
        return "GRN-" + suffix.toUpperCase();
    }

    private void notifyProcurement(String message) {
        users.findAll().stream()
                .filter(user -> user.getRole() == Role.PROCUREMENT_OFFICER && user.isActive())
                .forEach(user -> notifications.notify(user, NotificationType.GRN_VARIANCE, message));
    }
}
