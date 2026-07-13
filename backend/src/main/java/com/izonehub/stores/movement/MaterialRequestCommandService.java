package com.izonehub.stores.movement;

import com.izonehub.stores.audit.AuditLogService;
import com.izonehub.stores.inventory.InventoryCommandService;
import com.izonehub.stores.inventory.InventoryRepository;
import com.izonehub.stores.inventory.StoreInventory;
import com.izonehub.stores.notification.NotificationService;
import com.izonehub.stores.notification.NotificationType;
import com.izonehub.stores.user.AppUser;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class MaterialRequestCommandService {

    private final MaterialRequestRepository requests;
    private final DispatchRepository        dispatches;
    private final ReceiptRepository         receipts;
    private final DiscrepancyRepository     discrepancies;
    private final InventoryCommandService   inventory;
    private final InventoryRepository       inventoryRepo;
    private final NotificationService       notifications;
    private final AuditLogService           auditLog;

    public MaterialRequestCommandService(MaterialRequestRepository requests,
                                         DispatchRepository dispatches,
                                         ReceiptRepository receipts,
                                         DiscrepancyRepository discrepancies,
                                         InventoryCommandService inventory,
                                         InventoryRepository inventoryRepo,
                                         NotificationService notifications,
                                         AuditLogService auditLog) {
        this.requests      = requests;
        this.dispatches    = dispatches;
        this.receipts      = receipts;
        this.discrepancies = discrepancies;
        this.inventory     = inventory;
        this.inventoryRepo = inventoryRepo;
        this.notifications = notifications;
        this.auditLog      = auditLog;
    }

    // ── Submit ────────────────────────────────────────────────────────────────

    @Transactional
    public MaterialRequest submit(MaterialRequest request, AppUser submittedBy) {
        String old = request.getStatus().name();
        request.submit();
        MaterialRequest saved = requests.save(request);
        auditLog.record("MATERIAL_REQUEST", saved.getId().toString(), "SUBMITTED",
                "Request submitted by " + submittedBy.getEmail()
                        + " — from " + saved.getRequestingStore().getName()
                        + " requesting from " + saved.getSourceStore().getName(),
                old, saved.getStatus().name(), submittedBy.getEmail());
        return saved;
    }

    // ── Approve ───────────────────────────────────────────────────────────────

    @Transactional
    public MaterialRequest approve(MaterialRequest request, AppUser approver,
                                   List<BigDecimal> approvedQuantities) {
        if (approvedQuantities.size() != request.getLines().size())
            throw new IllegalArgumentException("Approved quantity count must match request lines");

        // ── Stock availability check at approval time ──────────────────────
        List<String> stockErrors = new ArrayList<>();
        for (int i = 0; i < request.getLines().size(); i++) {
            MaterialRequestLine line = request.getLines().get(i);
            BigDecimal approved = approvedQuantities.get(i);
            inventoryRepo.findByStoreAndItem(request.getSourceStore(), line.getItem())
                    .ifPresentOrElse(inv -> {
                        if (inv.getQuantityAvailable().compareTo(approved) < 0) {
                            stockErrors.add(String.format(
                                    "%s: requested %.4f but only %.4f available at %s",
                                    line.getItem().getName(), approved,
                                    inv.getQuantityAvailable(), request.getSourceStore().getName()));
                        }
                    }, () -> stockErrors.add(line.getItem().getName() + ": no stock record in source store"));
        }
        if (!stockErrors.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Insufficient stock: " + String.join("; ", stockErrors));
        }

        String old = request.getStatus().name();
        for (int i = 0; i < request.getLines().size(); i++) {
            MaterialRequestLine line = request.getLines().get(i);
            line.approve(approvedQuantities.get(i));
            inventory.reserve(request.getSourceStore(), line.getItem(), line.getApprovedQuantity());
        }
        request.approve(approver);
        MaterialRequest saved = requests.save(request);
        notifyRequester(saved, "Material request approved by " + approver.getEmail());
        auditLog.record("MATERIAL_REQUEST", saved.getId().toString(), "APPROVED",
                "Approved by " + approver.getEmail()
                        + " (" + saved.getSourceStore().getName() + " → "
                        + saved.getRequestingStore().getName() + ")",
                old, saved.getStatus().name(), approver.getEmail());
        return saved;
    }

    // ── Reject ────────────────────────────────────────────────────────────────

    @Transactional
    public MaterialRequest reject(MaterialRequest request, AppUser approver, String reason) {
        String old = request.getStatus().name();
        request.reject(approver, reason);
        MaterialRequest saved = requests.save(request);
        notifyRequester(saved, "Material request rejected: " + reason);
        auditLog.record("MATERIAL_REQUEST", saved.getId().toString(), "REJECTED",
                "Rejected by " + approver.getEmail() + ". Reason: " + reason,
                old, saved.getStatus().name(), approver.getEmail());
        return saved;
    }

    // ── Dispatch ──────────────────────────────────────────────────────────────

    @Transactional
    public Dispatch dispatch(MaterialRequest request, AppUser dispatchedBy,
                             String collectorName, String collectorEmployeeId,
                             List<BigDecimal> dispatchedQuantities) {
        if (dispatchedQuantities.size() != request.getLines().size())
            throw new IllegalArgumentException("Dispatch quantity count must match request lines");

        String old = request.getStatus().name();
        for (int i = 0; i < request.getLines().size(); i++) {
            MaterialRequestLine line = request.getLines().get(i);
            line.dispatch(dispatchedQuantities.get(i));
            
            BigDecimal approved = line.getApprovedQuantity();
            BigDecimal dispatched = line.getDispatchedQuantity();
            if (dispatched.compareTo(approved) > 0) {
                throw new IllegalStateException("Cannot dispatch more than approved quantity");
            }
            
            inventory.dispatchReserved(request.getSourceStore(), line.getItem(), dispatched);
            if (approved.compareTo(dispatched) > 0) {
                inventory.unreserve(request.getSourceStore(), line.getItem(), approved.subtract(dispatched));
            }
        }
        request.markInTransit();
        requests.save(request);
        notifyRequester(request, "Material dispatched — collected by " + collectorName);
        Dispatch dispatch = dispatches.save(
                new Dispatch(request, dispatchedBy, collectorName, collectorEmployeeId));
        auditLog.record("MATERIAL_REQUEST", request.getId().toString(), "DISPATCHED",
                "Dispatched by " + dispatchedBy.getEmail()
                        + ". Collector: " + collectorName + " (ID: " + collectorEmployeeId + ")",
                old, request.getStatus().name(), dispatchedBy.getEmail());
        return dispatch;
    }

    // ── Receive ───────────────────────────────────────────────────────────────

    @Transactional
    public Receipt receive(MaterialRequest request, AppUser receivedBy,
                           List<BigDecimal> receivedQuantities) {
        if (receivedQuantities.size() != request.getLines().size())
            throw new IllegalArgumentException("Received quantity count must match request lines");

        String old = request.getStatus().name();
        for (int i = 0; i < request.getLines().size(); i++) {
            request.getLines().get(i).receive(receivedQuantities.get(i));
        }
        boolean hasDiscrepancy = request.getLines().stream().anyMatch(MaterialRequestLine::hasReceiptVariance);
        Receipt receipt = receipts.save(new Receipt(request, receivedBy,
                hasDiscrepancy ? ReceiptStatus.DISCREPANCY : ReceiptStatus.CLEAN));
        request.getLines().forEach(line -> applyReceiptLine(request, receipt, line));
        request.markReceiptResult(hasDiscrepancy);
        requests.save(request);
        if (hasDiscrepancy) notifyRequester(request, "Material received with discrepancy");
        auditLog.record("MATERIAL_REQUEST", request.getId().toString(), "RECEIVED",
                "Received by " + receivedBy.getEmail()
                        + (hasDiscrepancy ? " — DISCREPANCY recorded" : " — clean receipt"),
                old, request.getStatus().name(), receivedBy.getEmail());
        return receipt;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void applyReceiptLine(MaterialRequest request, Receipt receipt, MaterialRequestLine line) {
        if (line.getReceivedQuantity().signum() > 0) {
            inventory.completeTransit(request.getSourceStore(), line.getItem(), line.getReceivedQuantity());
            inventory.receive(request.getRequestingStore(), line.getItem(), line.getReceivedQuantity());
        }
        if (line.hasReceiptVariance()) {
            inventory.freezeTransitVariance(request.getSourceStore(), line.getItem(), line.varianceQuantity());
            discrepancies.save(new Discrepancy(receipt, line.getItem(),
                    line.getDispatchedQuantity(), line.getReceivedQuantity()));
        }
    }

    private void notifyRequester(MaterialRequest request, String message) {
        if (request.getRequestingStore().getManager() != null) {
            notifications.notify(request.getRequestingStore().getManager(),
                    NotificationType.MATERIAL_REQUEST, message);
        }
    }
}
