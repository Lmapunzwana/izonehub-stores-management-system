package com.izonehub.stores.inventory;

import com.izonehub.stores.notification.NotificationService;
import com.izonehub.stores.notification.NotificationType;
import com.izonehub.stores.user.Role;
import com.izonehub.stores.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class LowStockPolicy {

    private final NotificationService        notifications;
    private final UserRepository             users;
    private final LowStockThresholdRepository thresholds;

    public LowStockPolicy(NotificationService notifications,
                          UserRepository users,
                          LowStockThresholdRepository thresholds) {
        this.notifications = notifications;
        this.users         = users;
        this.thresholds    = thresholds;
    }

    @Transactional
    public void evaluate(StoreInventory inventory) {
        // Per-store threshold takes precedence over the item-level default
        BigDecimal threshold = thresholds
                .findByStoreAndItem(inventory.getStore(), inventory.getItem())
                .map(LowStockThreshold::getThreshold)
                .orElse(inventory.getItem().getReorderThreshold());

        if (inventory.getQuantityOnHand().compareTo(threshold) > 0) return;

        String message = "Low stock: %s at %.2f units (threshold: %.2f) — %s".formatted(
                inventory.getItem().getCode(),
                inventory.getQuantityOnHand(),
                threshold,
                inventory.getStore().getName()
        );

        if (inventory.getStore().getManager() != null) {
            notifications.notify(inventory.getStore().getManager(), NotificationType.LOW_STOCK, message);
        }

        users.findAll().stream()
                .filter(u -> u.getRoles().contains(Role.CENTRAL_STORE_MANAGER) && u.isActive())
                .forEach(u -> notifications.notify(u, NotificationType.LOW_STOCK, message));
    }
}
