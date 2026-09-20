package com.themistra.crypto.common;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.UUID;

/**
 * Framework-level RFC 9457 mapping (T15 Phase 8, agents.md Security rule) - this service's first
 * global error handler, added alongside its first controller ({@code WatchController}). Domain
 * exceptions are mapped by their own module's advice (e.g. {@code WatchExceptionHandler}) when that
 * module's API lands; this class must never import from a feature module. Mirrors {@code
 * services/auth}'s own {@code ApiExceptionHandler} shape closely, with one addition
 * ({@link #onTypeMismatch}) that class does not have.
 *
 * <p>{@code @Order(LOWEST_PRECEDENCE)} is load-bearing, not decorative - see {@code
 * WatchExceptionHandler}'s own {@code @Order(HIGHEST_PRECEDENCE)} Javadoc for why a domain-specific
 * advice needs to reliably outrank this class's catch-all {@code Exception.class} handler.</p>
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
        problem.setTitle("Validation failed");
        problem.setProperty("violations", violations);
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail onUnreadableBody(HttpMessageNotReadableException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Malformed request body");
        return problem;
    }

    /** T15 Phase 8 Finding 8: e.g. {@code DELETE /internal/v1/watches/not-a-uuid} - never includes
     * the rejected value itself in the response (it may be sensitive in a future endpoint; this
     * service's own no-internal-detail posture is applied uniformly, not decided per-field). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail onTypeMismatch(MethodArgumentTypeMismatchException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Malformed request parameter");
        problem.setDetail("'" + e.getName() + "' is not a valid value for this request");
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail onUnexpected(Exception e) {
        String traceId = currentTraceId();
        log.error("Unhandled exception [trace_id={}]", traceId, e);

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
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
