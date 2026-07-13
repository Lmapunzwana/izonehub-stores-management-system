package com.izonehub.stores.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public final class AuthDtos {

    private AuthDtos() {}

    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank         String password) {}

    /** Tokens live in HttpOnly cookies — only flags returned in body. */
    public record LoginResponse(boolean forcePasswordChange) {}

    /** Current user identity — returned by GET /api/auth/me */
    public record MeResponse(String email, java.util.List<String> roles, boolean forcePasswordChange,
                             java.util.UUID assignedStoreId, String assignedStoreName) {}

    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank String newPassword) {}

    public record ResetPasswordRequest(
            @NotBlank String token,
            @NotBlank String newPassword) {}
}
