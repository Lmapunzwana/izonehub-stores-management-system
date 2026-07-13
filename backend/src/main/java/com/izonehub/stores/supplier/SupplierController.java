package com.izonehub.stores.supplier;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {

    private final SupplierRepository suppliers;

    public SupplierController(SupplierRepository suppliers) {
        this.suppliers = suppliers;
    }

    @GetMapping
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER')")
    public List<Supplier> list() {
        return suppliers.findAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER')")
    public Supplier create(@Valid @RequestBody SupplierRequest req) {
        Supplier supplier = new Supplier(
                req.name(),
                req.category(),
                req.email(),
                req.phone(),
                req.address(),
                req.leadTime() != null ? req.leadTime() : BigDecimal.ZERO,
                req.accuracy() != null ? req.accuracy() : BigDecimal.valueOf(100),
                req.rating() != null ? req.rating() : BigDecimal.ZERO,
                req.status() != null ? req.status() : "Active"
        );
        return suppliers.save(supplier);
    }

    @PutMapping("/{id}")
    @Transactional
    @PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR','CENTRAL_STORE_MANAGER')")
    public Supplier update(@PathVariable UUID id, @Valid @RequestBody SupplierRequest req) {
        Supplier supplier = suppliers.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        supplier.setName(req.name());
        supplier.setCategory(req.category());
        supplier.setEmail(req.email());
        supplier.setPhone(req.phone());
        supplier.setAddress(req.address());
        if (req.leadTime() != null) supplier.setLeadTime(req.leadTime());
        if (req.accuracy() != null) supplier.setAccuracy(req.accuracy());
        if (req.rating() != null) supplier.setRating(req.rating());
        if (req.status() != null) supplier.setStatus(req.status());

        return suppliers.save(supplier);
    }

    public record SupplierRequest(
            @NotBlank String name,
            @NotBlank String category,
            String email,
            String phone,
            String address,
            BigDecimal leadTime,
            BigDecimal accuracy,
            BigDecimal rating,
            String status
    ) {}
}
