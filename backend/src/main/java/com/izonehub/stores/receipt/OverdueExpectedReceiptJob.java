package com.izonehub.stores.receipt;

import com.izonehub.stores.notification.NotificationService;
import com.izonehub.stores.notification.NotificationType;
import com.izonehub.stores.user.Role;
import com.izonehub.stores.user.UserRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;

@Component
public class OverdueExpectedReceiptJob {
    private final ExpectedReceiptRepository expectedReceipts;
    private final UserRepository users;
    private final NotificationService notifications;

    public OverdueExpectedReceiptJob(ExpectedReceiptRepository expectedReceipts, UserRepository users, NotificationService notifications) {
        this.expectedReceipts = expectedReceipts;
        this.users = users;
        this.notifications = notifications;
    }

    @Scheduled(cron = "0 0 7 * * MON-SAT", zone = "Africa/Harare")
    @Transactional
    public void flagOverdueReceipts() {
        LocalDate today = LocalDate.now();
        expectedReceipts.findByStatusAndExpectedDateBefore(ExpectedReceiptStatus.PENDING, today).forEach(receipt -> {
            receipt.markOverdue(today);
            notifyProcurement("Expected receipt overdue from " + receipt.getSupplierName());
        });
    }

    private void notifyProcurement(String message) {
        users.findAll().stream()
                .filter(user -> user.getRole() == Role.PROCUREMENT_OFFICER && user.isActive())
                .forEach(user -> notifications.notify(user, NotificationType.EXPECTED_RECEIPT_OVERDUE, message));
    }
}
