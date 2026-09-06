package com.themistra.crypto.watch;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps watch failures to statuses.
 *
 * <p>Each kind maps by type rather than by inspecting a message, so a conflicting retry is never
 * confused with a bad request. Responses carry only what the caller needs to fix the call — never
 * internal identifiers, and never anything the caller did not send.
 */
@RestControllerAdvice(assignableTypes = WatchController.class)
public class WatchExceptionHandler {

    @ExceptionHandler(WatchException.Invalid.class)
    ProblemDetail onInvalid(WatchException.Invalid e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(WatchException.Conflict.class)
    ProblemDetail onConflict(WatchException.Conflict e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(WatchException.NotFound.class)
    ProblemDetail onNotFound(WatchException.NotFound e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }
}
