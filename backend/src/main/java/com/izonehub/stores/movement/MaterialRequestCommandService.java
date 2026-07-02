package com.izonehub.stores.movement;

import com.izonehub.stores.inventory.InventoryCommandService;
import com.izonehub.stores.notification.NotificationService;
import com.izonehub.stores.notification.NotificationType;
import com.izonehub.stores.user.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;

@Service
public class MaterialRequestCommandService {
    private final MaterialRequestRepository requests;
    private final DispatchRepository dispatches;
    private final ReceiptRepository receipts;
    private final DiscrepancyRepository discrepancies;
    private final InventoryCommandService inventory;
    private final NotificationService notifications;

    public MaterialRequestCommandService(MaterialRequestRepository requests, DispatchRepository dispatches,
                                         ReceiptRepository receipts, DiscrepancyRepository discrepancies,
                                         InventoryCommandService inventory, NotificationService notifications) {
        this.requests = requests;
        this.dispatches = dispatches;
        this.receipts = receipts;
        this.discrepancies = discrepancies;
        this.inventory = inventory;
        this.notifications = notifications;
    }

    @Transactional
    public MaterialRequest submit(MaterialRequest request) {
        request.submit();
        return requests.save(request);
    }

    @Transactional
    public MaterialRequest approve(MaterialRequest request, AppUser approver, List<BigDecimal> approvedQuantities) {
        if (approvedQuantities.size() != request.getLines().size()) {
            throw new IllegalArgumentException("Approved quantity count must match request lines");
        }
        for (int i = 0; i < request.getLines().size(); i++) {
            request.getLines().get(i).approve(approvedQuantities.get(i));
        }
        request.approve(approver);
        notifyRequester(request, "Material request approved");
        return requests.save(request);
    }

    @Transactional
    public MaterialRequest reject(MaterialRequest request, AppUser approver, String reason) {
        request.reject(approver, reason);
        notifyRequester(request, "Material request rejected: " + reason);
        return requests.save(request);
    }

    @Transactional
    public Dispatch dispatch(MaterialRequest request, AppUser dispatchedBy, String collectorName, String collectorEmployeeId,
                             List<BigDecimal> dispatchedQuantities) {
        if (dispatchedQuantities.size() != request.getLines().size()) {
            throw new IllegalArgumentException("Dispatch quantity count must match request lines");
        }
        for (int i = 0; i < request.getLines().size(); i++) {
            MaterialRequestLine line = request.getLines().get(i);
            line.dispatch(dispatchedQuantities.get(i));
            inventory.dispatch(request.getSourceStore(), line.getItem(), line.getDispatchedQuantity());
        }
        request.markInTransit();
        requests.save(request);
        notifyRequester(request, "Material request dispatched");
        return dispatches.save(new Dispatch(request, dispatchedBy, collectorName, collectorEmployeeId));
    }

    @Transactional
    public Receipt receive(MaterialRequest request, AppUser receivedBy, List<BigDecimal> receivedQuantities) {
        if (receivedQuantities.size() != request.getLines().size()) {
            throw new IllegalArgumentException("Received quantity count must match request lines");
        }
        for (int i = 0; i < request.getLines().size(); i++) {
            request.getLines().get(i).receive(receivedQuantities.get(i));
        }
        boolean hasDiscrepancy = request.getLines().stream().anyMatch(MaterialRequestLine::hasReceiptVariance);
        Receipt receipt = receipts.save(new Receipt(request, receivedBy,
                hasDiscrepancy ? ReceiptStatus.DISCREPANCY : ReceiptStatus.CLEAN));
        request.getLines().forEach(line -> applyReceiptLine(request, receipt, line));
        request.markReceiptResult(hasDiscrepancy);
        requests.save(request);
        if (hasDiscrepancy) {
            notifyRequester(request, "Material request received with discrepancy");
        }
        return receipt;
    }

    private void applyReceiptLine(MaterialRequest request, Receipt receipt, MaterialRequestLine line) {
        if (line.getReceivedQuantity().signum() > 0) {
            inventory.completeTransit(request.getSourceStore(), line.getItem(), line.getReceivedQuantity());
            inventory.receive(request.getRequestingStore(), line.getItem(), line.getReceivedQuantity());
        }
        if (line.hasReceiptVariance()) {
            inventory.freezeTransitVariance(request.getSourceStore(), line.getItem(), line.varianceQuantity());
            discrepancies.save(new Discrepancy(receipt, line.getItem(), line.getDispatchedQuantity(), line.getReceivedQuantity()));
        }
    }

    private void notifyRequester(MaterialRequest request, String message) {
        if (request.getRequestingStore().getManager() != null) {
            notifications.notify(request.getRequestingStore().getManager(), NotificationType.MATERIAL_REQUEST, message);
        }
    }
}
