package com.themistra.payment.receipt;

import com.themistra.payment.common.ProblemTypes;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** RFC 9457 mapping for the receipt read (FU-T29-1). {@code HIGHEST_PRECEDENCE} for the same
 * reason as {@code InvoiceExceptionHandler}: it must outrank {@code ApiExceptionHandler}'s
 * catch-all. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ReceiptExceptionHandler {

    @ExceptionHandler(InvalidReceiptRequestException.class)
    ProblemDetail onInvalidRequest(InvalidReceiptRequestException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(ProblemTypes.INVALID_RECEIPT_REQUEST);
        problem.setTitle("Invalid receipt request");
        problem.setDetail(e.getMessage());
        return problem;
    }

    /** Same 404 for "doesn't exist" and "isn't yours" (R28/L10), with no detail. */
    @ExceptionHandler(ReceiptNotFoundException.class)
    ProblemDetail onReceiptNotFound(ReceiptNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setType(ProblemTypes.NOT_FOUND);
        problem.setTitle("Receipt not found");
        return problem;
    }
}
