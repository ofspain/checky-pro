package com.themistra.auth.account;

import com.themistra.auth.common.ProblemTypes;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps this module's domain exceptions to RFC 9457 responses. {@link DuplicateEmailException}
 * is deliberately NOT mapped here — it is caught locally in {@link AccountController#register}
 * so the public registration endpoint never reveals whether an email is already taken.
 *
 * <p>{@code @Order(HIGHEST_PRECEDENCE)} is load-bearing — see {@code SessionExceptionHandler}'s
 * Javadoc for why a domain-specific advice needs this to reliably outrank
 * {@code ApiExceptionHandler}'s catch-all {@code Exception.class} handler.</p>
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AccountExceptionHandler {

    @ExceptionHandler(AccountNotFoundException.class)
    ProblemDetail onNotFound(AccountNotFoundException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setType(ProblemTypes.NOT_FOUND);
        problem.setTitle("Account not found");
        return problem;
    }

    @ExceptionHandler(InvalidAccountStateException.class)
    ProblemDetail onInvalidState(InvalidAccountStateException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setType(ProblemTypes.INVALID_STATE);
        problem.setTitle("Account does not permit this transition");
        problem.setDetail(e.getMessage());
        return problem;
    }

    /**
     * The single mapping for every verification-token rejection reason (R5) — fixed status,
     * fixed title, no detail that could vary by cause. Never distinguishes "not found" from
     * "expired" from "already used" from "wrong account state".
     */
    @ExceptionHandler(AccountService.VerificationTokenRejectedException.class)
    ProblemDetail onVerificationTokenRejected(AccountService.VerificationTokenRejectedException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(ProblemTypes.INVALID_TOKEN);
        problem.setTitle("Verification token is invalid or expired");
        return problem;
    }

    /** Not enumeration-sensitive (R11) - the caller is already authenticated as this account. */
    @ExceptionHandler(AccountService.CurrentPasswordMismatchException.class)
    ProblemDetail onCurrentPasswordMismatch(AccountService.CurrentPasswordMismatchException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(ProblemTypes.CURRENT_PASSWORD_MISMATCH);
        problem.setTitle("Current password is incorrect");
        return problem;
    }

    @ExceptionHandler(PasswordPolicy.PasswordPolicyViolationException.class)
    ProblemDetail onPasswordPolicyViolation(PasswordPolicy.PasswordPolicyViolationException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(ProblemTypes.VALIDATION_ERROR);
        problem.setTitle("Password does not meet policy requirements");
        problem.setDetail(e.getMessage());
        return problem;
    }
}
