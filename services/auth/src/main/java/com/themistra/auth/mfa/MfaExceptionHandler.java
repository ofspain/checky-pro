package com.themistra.auth.mfa;

import com.themistra.auth.common.ProblemTypes;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps this module's domain exceptions to RFC 9457 responses (T19). Mirrors
 * {@code ApiKeyExceptionHandler}'s shape for a single-cause-hidden rejection.
 *
 * <p>{@code @Order(HIGHEST_PRECEDENCE)} is load-bearing — see {@code SessionExceptionHandler}'s
 * Javadoc for why a domain-specific advice needs this to reliably outrank
 * {@code ApiExceptionHandler}'s catch-all {@code Exception.class} handler.</p>
 *
 * <p>{@link InvalidRecoveryCodeException} and {@link MfaEncryptionException} have no mapping here:
 * no path through this task's four endpoints can throw the former (it belongs solely to
 * {@link MfaService#verifyRecoveryCode}, task 20's own login-flow call site); the latter is a KMS
 * infrastructure failure, not a caller error, and falls through to the global catch-all advice,
 * which already produces a safe, detail-free 5xx (R46).</p>
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MfaExceptionHandler {

    /** {@code POST /accounts/me/mfa/totp} (R22) — a confirmed enrollment already exists. */
    @ExceptionHandler(MfaAlreadyEnrolledException.class)
    ProblemDetail onAlreadyEnrolled(MfaAlreadyEnrolledException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setType(ProblemTypes.MFA_ALREADY_ENROLLED);
        problem.setTitle("MFA is already enrolled");
        return problem;
    }

    /**
     * {@code DELETE /accounts/me/mfa/totp} and {@code POST /accounts/me/mfa/recovery-codes}
     * (R28/R49) — no confirmed TOTP enrollment exists to disable or regenerate codes for.
     */
    @ExceptionHandler(MfaNotEnrolledException.class)
    ProblemDetail onNotEnrolled(MfaNotEnrolledException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setType(ProblemTypes.MFA_NOT_ENROLLED);
        problem.setTitle("MFA is not enrolled");
        return problem;
    }

    /**
     * {@code POST /accounts/me/mfa/totp/confirm}, {@code DELETE /accounts/me/mfa/totp}, and
     * {@code POST /accounts/me/mfa/recovery-codes} (R23/R28/R29/R49) — the submitted TOTP code was
     * rejected. {@code 401} here means "the second credential was rejected," not "not logged in" —
     * every one of these endpoints already requires authentication; the caller presented a second,
     * independent credential (the TOTP code) and it failed, the same "presented credential
     * rejected" semantic {@code ApiKeyExceptionHandler.onExchangeRejected} already uses for a
     * rejected API key.
     */
    @ExceptionHandler(InvalidTotpCodeException.class)
    ProblemDetail onInvalidCode(InvalidTotpCodeException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);
        problem.setType(ProblemTypes.MFA_INVALID_CODE);
        problem.setTitle("TOTP code is invalid");
        return problem;
    }

    /**
     * {@code DELETE /accounts/me/mfa/totp} and {@code POST /accounts/me/mfa/recovery-codes}
     * (R28/R49) — the supplied current password does not match. Reuses the existing
     * {@link ProblemTypes#CURRENT_PASSWORD_MISMATCH} type rather than defining a near-duplicate
     * MFA-specific one: {@code AccountExceptionHandler.onCurrentPasswordMismatch} already
     * establishes this exact type as {@code 400 Bad Request} (not enumeration-sensitive — the
     * caller is already authenticated as this account), for the identical semantic on
     * {@code POST /accounts/me/password}. A stable, published problem type cannot mean 400 in one
     * place and 403 in another, so this handler reuses both the type and its established status.
     */
    @ExceptionHandler(MfaCurrentPasswordMismatchException.class)
    ProblemDetail onPasswordMismatch(MfaCurrentPasswordMismatchException e) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setType(ProblemTypes.CURRENT_PASSWORD_MISMATCH);
        problem.setTitle("Current password is incorrect");
        return problem;
    }
}
