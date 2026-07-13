package com.izonehub.stores.receipt;

import com.izonehub.stores.audit.AuditLogService;
import com.izonehub.stores.item.Item;
import com.izonehub.stores.item.ItemRepository;
import com.izonehub.stores.store.Store;
import com.izonehub.stores.store.StoreRepository;
import com.izonehub.stores.user.AppUser;
import com.izonehub.stores.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/expected-receipts")
public class ExpectedReceiptController {

    private final ExpectedReceiptRepository receipts;
    private final StoreRepository stores;
    private final ItemRepository items;
    private final UserRepository users;
    private final GrnCommandService grnCommandService;
    private final AuditLogService auditLog;

    public ExpectedReceiptController(ExpectedReceiptRepository receipts, StoreRepository stores, ItemRepository items,
                                     UserRepository users, GrnCommandService grnCommandService,
                                     AuditLogService auditLog) {
        this.receipts          = receipts;
        this.stores            = stores;
        this.items             = items;
        this.users             = users;
        this.grnCommandService = grnCommandService;
        this.auditLog          = auditLog;
    }

    @GetMapping
    public Page<ExpectedReceipt> list(@RequestParam(defaultValue = "0")  int page,
                                      @RequestParam(defaultValue = "20") int size,
                                      @RequestParam(required = false)    String status,
                                      @RequestParam(required = false)    UUID itemId) {
        var all = receipts.findAll().stream()
                .filter(r -> status == null || r.getStatus().name().equalsIgnoreCase(status))
                .filter(r -> itemId == null || r.getLines().stream().anyMatch(l -> l.getItem().getId().equals(itemId)))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .toList();
        int total = all.size(), from = Math.min(page * size, total), to = Math.min(from + size, total);
        return new PageImpl<>(all.subList(from, to), PageRequest.of(page, size), total);
    }

    @GetMapping("/{id}")
    public ExpectedReceipt get(@PathVariable UUID id) {
        return receipts.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER')")
    public ExpectedReceipt create(@Valid @RequestBody CreateRequest req, @AuthenticationPrincipal String email) {
        AppUser creator = users.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        Store store = stores.findById(req.storeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Store not found"));
        if (req.lines() == null || req.lines().isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one line is required");

        ExpectedReceipt receipt = new ExpectedReceipt(store, req.supplierName(), req.expectedDate(), creator);
        for (LineRequest l : req.lines()) {
            Item item = items.findById(l.itemId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item not found: " + l.itemId()));
            receipt.addLine(new ExpectedReceiptLine(item, l.expectedQuantity()));
        }
        ExpectedReceipt saved = receipts.save(receipt);
        auditLog.record("EXPECTED_RECEIPT", saved.getId().toString(), "CREATED",
                creator.getEmail() + " created expected receipt from supplier '" + req.supplierName()
                        + "' for store '" + store.getName() + "' — " + req.lines().size() + " line(s)",
                creator.getEmail());
        return saved;
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER')")
    public ExpectedReceipt updateStatus(@PathVariable UUID id, @RequestBody UpdateStatusRequest req,
                                        @AuthenticationPrincipal String email) {
        ExpectedReceipt receipt = receipts.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        
        ExpectedReceiptStatus newStatus = ExpectedReceiptStatus.valueOf(req.status());
        
        // Procurement cannot mark as received
        if (newStatus == ExpectedReceiptStatus.COMPLETED || newStatus == ExpectedReceiptStatus.PARTIALLY_RECEIVED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only warehouse staff can mark goods as received");
        }
        
        receipt.setStatus(newStatus);
        ExpectedReceipt saved = receipts.save(receipt);
        
        auditLog.record("EXPECTED_RECEIPT", saved.getId().toString(), "STATUS_UPDATED",
                "Status updated to " + newStatus + " by " + email, email);
                
        return saved;
    }

    /**
     * Confirm goods received. The body is optional per-line overrides; any
     * line not specified there defaults to "received in full, good condition"
     * — matching the current single-click "Confirm GRN" button on the frontend.
     * Supplying overrides lets a future UI report partial/damaged/short
     * receipts without any backend changes.
     */
    @PostMapping("/{id}/confirm")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER')")
    public GoodsReceivedNote confirm(@PathVariable UUID id,
                                     @RequestBody(required = false) ConfirmRequest req,
                                     @AuthenticationPrincipal String email) {
        AppUser receivedBy = users.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        ExpectedReceipt receipt = receipts.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        List<ConfirmLine> overrides = (req == null || req.lines() == null) ? List.of() : req.lines();
        for (ExpectedReceiptLine line : receipt.getLines()) {
            var override = overrides.stream().filter(o -> o.lineId().equals(line.getId())).findFirst();
            if (override.isPresent()) {
                line.recordReceived(override.get().receivedQuantity(),
                        ReceiptLineCondition.valueOf(override.get().condition()));
            } else {
                line.recordReceived(line.getExpectedQuantity(), ReceiptLineCondition.GOOD);
            }
        }
        GoodsReceivedNote grn = grnCommandService.confirm(receipt, receivedBy);
        String storeName = grn.getStore().getName();
        auditLog.record("GRN", grn.getId().toString(), "CONFIRMED",
                receivedBy.getEmail() + " confirmed GRN for supplier '" + receipt.getSupplierName()
                        + "' at store '" + storeName + "'",
                receivedBy.getEmail());
        return grn;
    }

    @GetMapping("/{id}/grn")
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER')")
    public org.springframework.http.ResponseEntity<byte[]> downloadGrn(@PathVariable UUID id, com.izonehub.stores.reporting.GrnNoteService grnService, GoodsReceivedNoteRepository grnRepo) {
        GoodsReceivedNote grn = grnRepo.findByExpectedReceiptId(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "GRN not found for this receipt"));
        byte[] pdf = grnService.generate(grn);
        return org.springframework.http.ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"GRN-" + grn.getReferenceNumber() + ".pdf\"")
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    public record LineRequest(@NotNull UUID itemId, @NotNull BigDecimal expectedQuantity) {}

    public record CreateRequest(
            @NotNull UUID storeId,
            @NotBlank String supplierName,
            @NotNull LocalDate expectedDate,
            List<LineRequest> lines) {}

    public record ConfirmLine(@NotNull UUID lineId, @NotNull BigDecimal receivedQuantity, @NotBlank String condition) {}

    public record ConfirmRequest(List<ConfirmLine> lines) {}
    
    public record UpdateStatusRequest(@NotBlank String status) {}
}
