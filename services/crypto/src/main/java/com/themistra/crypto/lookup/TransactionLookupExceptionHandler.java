package com.themistra.crypto.lookup;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Detail-free problems for the lookup endpoint's two exceptions. Runs ahead of the catch-all in
 * {@code common.ApiExceptionHandler}, as {@code WatchExceptionHandler} does. Not scoped to the
 * controller by type: nothing may depend on a controller (FU-1). */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TransactionLookupExceptionHandler {

    @ExceptionHandler(TransactionNotFoundException.class)
    public ProblemDetail onNotFound(TransactionNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("Transaction not found");
        return problem;
    }

    @ExceptionHandler(ProvidersUnavailableException.class)
    public ProblemDetail onProvidersUnavailable(ProvidersUnavailableException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
        problem.setTitle("Chain providers unavailable");
        return problem;
    }
}
