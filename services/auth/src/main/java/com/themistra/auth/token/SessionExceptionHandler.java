package com.themistra.auth.token;

import com.themistra.auth.common.ProblemTypes;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps this module's domain exceptions to RFC 9457 responses (T28). Kept in {@code token}, not
 * folded into {@code account.AccountExceptionHandler}, even though the triggering controller
 * ({@code AccountController}) lives in {@code account} — Spring's {@code @RestControllerAdvice}
 * resolution is global and type-matched, not package-scoped (the same mechanism T26's D6 already
 * relied on for a foreign-module exception resolving correctly), so the module that owns the
 * exception also owns its translation.
 *
 * <p>{@code @Order(HIGHEST_PRECEDENCE)} is load-bearing: without an explicit order, this class
 * defaults to the same {@code LOWEST_PRECEDENCE} value as {@code ApiExceptionHandler}'s catch-all
 * {@code Exception.class} handler, and Spring's advice-bean iteration only breaks that tie by
 * incidental registration order — discovered when a real HTTP call showed
 * {@code SessionNotFoundException} falling through to a 500 instead of this class's 404.</p>
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SessionExceptionHandler {

    /**
     * The single mapping for every {@code DELETE /accounts/me/sessions/{familyId}} rejection
     * reason (R37, R46) — fixed status, fixed type, fixed title, no {@code detail} that could
     * distinguish "doesn't exist" from "exists but isn't yours."
     */
    @ExceptionHandler(SessionNotFoundException.class)
    ProblemDetail onNotFound(SessionNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setType(ProblemTypes.SESSION_NOT_FOUND);
        problem.setTitle("Session not found");
        return problem;
    }
}
