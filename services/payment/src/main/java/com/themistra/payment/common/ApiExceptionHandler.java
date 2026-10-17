package com.themistra.payment.common;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * Framework-level RFC 9457 mapping (T06 Phase 7, agents.md Security rule) — this service's first
 * global error handler, added alongside its first controller ({@code InvoiceController}).
 * Domain exceptions are mapped by their own module's advice (e.g. {@code
 * InvoiceExceptionHandler}) when that module's API lands; this class must never import from a
 * feature module. Mirrors {@code crypto-service}'s own {@code ApiExceptionHandler}, with one
 * deliberate departure (Phase 9, Kimi Phase 8 finding 5): every {@code ProblemDetail} here also
 * sets {@code type("about:blank")}, matching {@code ResourceServerConfig}'s own manual 401/403
 * handlers and this service's own standing rule (agents.md: a stable {@code type} catalogue) —
 * {@code crypto-service}'s own version omits it.
 *
 * <p>{@code @Order(LOWEST_PRECEDENCE)} is load-bearing, not decorative — see {@code
 * InvoiceExceptionHandler}'s own {@code @Order(HIGHEST_PRECEDENCE)} Javadoc for why a domain-
 * specific advice needs to reliably outrank this class's catch-all {@code Exception.class}
 * handler.</p>
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    record Violation(String field, String message) {
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail onValidationFailure(MethodArgumentNotValidException e) {
        List<Violation> violations = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> new Violation(fe.getField(), fe.getDefaultMessage()))
                .toList();
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(URI.create("about:blank"));
        problem.setTitle("Validation failed");
        problem.setProperty("violations", violations);
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail onConstraintViolation(ConstraintViolationException e) {
        List<Violation> violations = e.getConstraintViolations().stream()
                .map(v -> new Violation(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(URI.create("about:blank"));
        problem.setTitle("Validation failed");
        problem.setProperty("violations", violations);
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail onUnreadableBody(HttpMessageNotReadableException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(URI.create("about:blank"));
        problem.setTitle("Malformed request body");
        return problem;
    }

    /** Phase 10 — a genuinely new discovery, not previously tested anywhere in this codebase
     * (confirmed: {@code auth-service}'s own {@code @PreAuthorize} test explicitly avoided a
     * live MockMvc check for this exact reason, calling it "a bigger infrastructure decision").
     * {@code @PreAuthorize}'s own denial throws {@link AuthorizationDeniedException} - a
     * <b>different</b> exception from the filter-chain-level {@code AccessDeniedException}
     * {@code ResourceServerConfig}'s own manual {@code problemJsonAccessDeniedHandler} handles.
     * Without this, every current {@code @PreAuthorize}-protected endpoint in this service (and,
     * by the same mirrored template, every sibling service using {@code @PreAuthorize}) returns
     * {@code 500}, not {@code 403}, for a role-denied caller - confirmed empirically via
     * {@code InvoiceControllerTest} before this handler existed. */
    @ExceptionHandler(AuthorizationDeniedException.class)
    ProblemDetail onAuthorizationDenied(AuthorizationDeniedException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        problem.setType(URI.create("about:blank"));
        problem.setTitle("Forbidden");
        problem.setDetail("The token does not carry the required access.");
        return problem;
    }

    /** T22 - the first endpoint in this service with a required {@code @RequestParam}
     * (`ExportController`'s `from`/`to`). Without this, a missing required query param falls
     * through to {@link #onUnexpected}, returning 500 for a plain caller input error - a
     * generic, framework-level request-binding failure, same class as {@link
     * MethodArgumentTypeMismatchException} below, not something a feature module's own advice
     * should each have to handle separately. */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    ProblemDetail onMissingParameter(MissingServletRequestParameterException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(URI.create("about:blank"));
        problem.setTitle("Missing request parameter");
        problem.setDetail("'" + e.getParameterName() + "' is required");
        return problem;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail onTypeMismatch(MethodArgumentTypeMismatchException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(URI.create("about:blank"));
        problem.setTitle("Malformed request parameter");
        problem.setDetail("'" + e.getName() + "' is not a valid value for this request");
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail onUnexpected(Exception e) {
        String traceId = currentTraceId();
        log.error("Unhandled exception [trace_id={}]", traceId, e);

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setType(URI.create("about:blank"));
        problem.setTitle("Internal error");
        problem.setDetail("An unexpected error occurred. Reference trace_id when contacting support.");
        problem.setProperty("trace_id", traceId);
        return problem;
    }

    private static String currentTraceId() {
        String traceId = MDC.get("traceId");
        return traceId != null ? traceId : UUID.randomUUID().toString();
    }
}
