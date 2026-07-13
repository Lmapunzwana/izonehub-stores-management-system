package com.izonehub.stores.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Spring Security 6's default CsrfTokenRequestAttributeHandler resolves the
 * CSRF token lazily (a DeferredCsrfToken) so the cost of generating one is
 * only paid if something actually reads it during the request. The problem:
 * that also means the XSRF-TOKEN cookie is never written to the response
 * unless something forces resolution — and nothing did, anywhere in this app.
 * No cookie -> frontend never has a token to send back -> CsrfFilter rejects
 * every POST/PUT/PATCH/DELETE outside the explicitly-ignored auth endpoints.
 *
 * This filter forces that resolution on every request, exactly as documented
 * here: https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html
 */
@Component
public class CsrfCookieFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) {
            csrfToken.getToken(); // forces the deferred supplier to resolve -> writes the XSRF-TOKEN cookie
        }
        filterChain.doFilter(request, response);
    }
}
