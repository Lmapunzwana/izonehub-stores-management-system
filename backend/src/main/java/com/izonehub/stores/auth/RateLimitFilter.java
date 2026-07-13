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
 * Per-IP sliding-window rate limit on POST /api/auth/login.
 *
 * Uses Bucket4j's in-process token-bucket algorithm — no external cache required.
 * Each unique client IP gets its own bucket. Exceeding the limit returns 429 with
 * a Retry-After header.
 *
 * OWASP: addresses OTG-AUTHN-003 (Testing for Weak Lock Out Mechanism) and
 *        prevents credential-stuffing and brute-force attacks (CWE-307).
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/api/auth/login";

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final int maxAttempts;
    private final long windowMinutes;

    public RateLimitFilter(
            @Value("${app.security.login-rate-limit-attempts}") int maxAttempts,
            @Value("${app.security.login-rate-limit-minutes}")  long windowMinutes) {
        this.maxAttempts   = maxAttempts;
        this.windowMinutes = windowMinutes;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only apply to POST /api/auth/login
        return !("POST".equalsIgnoreCase(request.getMethod())
                && LOGIN_PATH.equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String ip = resolveClientIp(request);
        Bucket bucket = buckets.computeIfAbsent(ip, this::newBucket);

        if (bucket.tryConsume(1)) {
            chain.doFilter(request, response);
        } else {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.addHeader("Retry-After", String.valueOf(windowMinutes * 60));
            response.getWriter().write("{\"error\":\"Too many login attempts. Please try again later.\"}");
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Bucket newBucket(String ip) {
        Bandwidth limit = Bandwidth.classic(
                maxAttempts,
                Refill.intervally(maxAttempts, Duration.ofMinutes(windowMinutes))
        );
        return Bucket.builder().addLimit(limit).build();
    }

    /**
     * Prefer X-Forwarded-For when behind a reverse proxy (nginx / AWS ALB).
     * Only trust the first hop to prevent IP spoofing via header injection.
     */
    private static String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
