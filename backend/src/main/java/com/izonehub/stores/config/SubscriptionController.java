package com.izonehub.stores.config;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import java.util.List;

import com.izonehub.stores.store.StoreRepository;

@RestController
@RequestMapping("/api/subscription")
public class SubscriptionController {

    private final CompanySubscriptionRepository repo;
    private final StoreRepository storeRepo;

    public SubscriptionController(CompanySubscriptionRepository repo, StoreRepository storeRepo) {
        this.repo = repo;
        this.storeRepo = storeRepo;
    }

    private CompanySubscription getOrCreateSubscription() {
        List<CompanySubscription> all = repo.findAll();
        if (all.isEmpty()) {
            return repo.save(new CompanySubscription(3)); // Default slots
        }
        return all.get(0);
    }

    @GetMapping
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR', 'CENTRAL_STORE_MANAGER')")
    public SubscriptionResponse getSubscription() {
        CompanySubscription sub = getOrCreateSubscription();
        long operationalCount = storeRepo.countByActiveTrueAndClosingFalse();
        return new SubscriptionResponse(sub.getAllowedStoreSlots(), operationalCount);
    }

    @PutMapping
    @Transactional
    @PreAuthorize("hasRole('SYSTEM_ADMINISTRATOR')")
    public SubscriptionResponse updateSubscription(@Valid @RequestBody UpdateSubscriptionRequest req) {
        CompanySubscription sub = getOrCreateSubscription();
        long operationalCount = storeRepo.countByActiveTrueAndClosingFalse();
        
        if (req.allowedStoreSlots() < operationalCount) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot decrease allowed slots below the current operational count (" + operationalCount + ").");
        }
        
        sub.setAllowedStoreSlots(req.allowedStoreSlots());
        CompanySubscription saved = repo.save(sub);
        
        return new SubscriptionResponse(saved.getAllowedStoreSlots(), operationalCount);
    }

    public record SubscriptionResponse(int allowedStoreSlots, long operationalCount) {}
    public record UpdateSubscriptionRequest(@Min(1) int allowedStoreSlots) {}
}
