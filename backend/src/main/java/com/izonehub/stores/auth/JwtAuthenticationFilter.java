package com.izonehub.stores.auth;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Reads the signed JWT access token from the HttpOnly "access_token" cookie,
 * validates it via JwtService, and populates the Spring Security context.
 *
 * Using a cookie (not Authorization header) means the token is never accessible
 * to JavaScript, eliminating XSS-based token theft. CSRF protection in
 * SecurityConfig compensates for the cookie-based transport.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    static final String ACCESS_COOKIE = "access_token";

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        java.util.Optional<String> tokenOpt = extractCookie(request, ACCESS_COOKIE);
        if (tokenOpt.isEmpty()) {
            if (request.getRequestURI().startsWith("/api/") && !request.getRequestURI().contains("/auth/")) {
                System.out.println("DEBUG JWT: No access_token cookie found for " + request.getRequestURI());
            }
        } else {
            java.util.Optional<Claims> claimsOpt = jwtService.validateAccessToken(tokenOpt.get());
            if (claimsOpt.isEmpty()) {
                System.out.println("DEBUG JWT: validateAccessToken failed for " + request.getRequestURI() + " (token: " + tokenOpt.get().substring(0, 15) + "...)");
            } else {
                populate(claimsOpt.get(), request);
                System.out.println("DEBUG JWT: Successfully authenticated " + claimsOpt.get().getSubject() + " for " + request.getRequestURI());
            }
        }

        chain.doFilter(request, response);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static java.util.Optional<String> extractCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return java.util.Optional.empty();
        return Arrays.stream(request.getCookies())
                .filter(c -> name.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst();
    }

    private static void populate(Claims claims, HttpServletRequest request) {
        // Guard: don't overwrite an already-authenticated context
        if (SecurityContextHolder.getContext().getAuthentication() != null) return;

        String email = claims.getSubject();
        List<?> roles = claims.get("roles", List.class);
        if (email == null || roles == null || roles.isEmpty()) return;

        List<SimpleGrantedAuthority> authorities = roles.stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r.toString()))
                .collect(java.util.stream.Collectors.toList());

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                email,
                null,
                authorities
        );
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
