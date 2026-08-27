package com.izonehub.stores.admin;

import com.izonehub.stores.user.AppUser;
import com.izonehub.stores.user.Role;
import com.izonehub.stores.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/reset")
public class SystemResetController {

    private final SystemResetService resetService;
    private final UserRepository users;
    private final PasswordEncoder encoder;

    public SystemResetController(SystemResetService resetService, UserRepository users, PasswordEncoder encoder) {
        this.resetService = resetService;
        this.users = users;
        this.encoder = encoder;
    }

    /**
     * Irreversibly wipes every store, item, project, supplier, material
     * request and downstream record (inventory, GRNs, dispatches, receipts,
     * discrepancies, stock counts/adjustments/returns, batches, notifications,
     * audit logs). Only SYSTEM_ADMINISTRATOR accounts survive. Requires the
     * caller to re-enter their own password as confirmation — there is no undo.
     */
    @PostMapping
    @PreAuthorize("hasRole('SYSTEM_ADMINISTRATOR')")
    public void reset(@Valid @RequestBody ResetRequest req, @AuthenticationPrincipal String email) {
        AppUser caller = users.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        if (!caller.getRoles().contains(Role.SYSTEM_ADMINISTRATOR)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only System Administrators can reset the system.");
        }

        if (!encoder.matches(req.password(), caller.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect password.");
        }

        if (!"RESET".equals(req.confirmationPhrase())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Type RESET to confirm.");
        }

        resetService.resetToCleanSlate(email);
    }

    public record ResetRequest(@NotBlank String password, @NotBlank String confirmationPhrase) {}
}
