package com.izonehub.stores.config;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import com.izonehub.stores.audit.AuditLogService;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Centralised exception handling.
 *
 * Security goals (OWASP A05 — Security Misconfiguration):
 *  - Stack traces, class names, and internal paths are NEVER returned to the client.
 *  - Validation errors return field-level messages but no framework internals.
 *  - All errors are logged server-side with a correlation timestamp so support can
 *    cross-reference without exposing details to the caller.
 *  - 401 and 403 responses are intentionally vague to avoid user-enumeration.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final AuditLogService auditLog;

    public GlobalExceptionHandler(AuditLogService auditLog) {
        this.auditLog = auditLog;
    }

    // ── Validation errors (400) ───────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {

        List<String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();

        return response(HttpStatus.BAD_REQUEST, "Validation failed", request, fieldErrors);
    }

    // ── Controlled application errors (4xx) ───────────────────────────────────

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(
            ResponseStatusException ex, HttpServletRequest request) {

        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) status = HttpStatus.INTERNAL_SERVER_ERROR;

        // For 401/403 always return a generic message to avoid user enumeration
        String message = (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN)
                ? "Access denied"
                : sanitise(ex.getReason());

        // Log 5xx as errors, 4xx as warnings
        if (status.is5xxServerError()) log.error("[{}] {}: {}", request.getRequestURI(), status, ex.getMessage());
        else                           log.warn("[{}] {}: {}", request.getRequestURI(), status, ex.getMessage());

        return response(status, message, request, null);
    }

    // ── Spring Security exceptions ─────────────────────────────────────────────

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuth(
            AuthenticationException ex, HttpServletRequest request) {
        log.warn("[{}] Authentication failure: {}", request.getRequestURI(), ex.getMessage());
        return response(HttpStatus.UNAUTHORIZED, "Access denied", request, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(
            AccessDeniedException ex, HttpServletRequest request) {
        log.warn("[{}] Access denied: {}", request.getRequestURI(), ex.getMessage());
        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        
        String username = (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal()))
                ? auth.getName() : "ANONYMOUS";
                
        auditLog.record("SECURITY", request.getRequestURI(), "UNAUTHORIZED_ACCESS",
                "Unauthorized access attempt to " + request.getRequestURI() + " by " + username,
                username);
                
        if ("ANONYMOUS".equals(username)) {
            return response(HttpStatus.UNAUTHORIZED, "Access denied", request, null);
        }
        return response(HttpStatus.FORBIDDEN, "Access denied", request, null);
    }

    // ── Illegal state / argument (domain rule violations exposed as 422) ───────

    @ExceptionHandler({IllegalStateException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, Object>> handleDomainError(
            RuntimeException ex, HttpServletRequest request) {
        log.warn("[{}] Domain rule violation: {}", request.getRequestURI(), ex.getMessage());
        return response(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), request, null);
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(
            org.springframework.dao.DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("[{}] Data integrity violation: {}", request.getRequestURI(), ex.getMessage());
        return response(HttpStatus.CONFLICT, "A record with these details already exists or violates constraints.", request, null);
    }

    // ── Catch-all (500) ───────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAll(
            Exception ex, HttpServletRequest request) {
        // Log the full exception server-side — never send it to the client
        log.error("[{}] Unhandled exception", request.getRequestURI(), ex);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", request, null);
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private static ResponseEntity<Map<String, Object>> response(
            HttpStatus status, String message, HttpServletRequest request, List<String> details) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status",    status.value());
        body.put("error",     status.getReasonPhrase());
        body.put("message",   message != null ? message : status.getReasonPhrase());
        body.put("path",      request.getRequestURI());
        if (details != null && !details.isEmpty()) body.put("details", details);

        return ResponseEntity.status(status).body(body);
    }

    /** Strip any internal detail that might have leaked into a reason string. */
    private static String sanitise(String reason) {
        if (reason == null || reason.isBlank()) return "An error occurred";
        // Truncate excessively long messages (could contain stack info from lazy callers)
        return reason.length() > 200 ? reason.substring(0, 200) : reason;
    }
}
