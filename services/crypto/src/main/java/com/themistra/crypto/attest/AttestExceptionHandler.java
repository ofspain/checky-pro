package com.themistra.crypto.attest;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps this module's domain exceptions to RFC 9457 responses (agents.md) - mirrors {@code
 * watch.WatchExceptionHandler}'s exact shape. {@code @Order(HIGHEST_PRECEDENCE)} is load-bearing (same
 * reasoning as that class): a domain-specific advice needs this to reliably outrank
 * {@code common.ApiExceptionHandler}'s generic fallback. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AttestExceptionHandler {

    @ExceptionHandler(AttestationRefusedException.class)
    ProblemDetail onRefused(AttestationRefusedException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Attestation refused");
        problem.setDetail(e.getMessage());
        return problem;
    }
}
