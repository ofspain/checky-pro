package com.themistra.auth.mfa;

import com.themistra.auth.common.ProblemTypes;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Direct unit test of the handler method itself — {@code MfaControllerTest} constructs
 * {@link MfaController} directly and never goes through Spring's dispatcher, so
 * {@code @RestControllerAdvice} translation isn't observable there. Mirrors
 * {@code ApiKeyExceptionHandlerTest}'s established shape for this exact kind of
 * single-cause-hidden rejection.
 */
class MfaExceptionHandlerTest {

    private final MfaExceptionHandler handler = new MfaExceptionHandler();

    @Test // R22 - a confirmed enrollment already exists
    void onAlreadyEnrolledReturnsUniform409() {
        ProblemDetail problem = handler.onAlreadyEnrolled(new MfaAlreadyEnrolledException());

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getType()).isEqualTo(ProblemTypes.MFA_ALREADY_ENROLLED);
        assertThat(problem.getTitle()).isEqualTo("MFA is already enrolled");
        assertThat(problem.getDetail()).isNull();
        assertThat(problem.getInstance()).isNull();
        assertThat(problem.getProperties()).isNull();
    }

    @Test // R28/R49 - no confirmed TOTP enrollment exists to disable or regenerate codes for
    void onNotEnrolledReturnsUniform404() {
        ProblemDetail problem = handler.onNotEnrolled(new MfaNotEnrolledException());

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getType()).isEqualTo(ProblemTypes.MFA_NOT_ENROLLED);
        assertThat(problem.getTitle()).isEqualTo("MFA is not enrolled");
        assertThat(problem.getDetail()).isNull();
        assertThat(problem.getInstance()).isNull();
        assertThat(problem.getProperties()).isNull();
    }

    @Test // R23/R28/R29/R49 - the submitted TOTP code was rejected; 401 means "credential
          // rejected," not "not logged in" (Phase 3 Finding 2)
    void onInvalidCodeReturnsUniform401() {
        ProblemDetail problem = handler.onInvalidCode(new InvalidTotpCodeException());

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(problem.getType()).isEqualTo(ProblemTypes.MFA_INVALID_CODE);
        assertThat(problem.getTitle()).isEqualTo("TOTP code is invalid");
        assertThat(problem.getDetail()).isNull();
        assertThat(problem.getInstance()).isNull();
        assertThat(problem.getProperties()).isNull();
    }

    @Test // R28/R49 - the supplied current password does not match. 400, not 403: reuses the
          // EXISTING ProblemTypes.CURRENT_PASSWORD_MISMATCH, so it must also reuse that type's
          // already-established status (AccountExceptionHandler.onCurrentPasswordMismatch) -
          // a stable, published problem type cannot mean 400 in one place and 403 in another.
    void onPasswordMismatchReturnsUniform400() {
        ProblemDetail problem = handler.onPasswordMismatch(new MfaCurrentPasswordMismatchException());

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getType()).isEqualTo(ProblemTypes.CURRENT_PASSWORD_MISMATCH);
        assertThat(problem.getTitle()).isEqualTo("Current password is incorrect");
        assertThat(problem.getDetail()).isNull();
        assertThat(problem.getInstance()).isNull();
        assertThat(problem.getProperties()).isNull();
    }

    @Test // direct, named proof that T19 reused /accounts/me/password's own problem type rather
          // than silently defining a near-duplicate MFA-specific one
    void onPasswordMismatchReusesTheExactSameProblemTypeAsAccountsMePassword() {
        ProblemDetail problem = handler.onPasswordMismatch(new MfaCurrentPasswordMismatchException());

        assertThat(problem.getType()).isSameAs(ProblemTypes.CURRENT_PASSWORD_MISMATCH);
    }

    @Test // every one of the four exception types this task maps constructs with no distinguishing
          // state, so each handler method necessarily produces byte-for-byte identical bodies
          // regardless of which call site constructed the exception
    void everyMappedResponseIsIdenticalRegardlessOfConstructionSite() {
        ProblemDetail firstEnrolled = handler.onAlreadyEnrolled(new MfaAlreadyEnrolledException());
        ProblemDetail secondEnrolled = handler.onAlreadyEnrolled(new MfaAlreadyEnrolledException());
        assertThat(firstEnrolled.getType()).isEqualTo(secondEnrolled.getType());
        assertThat(firstEnrolled.getTitle()).isEqualTo(secondEnrolled.getTitle());

        ProblemDetail firstNotEnrolled = handler.onNotEnrolled(new MfaNotEnrolledException());
        ProblemDetail secondNotEnrolled = handler.onNotEnrolled(new MfaNotEnrolledException());
        assertThat(firstNotEnrolled.getType()).isEqualTo(secondNotEnrolled.getType());
        assertThat(firstNotEnrolled.getTitle()).isEqualTo(secondNotEnrolled.getTitle());

        ProblemDetail firstInvalidCode = handler.onInvalidCode(new InvalidTotpCodeException());
        ProblemDetail secondInvalidCode = handler.onInvalidCode(new InvalidTotpCodeException());
        assertThat(firstInvalidCode.getType()).isEqualTo(secondInvalidCode.getType());
        assertThat(firstInvalidCode.getTitle()).isEqualTo(secondInvalidCode.getTitle());

        ProblemDetail firstMismatch = handler.onPasswordMismatch(new MfaCurrentPasswordMismatchException());
        ProblemDetail secondMismatch = handler.onPasswordMismatch(new MfaCurrentPasswordMismatchException());
        assertThat(firstMismatch.getType()).isEqualTo(secondMismatch.getType());
        assertThat(firstMismatch.getTitle()).isEqualTo(secondMismatch.getTitle());
    }
}
