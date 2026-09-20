package com.themistra.auth.common;

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

import java.util.List;
import java.util.UUID;

/**
 * Framework-level RFC 9457 mapping. Domain exceptions are mapped by their own module's advice
 * when that module's API lands — this class must never import from a feature module.
 *
 * Enumeration/leak rules (target-design §13): violation entries carry field + message but never
 * the rejected value (it may be a password); 5xx bodies are opaque, correlated by trace_id only.
 *
 * <p>{@code @Order(LOWEST_PRECEDENCE)} is load-bearing, not decorative: Spring resolves
 * {@code @ExceptionHandler} methods by iterating {@code @RestControllerAdvice} beans in order and
 * using the FIRST bean with any matching handler — it does not search all advice beans for the
 * most specific match (verified against {@code ExceptionHandlerExceptionResolver}'s actual
 * source). Without this annotation, this class's catch-all {@code Exception.class} handler could
 * shadow every domain-specific handler (e.g. {@code SessionExceptionHandler.onNotFound}) whenever
 * this bean happens to sort earlier, silently turning an intended 404/409/etc. into a 500. This
 * class must always be tried last.</p>
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
        return validationProblem(violations);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail onConstraintViolation(ConstraintViolationException e) {
        List<Violation> violations = e.getConstraintViolations().stream()
                .map(v -> new Violation(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return validationProblem(violations);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ProblemDetail onUnreadableBody(HttpMessageNotReadableException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(ProblemTypes.MALFORMED_REQUEST);
        problem.setTitle("Malformed request body");
        return problem;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail onUnexpected(Exception e) {
        String traceId = currentTraceId();
        log.error("Unhandled exception [trace_id={}]", traceId, e);

        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setType(ProblemTypes.INTERNAL_ERROR);
        problem.setTitle("Internal error");
        problem.setDetail("An unexpected error occurred. Reference trace_id when contacting support.");
        problem.setProperty("trace_id", traceId);
        return problem;
    }

    private ProblemDetail validationProblem(List<Violation> violations) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(ProblemTypes.VALIDATION_ERROR);
        problem.setTitle("Validation failed");
        problem.setProperty("violations", violations);
        return problem;
    }

    private static String currentTraceId() {
        String traceId = MDC.get("traceId");
        return traceId != null ? traceId : UUID.randomUUID().toString();
    }
}
