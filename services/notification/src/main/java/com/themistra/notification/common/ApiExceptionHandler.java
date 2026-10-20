package com.themistra.notification.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * RFC 9457 error responses for {@code inapp}'s own two controllers (T13, `design.md` §6's own file
 * map) - the first REST surface in this service to need anything beyond the 401/403 handling
 * {@code ResourceServerConfig} (T03) already owns, which this class deliberately does not
 * duplicate.
 *
 * <p>Scoped narrowly (Kimi Phase 3 Finding #9): a malformed JWT {@code sub} claim (Finding #7) and
 * any other unexpected exception. Uses Spring's own built-in {@link ProblemDetail} (RFC 9457-native
 * since Spring 6/Boot 3) rather than hand-rolling a body the way {@code ResourceServerConfig}'s own
 * servlet-level entry-point/denied-handler do - a different Spring subsystem (MVC exception
 * resolution vs. a raw filter-chain handler), same output shape.</p>
 *
 * <p>The generic fallback catches {@code RuntimeException}, not {@code Exception} (Kimi Phase 8
 * Finding #3, self-review Finding #2) - a blanket {@code Exception} handler would take precedence
 * over Spring's own default handling for framework-level, checked {@code ServletException}
 * subtypes (verified directly: {@code NoResourceFoundException}, {@code
 * HttpRequestMethodNotSupportedException}), turning an ordinary 404/405 for an unrelated,
 * mistyped request into a generic 500 instead.</p>
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /**
     * Thrown when a validated JWT's own {@code sub} claim cannot be parsed as the account UUID it
     * is expected to be (Kimi Phase 3 Finding #7). Nested here, not a separate top-level file - this
     * class's own job is exactly "what HTTP shape does each exception map to."
     */
    public static class InvalidSubjectClaimException extends RuntimeException {
        public InvalidSubjectClaimException(String message) {
            super(message);
        }
    }

    @ExceptionHandler(InvalidSubjectClaimException.class)
    public ProblemDetail handleInvalidSubjectClaim(InvalidSubjectClaimException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "The authenticated token's own subject claim is not a valid account identifier.");
    }

    /** A {@code @PreAuthorize} denial (FU-2 port, 2026-10-20). It is a {@code RuntimeException},
     * so without this the fallback below turns it into a 500. No detail about which rule refused. */
    @ExceptionHandler(AuthorizationDeniedException.class)
    public ProblemDetail handleAuthorizationDenied(AuthorizationDeniedException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
        problem.setTitle("Forbidden");
        return problem;
    }

    @ExceptionHandler(RuntimeException.class)
    public ProblemDetail handleUnexpected(RuntimeException e) {
        log.error("Unexpected error handling an in-app API request", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred.");
    }
}
