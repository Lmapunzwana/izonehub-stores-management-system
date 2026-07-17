package com.izonehub.stores.issuance;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

import com.izonehub.stores.item.Item;
import com.izonehub.stores.item.ItemRepository;
import com.izonehub.stores.store.Store;
import com.izonehub.stores.store.StoreRepository;
import com.izonehub.stores.user.AppUser;
import com.izonehub.stores.user.UserRepository;
import com.izonehub.stores.audit.AuditLogService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api/returns")
public class ReturnController {

    private final StockReturnRepository returns;
    private final MaterialIssueVoucherRepository mivs;
    private final ItemRepository items;
    private final UserRepository users;
    private final ReturnCommandService svc;
    private final AuditLogService auditLog;
    private final com.izonehub.stores.inventory.InventoryRepository inventoryRepo;
    private final StoreRepository storeRepo;

    public ReturnController(StockReturnRepository returns, MaterialIssueVoucherRepository mivs,
                            ItemRepository items, UserRepository users,
                            ReturnCommandService svc, AuditLogService auditLog,
                            com.izonehub.stores.inventory.InventoryRepository inventoryRepo,
                            StoreRepository storeRepo) {
        this.returns = returns;
        this.mivs = mivs;
        this.items = items;
        this.users = users;
        this.svc = svc;
        this.auditLog = auditLog;
        this.inventoryRepo = inventoryRepo;
        this.storeRepo = storeRepo;
    }

    @GetMapping
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER','SITE_STORE_MANAGER')")
    public Page<StockReturn> list(@RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "20") int size,
                                  @AuthenticationPrincipal String email) {
        AppUser user = users.findByEmail(email).orElse(null);
        java.util.List<UUID> storeIds = null;
        if (user != null) {
            boolean isSiteManager = user.getRoles().contains(com.izonehub.stores.user.Role.SITE_STORE_MANAGER)
                                    && !user.getRoles().contains(com.izonehub.stores.user.Role.SYSTEM_ADMINISTRATOR)
                                    && !user.getRoles().contains(com.izonehub.stores.user.Role.CENTRAL_STORE_MANAGER);
            if (isSiteManager) {
                java.util.List<Store> managedStores = storeRepo.findByManager_Id(user.getId());
                if (managedStores.isEmpty()) {
                    return new PageImpl<>(List.of(), PageRequest.of(page, size), 0);
                }
                storeIds = managedStores.stream().map(Store::getId).toList();
            }
        }
        final java.util.List<UUID> finalStoreIds = storeIds;

        List<StockReturn> all = returns.findAll().stream()
                .filter(r -> finalStoreIds == null || finalStoreIds.contains(r.getStore().getId()))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .toList();
        
        // Resolve lazy properties for serialization
        all.forEach(this::resolveLazy);
        
        int total = all.size();
        int from = Math.min(page * size, total);
        int to = Math.min(from + size, total);
        return new PageImpl<>(all.subList(from, to), PageRequest.of(page, size), total);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER','SITE_STORE_MANAGER')")
    public StockReturn create(@Valid @RequestBody CreateReturnRequest req, @AuthenticationPrincipal String email) {
        AppUser returnedBy = users.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        MaterialIssueVoucher miv = mivs.findById(req.mivId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "MIV not found"));
                
        boolean isSiteManager = returnedBy.getRoles().contains(com.izonehub.stores.user.Role.SITE_STORE_MANAGER)
                                && !returnedBy.getRoles().contains(com.izonehub.stores.user.Role.SYSTEM_ADMINISTRATOR)
                                && !returnedBy.getRoles().contains(com.izonehub.stores.user.Role.CENTRAL_STORE_MANAGER);
        if (isSiteManager) {
            java.util.List<Store> managedStores = storeRepo.findByManager_Id(returnedBy.getId());
            if (managedStores.stream().noneMatch(s -> s.getId().equals(miv.getProject().getSiteStore().getId()))) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not manage this store");
            }
        }

        StockReturn sr = new StockReturn(miv, miv.getStore(), returnedBy);
        for (ReturnLineRequest l : req.lines()) {
            Item item = items.findById(l.itemId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item not found"));
            
            // Validate return quantity does not exceed what was issued minus already returned
            miv.getLines().stream()
                .filter(java.util.Objects::nonNull)
                .filter(ml -> ml.getItem().getId().equals(item.getId()))
                .findFirst()
                .ifPresentOrElse(ml -> {
                    BigDecimal maxReturnable = ml.getIssuedQuantity().subtract(ml.getReturnedQuantity());
                    if (l.quantity().compareTo(maxReturnable) > 0) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot return more than issued unused quantity for item " + item.getCode());
                    }
                }, () -> {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item " + item.getCode() + " was not issued in this MIV");
                });

            sr.addLine(new StockReturnLine(item, l.quantity(), l.condition()));
        }

        StockReturn saved = svc.createPendingReturn(miv, sr);
        auditLog.record("RETURN", saved.getId().toString(), "INITIATED",
                "Initiated return against MIV '" + miv.getReferenceNumber() + "'", email);
        return saved;
    }

    @PostMapping("/{id}/confirm")
    @ResponseStatus(HttpStatus.OK)
    @Transactional
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER')")
    public StockReturn confirmReturn(@PathVariable UUID id, @Valid @RequestBody ConfirmReturnRequest req, @AuthenticationPrincipal String email) {
        AppUser confirmedBy = users.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        StockReturn sr = returns.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (sr.getStatus() == ReturnStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Already confirmed");
        }

        StockReturn confirmed = svc.confirm(sr, req);
        String mivRef = confirmed.getMiv() != null ? " against MIV '" + confirmed.getMiv().getReferenceNumber() + "'" : "";
        auditLog.record("RETURN", confirmed.getId().toString(), "CONFIRMED",
                "Confirmed return" + mivRef, email);

        Store store = confirmed.getStore();
        if (store.isClosing()) {
            boolean hasInventory = inventoryRepo.findByStoreAndQuantityOnHandGreaterThan(store, BigDecimal.ZERO).size() > 0;
            // pending returns for this store? We can query returns.
            boolean hasPendingReturns = returns.findAll().stream()
                .anyMatch(r -> r.getStore().getId().equals(store.getId()) && r.getStatus() == ReturnStatus.PENDING_CONFIRMATION);
            
            if (!hasInventory && !hasPendingReturns) {
                store.close();
                storeRepo.save(store);
                auditLog.record("STORE", store.getId().toString(), "CLOSED",
                    "Store '" + store.getName() + "' automatically closed after final return.", "SYSTEM");
            }
        }

        return confirmed;
    }

    private void resolveLazy(StockReturn sr) {
        sr.getStore().getName();
        sr.getReturnedBy().getFullName();
        if (sr.getMiv() != null) {
            sr.getMiv().getProject().getName();
        }
        sr.getLines().forEach(l -> { if (l != null) l.getItem().getName(); });
    }

    public record ReturnLineRequest(@NotNull UUID itemId, @NotNull BigDecimal quantity, @NotNull ReturnCondition condition) {}
    public record CreateReturnRequest(@NotNull UUID mivId, @NotNull java.util.List<ReturnLineRequest> lines) {}
    public record ConfirmLineRequest(@NotNull UUID itemId, @NotNull BigDecimal receivedQuantity) {}
    public record ConfirmReturnRequest(@NotNull java.util.List<ConfirmLineRequest> lines) {}
}
