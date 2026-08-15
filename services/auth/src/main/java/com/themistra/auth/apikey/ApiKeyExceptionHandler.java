package com.themistra.auth.apikey;

import com.themistra.auth.common.ProblemTypes;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps this module's domain exceptions to RFC 9457 responses (T25). Mirrors
 * {@code AccountExceptionHandler}'s shape for a single-cause-hidden rejection.
 */
@RestControllerAdvice
public class ApiKeyExceptionHandler {

    /**
     * The single mapping for every {@code POST /api-keys/token} rejection reason (R33, R46) —
     * fixed status, fixed type, fixed title, no {@code detail} that could vary by cause. Never
     * distinguishes revoked from expired from malformed from unknown-prefix from hash-mismatch
     * from a missing/wrong-scheme/blank/over-length {@code Authorization} header.
     */
    @ExceptionHandler(ApiKeyExchangeRejectedException.class)
    ProblemDetail onExchangeRejected(ApiKeyExchangeRejectedException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        problem.setType(ProblemTypes.API_KEY_EXCHANGE_REJECTED);
        problem.setTitle("API key is invalid or revoked");
        return problem;
    }
}
