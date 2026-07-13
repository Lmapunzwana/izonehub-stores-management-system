package com.izonehub.stores.auth;

import com.izonehub.stores.user.AppUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Issues and validates HMAC-SHA256-signed JWTs.
 *
 * Two token types:
 *  - access  : short-lived (15 min), carries email + role, stored in HttpOnly cookie
 *  - refresh : long-lived (7 days), carries only email + type claim, stored in a
 *              separate HttpOnly cookie; used by /api/auth/refresh to mint a new
 *              access token without re-login
 *
 * Both tokens are signed with the same key. The "typ" claim distinguishes them so
 * that a stolen refresh token cannot be submitted as an access token (the filter
 * checks the claim before trusting the principal).
 */
@Service
public class JwtService {

    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_TYPE = "typ";
    private static final String TYPE_ACCESS  = "access";
    private static final String TYPE_REFRESH = "refresh";

    private final SecretKey key;
    private final String issuer;
    private final long accessMinutes;
    private final long refreshDays;

    public JwtService(
            @Value("${app.jwt.secret}")              String secret,
            @Value("${app.jwt.issuer}")              String issuer,
            @Value("${app.jwt.access-token-minutes}") long accessMinutes,
            @Value("${app.jwt.refresh-token-days}")   long refreshDays) {
        this.key          = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer       = issuer;
        this.accessMinutes = accessMinutes;
        this.refreshDays  = refreshDays;
    }

    // ── issuance ──────────────────────────────────────────────────────────────

    public String issueAccessToken(AppUser user) {
        Instant now = Instant.now();
        java.util.List<String> roleNames = user.getRoles().stream()
                .map(Enum::name)
                .collect(java.util.stream.Collectors.toList());

        return Jwts.builder()
                .issuer(issuer)
                .subject(user.getEmail())
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .claim(CLAIM_ROLES, roleNames)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessMinutes * 60)))
                .signWith(key)
                .compact();
    }

    public String issueRefreshToken(AppUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(user.getEmail())
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(refreshDays * 86_400)))
                .signWith(key)
                .compact();
    }

    // ── validation ────────────────────────────────────────────────────────────

    /**
     * Parse and validate an access token.
     * Returns empty if the token is invalid, expired, or is not an access token.
     */
    public Optional<Claims> validateAccessToken(String token) {
        return parse(token).filter(c -> TYPE_ACCESS.equals(c.get(CLAIM_TYPE, String.class)));
    }

    /**
     * Parse and validate a refresh token.
     * Returns empty if the token is invalid, expired, or is not a refresh token.
     */
    public Optional<Claims> validateRefreshToken(String token) {
        return parse(token).filter(c -> TYPE_REFRESH.equals(c.get(CLAIM_TYPE, String.class)));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Optional<Claims> parse(String token) {
        try {
            return Optional.of(
                    Jwts.parser()
                        .verifyWith(key)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload()
            );
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
