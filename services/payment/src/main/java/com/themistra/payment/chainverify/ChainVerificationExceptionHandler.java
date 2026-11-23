package com.themistra.payment.chainverify;

import com.themistra.payment.common.ProblemTypes;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** RFC 9457 mapping for the public receipt read. {@code HIGHEST_PRECEDENCE} for the same reason as
 * {@code ReceiptExceptionHandler}: it must outrank {@code ApiExceptionHandler}'s catch-all. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ChainVerificationExceptionHandler {

    @ExceptionHandler(ChainVerificationNotFoundException.class)
    ProblemDetail onNotFound(ChainVerificationNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setType(ProblemTypes.NOT_FOUND);
        problem.setTitle("Verification not found");
        return problem;
    }
}
