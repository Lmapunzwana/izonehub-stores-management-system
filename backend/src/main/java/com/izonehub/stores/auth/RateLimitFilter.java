package com.izonehub.stores.auth;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP sliding-window rate limiting on sensitive authentication endpoints.
 *
 * Applies different bucket configurations:
 *   - POST /api/auth/login           → standard login bucket (configurable)
 *   - POST /api/auth/forgot-password → tight bucket (3 attempts / 15 min)
 *   - POST /api/auth/reset-password  → tight bucket (3 attempts / 15 min)
 *
 * Uses Bucket4j's in-process token-bucket algorithm — no external cache required.
 * Each unique client IP gets its own bucket per endpoint group.
 *
 * X-Forwarded-For is only trusted when app.security.trust-proxy=true (i.e.
 * when running behind nginx in K3s). In Docker Compose (trust-proxy=false) the
 * real remote address is always used so that clients cannot spoof IPs to bypass
 * the per-IP limit.
 *
 * OWASP: addresses OTG-AUTHN-003 (Testing for Weak Lock Out Mechanism) and
 *        prevents credential-stuffing and brute-force attacks (CWE-307).
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH          = "/api/auth/login";
    private static final String FORGOT_PATH         = "/api/auth/forgot-password";
    private static final String RESET_PATH          = "/api/auth/reset-password";

    /** Standard login buckets, keyed by resolved client IP. */
    private final ConcurrentHashMap<String, Bucket> loginBuckets  = new ConcurrentHashMap<>();
    /** Tight buckets for password-reset endpoints, keyed by resolved client IP. */
    private final ConcurrentHashMap<String, Bucket> resetBuckets  = new ConcurrentHashMap<>();

    private final int     maxAttempts;
    private final long    windowMinutes;
    private final boolean trustProxy;

    public RateLimitFilter(
            @Value("${app.security.login-rate-limit-attempts}") int maxAttempts,
            @Value("${app.security.login-rate-limit-minutes}")  long windowMinutes,
            @Value("${app.security.trust-proxy:false}")         boolean trustProxy) {
        this.maxAttempts   = maxAttempts;
        this.windowMinutes = windowMinutes;
        this.trustProxy    = trustProxy;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) return true;
        String uri = request.getRequestURI();
        return !LOGIN_PATH.equals(uri) && !FORGOT_PATH.equals(uri) && !RESET_PATH.equals(uri);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String ip  = resolveClientIp(request);
        String uri = request.getRequestURI();

        boolean isResetPath = FORGOT_PATH.equals(uri) || RESET_PATH.equals(uri);
        Bucket bucket = isResetPath
                ? resetBuckets.computeIfAbsent(ip, this::newTightBucket)
                : loginBuckets.computeIfAbsent(ip, this::newLoginBucket);

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            long retryAfterSeconds = isResetPath ? 15 * 60 : windowMinutes * 60;
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.addHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.getWriter().write("{\"error\":\"Too many attempts. Please try again later.\"}");
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /** Standard login bucket: configurable attempts per window. */
    private Bucket newLoginBucket(String ip) {
        Bandwidth limit = Bandwidth.classic(
                maxAttempts,
                Refill.intervally(maxAttempts, Duration.ofMinutes(windowMinutes))
        );
        return Bucket.builder().addLimit(limit).build();
    }

    /** Tight bucket for password-reset flows: 3 attempts per 15 minutes. */
    private Bucket newTightBucket(String ip) {
        Bandwidth limit = Bandwidth.classic(3, Refill.intervally(3, Duration.ofMinutes(15)));
        return Bucket.builder().addLimit(limit).build();
    }

    /**
     * Resolves the client IP.
     *
     * When {@code trustProxy=true} (production K3s behind nginx), the ingress
     * rewrites X-Forwarded-For to the real client IP before it reaches this
     * filter, so we can safely trust the first value.
     *
     * When {@code trustProxy=false} (local Docker Compose, no reverse proxy),
     * X-Forwarded-For is not trusted because any client could inject a fake
     * IP and bypass per-IP rate limiting.
     */
    private String resolveClientIp(HttpServletRequest request) {
        if (trustProxy) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return request.getRemoteAddr();
    }
}
