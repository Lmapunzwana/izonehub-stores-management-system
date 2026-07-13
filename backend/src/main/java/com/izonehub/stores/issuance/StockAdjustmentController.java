package com.izonehub.stores.issuance;

import com.izonehub.stores.inventory.InventoryRepository;
import com.izonehub.stores.inventory.StoreInventory;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/stock-adjustments")
public class StockAdjustmentController {

    private final StockAdjustmentRepository adjustments;
    private final StoreRepository stores;
    private final ItemRepository items;
    private final InventoryRepository inventoryRepo;
    private final UserRepository users;
    private final StockAdjustmentCommandService svc;

    public StockAdjustmentController(StockAdjustmentRepository adjustments, StoreRepository stores, ItemRepository items,
                                     InventoryRepository inventoryRepo, UserRepository users, StockAdjustmentCommandService svc) {
        this.adjustments = adjustments;
        this.stores = stores;
        this.items = items;
        this.inventoryRepo = inventoryRepo;
        this.users = users;
        this.svc = svc;
    }

    @GetMapping
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER','SITE_STORE_MANAGER')")
    public Page<StockAdjustment> list(@RequestParam(defaultValue = "0")  int page,
                                      @RequestParam(defaultValue = "20") int size,
                                      @RequestParam(required = false)    Boolean pendingCountersignature) {
        var all = adjustments.findAll().stream()
                .filter(a -> pendingCountersignature == null
                        || (pendingCountersignature == (a.isRequiresCountersignature() && !a.isCountersigned())))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .toList();
        all.forEach(this::resolveLazy);
        int total = all.size(), from = Math.min(page * size, total), to = Math.min(from + size, total);
        return new PageImpl<>(all.subList(from, to), PageRequest.of(page, size), total);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER','SITE_STORE_MANAGER')")
    public StockAdjustment get(@PathVariable UUID id) {
        StockAdjustment a = adjustments.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        resolveLazy(a);
        return a;
    }

    /** See IssuanceController for why this is necessary with open-in-view=false. */
    private void resolveLazy(StockAdjustment a) {
        a.getStore().getName();
        a.getItem().getName();
        a.getAdjustedBy().getFullName();
        if (a.getCountersignedBy() != null) a.getCountersignedBy().getFullName();
    }

    /** Raise an adjustment directly (e.g. damage write-off or a found item), independent of a stock count. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER','SITE_STORE_MANAGER')")
    public StockAdjustment create(@Valid @RequestBody CreateRequest req, @AuthenticationPrincipal String email) {
        AppUser performedBy = users.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        Store store = stores.findById(req.storeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Store not found"));
        Item item = items.findById(req.itemId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item not found"));
        BigDecimal quantityBefore = inventoryRepo.findByStoreAndItem(store, item)
                .map(StoreInventory::getQuantityOnHand)
                .orElse(BigDecimal.ZERO);

        String reference = "ADJ-" + System.nanoTime();
        StockAdjustment adjustment = new StockAdjustment(reference, store, item, performedBy,
                AdjustmentReasonCode.valueOf(req.reasonCode()), quantityBefore, req.quantityAfter(),
                req.notes(), req.countersignatureThreshold() == null ? BigDecimal.ZERO : req.countersignatureThreshold());
        StockAdjustment result = svc.raise(adjustment, performedBy);
        resolveLazy(result);
        return result;
    }

    @PostMapping("/{id}/countersign")
    @Transactional
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER')")
    public StockAdjustment countersign(@PathVariable UUID id, @AuthenticationPrincipal String email) {
        AppUser countersignedBy = users.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        StockAdjustment adjustment = adjustments.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        StockAdjustment result = svc.countersign(adjustment, countersignedBy);
        resolveLazy(result);
        return result;
    }

    public record CreateRequest(
            @NotNull UUID storeId,
            @NotNull UUID itemId,
            @NotBlank String reasonCode,
            @NotNull BigDecimal quantityAfter,
            String notes,
            BigDecimal countersignatureThreshold) {}
}
