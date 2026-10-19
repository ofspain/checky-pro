package com.themistra.payment.export;

import com.themistra.payment.common.ProblemTypes;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps this module's domain exception to an RFC 9457 response (agents.md). Mirrors
 * {@code InvoiceExceptionHandler}'s exact shape - {@code @Order(HIGHEST_PRECEDENCE)} is load-
 * bearing so this outranks {@code ApiExceptionHandler}'s own catch-all (Phase 3 Kimi Finding 6,
 * accepted at Phase 4: without this, {@code InvalidExportRequestException} would fall through to
 * the generic 500 path, violating R29/AC13). */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ExportExceptionHandler {

    @ExceptionHandler(InvalidExportRequestException.class)
    ProblemDetail onInvalidRequest(InvalidExportRequestException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(ProblemTypes.INVALID_EXPORT_REQUEST);
        problem.setTitle("Invalid export request");
        problem.setDetail(e.getMessage());
        return problem;
    }
}
