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

    private final InventoryCommandService inventoryCommandService;
    private final com.izonehub.stores.audit.AuditLogService auditLog;

    public InventoryQueryController(InventoryRepository inventoryRepo, ExpectedReceiptRepository expectedReceipts,
                                    ItemRepository items, StoreRepository stores, UserRepository users,
                                    InventoryCommandService inventoryCommandService, com.izonehub.stores.audit.AuditLogService auditLog) {
        this.inventoryRepo = inventoryRepo;
        this.expectedReceipts = expectedReceipts;
        this.items = items;
        this.stores = stores;
        this.users = users;
        this.inventoryCommandService = inventoryCommandService;
        this.auditLog = auditLog;
    }

    @GetMapping("/site-inventory")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER','SITE_STORE_MANAGER')")
    public List<SiteInventoryRow> getSiteInventory(@RequestParam(required = false) UUID storeId,
                                                   @AuthenticationPrincipal String email) {
        AppUser user = users.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        boolean isSiteManager = user.getRoles().contains(com.izonehub.stores.user.Role.SITE_STORE_MANAGER)
                                && !user.getRoles().contains(com.izonehub.stores.user.Role.SYSTEM_ADMINISTRATOR)
                                && !user.getRoles().contains(com.izonehub.stores.user.Role.CENTRAL_STORE_MANAGER);
        
        List<Store> allowedStores = isSiteManager ? stores.findStoresForUser(user.getId()) : null;
        List<UUID> allowedIds = (allowedStores != null && !allowedStores.isEmpty()) 
                ? allowedStores.stream().map(Store::getId).toList() 
                : null;
        
        List<StoreInventory> list = inventoryRepo.findAllEager().stream()
                .filter(inv -> {
                    if (storeId != null) {
                        return inv.getStore().getId().equals(storeId);
                    }
                    if (allowedIds != null && !allowedIds.isEmpty()) {
                        return allowedIds.contains(inv.getStore().getId());
                    }
                    return true;
                })
                .toList();

        return list.stream().map(inv -> new SiteInventoryRow(
                inv.getId(),
                inv.getStore().getId(),
                inv.getStore().getName(),
                inv.getItem().getId(),
                inv.getItem().getCode(),
                inv.getItem().getName(),
                inv.getItem().getCategory() != null ? inv.getItem().getCategory().name() : null,
                inv.getItem().getUnitOfMeasure(),
                inv.getQuantityOnHand(),
                inv.getQuantityReserved(),
                inv.getQuantityInTransit(),
                inv.getQuantityFrozen(),
                inv.getQuantityDamaged(),
                inv.getQuantityConsumed(),
                inv.getQuantityAvailable(),
                inv.getLastUpdated()
        )).toList();
    }

    @PostMapping("/{inventoryId}/freeze")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER','SITE_STORE_MANAGER')")
    @org.springframework.transaction.annotation.Transactional
    public StoreInventory freezeInventory(@PathVariable UUID inventoryId,
                                          @RequestBody FreezeRequest req,
                                          @AuthenticationPrincipal String email) {
        AppUser user = users.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        StoreInventory inv = inventoryRepo.findById(inventoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory row not found"));
        
        BigDecimal qty = (req.quantity() != null && req.quantity().compareTo(BigDecimal.ZERO) > 0) ? req.quantity() : inv.getQuantityOnHand();
        if (req.freeze()) {
            inventoryCommandService.freezeGrnVariance(inv.getStore(), inv.getItem(), qty);
        } else {
            inventoryCommandService.releaseFrozen(inv.getStore(), inv.getItem(), qty, true);
        }
        auditLog.record("INVENTORY", inv.getId().toString(), req.freeze() ? "FROZEN" : "UNFROZEN",
                (req.freeze() ? "Froze " : "Unfroze ") + qty + " of " + inv.getItem().getName() + " at store " + inv.getStore().getName(),
                email);
        return inventoryRepo.findById(inventoryId).orElse(inv);
    }

    @PostMapping("/{inventoryId}/adjust")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER','SITE_STORE_MANAGER')")
    @org.springframework.transaction.annotation.Transactional
    public StoreInventory adjustInventory(@PathVariable UUID inventoryId,
                                         @RequestBody AdjustRequest req,
                                         @AuthenticationPrincipal String email) {
        AppUser user = users.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        StoreInventory inv = inventoryRepo.findById(inventoryId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory row not found"));
        
        inventoryCommandService.adjustTo(inv.getStore(), inv.getItem(), req.newQuantity());
        auditLog.record("INVENTORY", inv.getId().toString(), "ADJUSTED",
                "Adjusted physical count of " + inv.getItem().getName() + " to " + req.newQuantity() + " at store " + inv.getStore().getName() + (req.reason() != null ? " — " + req.reason() : ""),
                email);
        return inventoryRepo.findById(inventoryId).orElse(inv);
    }

    public record FreezeRequest(Boolean freeze, BigDecimal quantity) {}
    public record AdjustRequest(BigDecimal newQuantity, String reason) {}

    public record SiteInventoryRow(
            UUID id,
            UUID storeId,
            String storeName,
            UUID itemId,
            String itemCode,
            String itemName,
            String category,
            String unitOfMeasure,
            BigDecimal onHand,
            BigDecimal reserved,
            BigDecimal inTransit,
            BigDecimal frozen,
            BigDecimal damaged,
            BigDecimal consumed,
            BigDecimal available,
            java.time.Instant lastUpdated
    ) {}

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
