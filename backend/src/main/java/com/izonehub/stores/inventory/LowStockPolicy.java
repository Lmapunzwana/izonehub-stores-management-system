package com.izonehub.stores.inventory;

import com.izonehub.stores.notification.NotificationService;
import com.izonehub.stores.notification.NotificationType;
import com.izonehub.stores.user.Role;
import com.izonehub.stores.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LowStockPolicy {
    private final NotificationService notifications;
    private final UserRepository users;

    public LowStockPolicy(NotificationService notifications, UserRepository users) {
        this.notifications = notifications;
        this.users = users;
    }

    @Transactional
    public void evaluate(StoreInventory inventory) {
        if (inventory.getQuantityOnHand().compareTo(inventory.getItem().getReorderThreshold()) > 0) {
            return;
        }
        var message = "Low stock: " + inventory.getItem().getCode() + " at " + inventory.getQuantityOnHand();
        if (inventory.getStore().getManager() != null) {
            notifications.notify(inventory.getStore().getManager(), NotificationType.LOW_STOCK, message);
        }
        users.findAll().stream()
                .filter(user -> user.getRole() == Role.PROCUREMENT_OFFICER && user.isActive())
                .forEach(user -> notifications.notify(user, NotificationType.LOW_STOCK, message));
    }
}
