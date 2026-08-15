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

    /**
     * {@code DELETE /api-keys/{keyUuid}} (R35, T26) — the single mapping for both "no such key"
     * and "exists but isn't yours" (no detail that could distinguish them; {@link
     * ApiKeyNotFoundException} itself carries no state to distinguish on).
     */
    @ExceptionHandler(ApiKeyNotFoundException.class)
    ProblemDetail onNotFound(ApiKeyNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setType(ProblemTypes.API_KEY_NOT_FOUND);
        problem.setTitle("API key not found");
        return problem;
    }

    /** {@code POST /api-keys} (R30, T26) — the caller lacks {@code MERCHANT} or confirmed MFA. */
    @ExceptionHandler(ApiKeyNotAuthorizedException.class)
    ProblemDetail onNotAuthorized(ApiKeyNotAuthorizedException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        problem.setType(ProblemTypes.API_KEY_NOT_AUTHORIZED);
        problem.setTitle("Not authorized to perform this action");
        return problem;
    }
}
