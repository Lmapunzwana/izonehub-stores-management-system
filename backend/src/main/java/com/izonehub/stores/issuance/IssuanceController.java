package com.izonehub.stores.issuance;

import com.izonehub.stores.item.Item;
import com.izonehub.stores.item.ItemRepository;
import com.izonehub.stores.project.Project;
import com.izonehub.stores.project.ProjectRepository;
import com.izonehub.stores.store.Store;
import com.izonehub.stores.store.StoreRepository;
import com.izonehub.stores.user.AppUser;
import com.izonehub.stores.user.UserRepository;
import com.izonehub.stores.audit.AuditLogService;
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
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/material-issue-vouchers")
public class IssuanceController {

    private final MaterialIssueVoucherRepository mivs;
    private final StoreRepository stores;
    private final ItemRepository items;
    private final UserRepository users;
    private final IssuanceCommandService issuance;
    private final ReturnCommandService returns;
    private final AuditLogService auditLog;
    private final ProjectRepository projects;

    public IssuanceController(MaterialIssueVoucherRepository mivs, StoreRepository stores, ItemRepository items,
                              UserRepository users, IssuanceCommandService issuance, ReturnCommandService returns,
                              AuditLogService auditLog, ProjectRepository projects) {
        this.mivs = mivs;
        this.stores = stores;
        this.items = items;
        this.users = users;
        this.issuance = issuance;
        this.returns = returns;
        this.auditLog = auditLog;
        this.projects = projects;
    }

    @GetMapping
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER','SITE_STORE_MANAGER')")
    public Page<MaterialIssueVoucher> list(@RequestParam(defaultValue = "0")  int page,
                                           @RequestParam(defaultValue = "20") int size,
                                           @RequestParam(required = false)    UUID projectId) {
        var all = mivs.findAll().stream()
                .filter(m -> projectId == null || m.getProject().getId().equals(projectId))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .toList();
        all.forEach(this::resolveLazy);
        int total = all.size(), from = Math.min(page * size, total), to = Math.min(from + size, total);
        return new PageImpl<>(all.subList(from, to), PageRequest.of(page, size), total);
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER','SITE_STORE_MANAGER')")
    public MaterialIssueVoucher get(@PathVariable UUID id) {
        MaterialIssueVoucher miv = mivs.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        resolveLazy(miv);
        return miv;
    }

    /**
     * Forces the lazy associations this screen actually needs to load while the
     * transaction (and Hibernate session) is still open. Without this, open-in-view=false
     * means Jackson would hit unresolved proxies during serialization and either throw
     * LazyInitializationException or (with the Hibernate6 Jackson module) silently null them out.
     */
    private void resolveLazy(MaterialIssueVoucher miv) {
        miv.getProject().getName();
        miv.getStore().getName();
        miv.getIssuedBy().getFullName();
        miv.getLines().forEach(l -> l.getItem().getName());
    }

    /**
     * Issue materials against a project. This is how consumption actually
     * gets charged to a project code — without it stock can move between
     * stores but is never recorded as consumed.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER','SITE_STORE_MANAGER')")
    public MaterialIssueVoucher issue(@Valid @RequestBody IssueRequest req, @AuthenticationPrincipal String email) {
        AppUser issuedBy = users.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        Store store = stores.findById(req.storeId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Store not found"));
        
        if (!store.isActive() || store.isClosing()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot issue items from a closed or closing store");
        }
        
        if (req.lines() == null || req.lines().isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one line is required");

        Project project = projects.findById(req.projectId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project not found"));
        MaterialIssueVoucher miv = new MaterialIssueVoucher(issuance.nextReference(), store, project, issuedBy);
        for (LineRequest l : req.lines()) {
            Item item = items.findById(l.itemId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item not found: " + l.itemId()));
            miv.addLine(new MivLine(item, l.issuedQuantity()));
        }
        MaterialIssueVoucher confirmed = issuance.confirm(miv);
        auditLog.record("MATERIAL_ISSUE_VOUCHER", confirmed.getId().toString(), "CREATED",
                "Issued by " + issuedBy.getEmail() + " from store '" + store.getName() + "' for project " + project.getCode(),
                issuedBy.getEmail());
        return confirmed;
    }

    /** Record a return of previously-issued material (serviceable stock goes back on the shelf). */
    @PostMapping("/{id}/returns")
    @Transactional
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER','SITE_STORE_MANAGER')")
    public StockReturn recordReturn(@PathVariable UUID id, @Valid @RequestBody ReturnRequest req,
                                    @AuthenticationPrincipal String email) {
        AppUser returnedBy = users.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        MaterialIssueVoucher miv = mivs.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (req.lines() == null || req.lines().isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one return line is required");

        StockReturn stockReturn = new StockReturn(miv, miv.getStore(), returnedBy);
        for (ReturnLineRequest l : req.lines()) {
            Item item = items.findById(l.itemId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Item not found: " + l.itemId()));
            stockReturn.addLine(new StockReturnLine(item, l.quantity(), ReturnCondition.valueOf(l.condition())));
        }
        StockReturn result = returns.createPendingReturn(miv, stockReturn);
        result.getStore().getName();
        result.getLines().forEach(l -> l.getItem().getName());
        
        auditLog.record("STOCK_RETURN", result.getId().toString(), "CREATED",
                "Recorded return to store '" + result.getStore().getName() + "' by " + returnedBy.getEmail(),
                returnedBy.getEmail());
                
        return result;
    }

    public record LineRequest(@NotNull UUID itemId, @NotNull BigDecimal issuedQuantity) {}

    public record IssueRequest(
            @NotNull UUID storeId,
            @NotNull UUID projectId,
            List<LineRequest> lines) {}

    public record ReturnLineRequest(@NotNull UUID itemId, @NotNull BigDecimal quantity, @NotBlank String condition) {}

    public record ReturnRequest(List<ReturnLineRequest> lines) {}
}
