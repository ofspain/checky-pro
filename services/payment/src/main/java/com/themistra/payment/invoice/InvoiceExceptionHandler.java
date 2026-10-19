package com.themistra.payment.invoice;

import com.themistra.payment.common.CryptoServiceUnavailableException;
import com.themistra.payment.common.ProblemTypes;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps this module's domain exceptions to RFC 9457 responses (agents.md). Mirrors {@code
 * crypto-service}'s own {@code WatchExceptionHandler} shape (this service's own first
 * {@code @RestControllerAdvice}, T06). {@code @Order(HIGHEST_PRECEDENCE)} is load-bearing,
 * mirroring that same precedent exactly — a domain-specific advice needs this to reliably
 * outrank any global fallback handler. Each handler sets a real {@link ProblemTypes} constant
 * (originally a uniform {@code "about:blank"} placeholder from Phase 9, Kimi Phase 8 finding 5,
 * replaced with the actual stable catalogue in T23/R29) — {@code WatchExceptionHandler} itself
 * sets neither. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class InvoiceExceptionHandler {

    @ExceptionHandler(InvalidInvoiceRequestException.class)
    ProblemDetail onInvalidRequest(InvalidInvoiceRequestException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(ProblemTypes.INVALID_INVOICE_REQUEST);
        problem.setTitle("Invalid invoice request");
        problem.setDetail(e.getMessage());
        return problem;
    }

    /** A {@code registerWatch}/token-fetch failure is an upstream dependency failure, never the
     * merchant's fault (AC6) - never includes the wrapped cause's own message, which may carry
     * internal detail (agents.md: no internal detail in a 4xx/5xx body). */
    @ExceptionHandler(CryptoServiceUnavailableException.class)
    ProblemDetail onCryptoServiceUnavailable(CryptoServiceUnavailableException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
        problem.setType(ProblemTypes.CRYPTO_SERVICE_UNAVAILABLE);
        problem.setTitle("Crypto Service unavailable");
        problem.setDetail("The invoice could not be created because an upstream dependency is unavailable.");
        return problem;
    }

    /** R28/L10 (T07): the same {@code 404} for "doesn't exist" and "exists but isn't the
     * caller's" - no detail message at all, so the body itself carries no signal distinguishing
     * the two (mirrors {@code auth-service}'s own {@code SessionExceptionHandler}). */
    @ExceptionHandler(InvoiceNotFoundException.class)
    ProblemDetail onInvoiceNotFound(InvoiceNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setType(ProblemTypes.NOT_FOUND);
        problem.setTitle("Invoice not found");
        return problem;
    }
}
