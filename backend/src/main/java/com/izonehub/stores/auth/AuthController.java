package com.izonehub.stores.auth;

import com.izonehub.stores.user.AppUser;
import com.izonehub.stores.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    static final String ACCESS_COOKIE  = "access_token";
    static final String REFRESH_COOKIE = "refresh_token";

    private final UserRepository       users;
    private final PasswordEncoder      encoder;
    private final JwtService           jwt;
    private final PasswordPolicy       policy;
    private final PasswordResetService resetService;
    private final int                  maxFailed;
    private final boolean              cookieSecure;
    private final String               cookieDomain;
    private final String               cookieSameSite;

    public AuthController(
            UserRepository users,
            PasswordEncoder encoder,
            JwtService jwt,
            PasswordPolicy policy,
            PasswordResetService resetService,
            @Value("${app.security.max-failed-logins}")  int maxFailed,
            @Value("${app.security.cookie-secure}")      boolean cookieSecure,
            @Value("${app.security.cookie-domain:}")     String cookieDomain,
            @Value("${app.security.cookie-same-site}")   String cookieSameSite) {
        this.users          = users;
        this.encoder        = encoder;
        this.jwt            = jwt;
        this.policy         = policy;
        this.resetService   = resetService;
        this.maxFailed      = maxFailed;
        this.cookieSecure   = cookieSecure;
        this.cookieDomain   = cookieDomain;
        this.cookieSameSite = cookieSameSite;
    }

    // ── POST /api/auth/login ──────────────────────────────────────────────────

    @PostMapping("/login")
    public AuthDtos.LoginResponse login(@Valid @RequestBody AuthDtos.LoginRequest req,
                                        HttpServletResponse response) {
        AppUser user = users.findByEmail(req.email().toLowerCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        if (!user.isActive() || user.isLocked())
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);

        if (!encoder.matches(req.password(), user.getPasswordHash())) {
            user.recordFailedLogin(maxFailed);
            users.save(user);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        user.recordSuccessfulLogin();
        users.save(user);

        writeTokenCookie(response, ACCESS_COOKIE,  jwt.issueAccessToken(user),  15 * 60);
        writeTokenCookie(response, REFRESH_COOKIE, jwt.issueRefreshToken(user), 7 * 24 * 60 * 60);

        return new AuthDtos.LoginResponse(user.isForcePasswordChange());
    }

    // ── GET /api/auth/me ─────────────────────────────────────────────────────

    @GetMapping("/me")
    public AuthDtos.MeResponse me(@AuthenticationPrincipal String email) {
        AppUser user = users.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        var store = user.getAssignedStore();
        return new AuthDtos.MeResponse(
                user.getEmail(),
                user.getRoles().stream().map(Enum::name).collect(java.util.stream.Collectors.toList()),
                user.isForcePasswordChange(),
                store != null ? store.getId() : null,
                store != null ? store.getName() : null);
    }

    // ── POST /api/auth/refresh ────────────────────────────────────────────────

    @PostMapping("/refresh")
    public void refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = extractCookie(request, REFRESH_COOKIE).orElse(null);
        if (refreshToken == null) {
            System.out.println("DEBUG REFRESH: REFRESH_COOKIE is missing from request!");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "No refresh cookie");
        }

        var claimsOpt = jwt.validateRefreshToken(refreshToken);
        if (claimsOpt.isEmpty()) {
            System.out.println("DEBUG REFRESH: validateRefreshToken failed for token: " + refreshToken);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }
        var claims = claimsOpt.get();

        AppUser user = users.findByEmail(claims.getSubject()).orElse(null);
        if (user == null) {
            System.out.println("DEBUG REFRESH: User not found: " + claims.getSubject());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found");
        }

        if (!user.isActive() || user.isLocked()) {
            System.out.println("DEBUG REFRESH: User inactive or locked: " + user.getEmail());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User inactive");
        }

        writeTokenCookie(response, ACCESS_COOKIE, jwt.issueAccessToken(user), 15 * 60);
        System.out.println("DEBUG REFRESH: Successfully issued new access token for " + user.getEmail());
    }

    // ── POST /api/auth/logout ─────────────────────────────────────────────────

    @PostMapping("/logout")
    public void logout(HttpServletResponse response) {
        clearCookie(response, ACCESS_COOKIE);
        clearCookie(response, REFRESH_COOKIE);
    }

    // ── POST /api/auth/change-password ────────────────────────────────────────

    @PostMapping("/change-password")
    public void changePassword(@AuthenticationPrincipal String email,
                               @Valid @RequestBody AuthDtos.ChangePasswordRequest req,
                               HttpServletResponse response) {
        AppUser user = users.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        if (!encoder.matches(req.currentPassword(), user.getPasswordHash()))
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Current password is incorrect");

        if (!policy.isValid(req.newPassword()))
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Password must be 8+ characters with a digit and special character");

        user.changePassword(encoder.encode(req.newPassword()));
        users.save(user);

        writeTokenCookie(response, ACCESS_COOKIE,  jwt.issueAccessToken(user),  15 * 60);
        writeTokenCookie(response, REFRESH_COOKIE, jwt.issueRefreshToken(user), 7 * 24 * 60 * 60);
    }

    // ── POST /api/auth/forgot-password ───────────────────────────────────────

    @PostMapping("/forgot-password")
    public Map<String, String> forgotPassword(@RequestBody Map<String, String> body) {
        String email = body.getOrDefault("email", "").toLowerCase().trim();
        // Always return success to prevent user enumeration
        users.findByEmail(email).ifPresent(resetService::initiateReset);
        return Map.of("message", "If that email exists you will receive a reset link");
    }

    // ── POST /api/auth/reset-password ────────────────────────────────────────

    @PostMapping("/reset-password")
    public Map<String, String> resetPassword(@Valid @RequestBody AuthDtos.ResetPasswordRequest req) {
        if (!policy.isValid(req.newPassword()))
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Password must be 8+ characters with a digit and special character");

        resetService.consumeToken(req.token(), encoder.encode(req.newPassword()));
        return Map.of("message", "Password reset successfully");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void writeTokenCookie(HttpServletResponse response, String name, String value, int maxAgeSeconds) {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append("=").append(value).append(";");
        sb.append(" HttpOnly; Path=/; Max-Age=").append(maxAgeSeconds).append(";");
        sb.append(" SameSite=").append(cookieSameSite).append(";");
        if (cookieSecure) sb.append(" Secure;");
        if (cookieDomain != null && !cookieDomain.isBlank()) sb.append(" Domain=").append(cookieDomain).append(";");
        response.addHeader("Set-Cookie", sb.toString());
    }

    private void clearCookie(HttpServletResponse response, String name) {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append("=; HttpOnly; Path=/; Max-Age=0;");
        sb.append(" SameSite=").append(cookieSameSite).append(";");
        if (cookieSecure) sb.append(" Secure;");
        if (cookieDomain != null && !cookieDomain.isBlank()) sb.append(" Domain=").append(cookieDomain).append(";");
        response.addHeader("Set-Cookie", sb.toString());
    }

    private static java.util.Optional<String> extractCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return java.util.Optional.empty();
        return Arrays.stream(request.getCookies())
                .filter(c -> name.equals(c.getName()))
                .map(jakarta.servlet.http.Cookie::getValue)
                .findFirst();
    }
}
