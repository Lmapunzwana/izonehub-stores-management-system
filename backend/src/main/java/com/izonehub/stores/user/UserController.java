package com.izonehub.stores.user;

import com.izonehub.stores.store.Store;
import com.izonehub.stores.store.StoreRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@PreAuthorize("hasAnyRole('SYSTEM_ADMINISTRATOR', 'CENTRAL_STORE_MANAGER')")
public class UserController {

    private final UserRepository    users;
    private final StoreRepository   stores;
    private final UserCommandService svc;

    public UserController(UserRepository users, StoreRepository stores, UserCommandService svc) {
        this.users  = users;
        this.stores = stores;
        this.svc    = svc;
    }

    @GetMapping
    public Page<AppUser> list(@RequestParam(defaultValue = "0")  int page,
                              @RequestParam(defaultValue = "20") int size,
                              @RequestParam(required = false)    String role) {
        var all = users.findAll().stream()
                .filter(u -> role == null || u.getRoles().stream().anyMatch(r -> r.name().equalsIgnoreCase(role)))
                .toList();
        int total = all.size(), from = Math.min(page * size, total), to = Math.min(from + size, total);
        return new PageImpl<>(all.subList(from, to), PageRequest.of(page, size), total);
    }

    @GetMapping("/{id}")
    public AppUser get(@PathVariable UUID id) {
        return users.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AppUser create(@Valid @RequestBody CreateUserRequest req,
                          @AuthenticationPrincipal String creatorEmail) {
        AppUser creator = users.findByEmail(creatorEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        
        java.util.Set<Role> roles = req.roles().stream().map(Role::valueOf).collect(java.util.stream.Collectors.toSet());
        
        // Prevent CENTRAL_STORE_MANAGER from creating SYSTEM_ADMINISTRATOR
        boolean creatorIsAdmin = creator.getRoles().contains(Role.SYSTEM_ADMINISTRATOR);
        if (!creatorIsAdmin && roles.contains(Role.SYSTEM_ADMINISTRATOR)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only System Administrators can create other System Administrators.");
        }

        Store store = req.assignedStoreId() != null
                ? stores.findById(req.assignedStoreId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Store not found"))
                : null;
        
        return svc.createUser(req.fullName(), req.email(), req.temporaryPassword(),
                roles, store, creator);
    }

    @PostMapping("/{id}/unlock")
    public AppUser unlock(@PathVariable UUID id) {
        AppUser u = users.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        u.unlock();
        return users.save(u);
    }

    @PostMapping("/{id}/deactivate")
    public AppUser deactivate(@PathVariable UUID id) {
        AppUser u = users.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        u.deactivate();
        return users.save(u);
    }

    public record CreateUserRequest(
            @NotBlank String fullName,
            @Email @NotBlank String email,
            @NotBlank String temporaryPassword,
            @NotNull java.util.List<String> roles,
            UUID assignedStoreId) {}
}
