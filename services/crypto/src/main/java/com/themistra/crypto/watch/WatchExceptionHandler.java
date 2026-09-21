package com.themistra.crypto.watch;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps this module's domain exceptions to RFC 9457 responses (agents.md). Mirrors {@code
 * services/auth}'s {@code ApiKeyExceptionHandler} shape (this service's own first {@code
 * @RestControllerAdvice}). {@code @Order(HIGHEST_PRECEDENCE)} is load-bearing (Phase 3 Finding 9) - a
 * domain-specific advice needs this to reliably outrank any global fallback handler. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WatchExceptionHandler {

    @ExceptionHandler(WatchNotFoundException.class)
    ProblemDetail onNotFound(WatchNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("Watch not found");
        return problem;
    }

    @ExceptionHandler(InvalidWatchRequestException.class)
    ProblemDetail onInvalidRequest(InvalidWatchRequestException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Invalid watch registration request");
        problem.setDetail(e.getMessage());
        return problem;
    }
}
