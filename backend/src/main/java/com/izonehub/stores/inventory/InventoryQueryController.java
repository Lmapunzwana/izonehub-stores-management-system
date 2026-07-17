package com.izonehub.stores.inventory;

import com.izonehub.stores.item.Item;
import com.izonehub.stores.item.ItemRepository;
import com.izonehub.stores.receipt.ExpectedReceipt;
import com.izonehub.stores.receipt.ExpectedReceiptLine;
import com.izonehub.stores.receipt.ExpectedReceiptRepository;
import com.izonehub.stores.receipt.ExpectedReceiptStatus;
import com.izonehub.stores.store.Store;
import com.izonehub.stores.store.StoreRepository;
import com.izonehub.stores.user.AppUser;
import com.izonehub.stores.user.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/inventory")
public class InventoryQueryController {

    private final InventoryRepository inventoryRepo;
    private final ExpectedReceiptRepository expectedReceipts;
    private final ItemRepository items;
    private final StoreRepository stores;
    private final UserRepository users;

    public InventoryQueryController(InventoryRepository inventoryRepo, ExpectedReceiptRepository expectedReceipts,
                                    ItemRepository items, StoreRepository stores, UserRepository users) {
        this.inventoryRepo = inventoryRepo;
        this.expectedReceipts = expectedReceipts;
        this.items = items;
        this.stores = stores;
        this.users = users;
    }

    @GetMapping("/items/{itemId}/stock")
    public ItemStockResponse getStock(@PathVariable UUID itemId, @AuthenticationPrincipal String email) {
        AppUser user = users.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (user.getAssignedStore() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User has no assigned store");
        }
        
        Item item = items.findById(itemId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Store store = user.getAssignedStore();

        StoreInventory inv = inventoryRepo.findByStoreAndItem(store, item).orElse(null);
        BigDecimal currentStock = inv != null ? inv.getQuantityOnHand() : BigDecimal.ZERO;
        BigDecimal reservedStock = inv != null ? inv.getQuantityReserved() : BigDecimal.ZERO;
        BigDecimal availableStock = currentStock.subtract(reservedStock);

        List<ExpectedReceipt> pendingReceipts = expectedReceipts.findAll().stream()
                .filter(r -> r.getStore().getId().equals(store.getId()))
                .filter(r -> r.getStatus() == ExpectedReceiptStatus.AWAITING_GRN || r.getStatus() == ExpectedReceiptStatus.DELAYED || r.getStatus() == ExpectedReceiptStatus.IN_TRANSIT || r.getStatus() == ExpectedReceiptStatus.SUBMITTED || r.getStatus() == ExpectedReceiptStatus.SUPPLIER_CONFIRMED)
                .toList();

        List<IncomingDelivery> incoming = pendingReceipts.stream()
                .flatMap(r -> r.getLines().stream()
                        .filter(java.util.Objects::nonNull)
                        .filter(l -> l.getItem().getId().equals(itemId))
                        .map(l -> new IncomingDelivery(
                                r.getId(),
                                l.getId(),
                                r.getSupplierName(),
                                l.getExpectedQuantity(),
                                r.getStatus().name(),
                                r.getExpectedDate()
                        )))
                .toList();
                
        BigDecimal totalIncoming = incoming.stream()
                .map(IncomingDelivery::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ItemStockResponse(
                itemId,
                currentStock,
                reservedStock,
                availableStock,
                totalIncoming,
                incoming
        );
    }

    public record IncomingDelivery(UUID receiptId, UUID lineId, String supplier, BigDecimal quantity, String status, LocalDate eta) {}

    public record ItemStockResponse(
            UUID itemId,
            BigDecimal currentStock,
            BigDecimal reservedStock,
            BigDecimal availableStock,
            BigDecimal totalIncoming,
            List<IncomingDelivery> incomingDeliveries
    ) {}
}
