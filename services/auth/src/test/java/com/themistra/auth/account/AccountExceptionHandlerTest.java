package com.themistra.auth.account;

import com.themistra.auth.common.ProblemTypes;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Direct unit test of the handler methods themselves — {@code AccountControllerTest} constructs
 * {@link AccountController} directly with a mocked {@link AccountService} and never goes through
 * Spring's dispatcher, so {@code @RestControllerAdvice} exception translation isn't observable at
 * that layer (T06 Phase 5 plan). This is the only place the actual HTTP status/problem-type
 * mapping is verified.
 */
class AccountExceptionHandlerTest {

    private final AccountExceptionHandler handler = new AccountExceptionHandler();

    @Test
    void onVerificationTokenRejectedReturnsUniformBadRequest() {
        ProblemDetail problem = handler.onVerificationTokenRejected(
                new AccountService.VerificationTokenRejectedException());

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getType()).isEqualTo(ProblemTypes.INVALID_TOKEN);
        assertThat(problem.getTitle()).isEqualTo("Verification token is invalid or expired");
        // No variable detail: every rejection reason must produce byte-for-byte the same body,
        // so nothing here may vary by cause (R5).
        assertThat(problem.getDetail()).isNull();
    }

    @Test
    void onVerificationTokenRejectedResponseIsIdenticalRegardlessOfConstructionSite() {
        // Two independently constructed exceptions (standing in for "token not found" vs. "wrong
        // account status" - different call sites in AccountService, same exception type) must
        // produce byte-for-byte identical problem bodies.
        ProblemDetail first = handler.onVerificationTokenRejected(
                new AccountService.VerificationTokenRejectedException());
        ProblemDetail second = handler.onVerificationTokenRejected(
                new AccountService.VerificationTokenRejectedException());

        assertThat(first.getStatus()).isEqualTo(second.getStatus());
        assertThat(first.getType()).isEqualTo(second.getType());
        assertThat(first.getTitle()).isEqualTo(second.getTitle());
        assertThat(first.getDetail()).isEqualTo(second.getDetail());
    }

    @Test
    void onVerificationTokenRejectedResponseIsIdenticalForVerifyEmailAndPasswordResetSurfaces() {
        // T10, AC4/L5: the test above proves uniformity *within* one rejection surface (two
        // different internal reasons, same surface). This proves it *across* surfaces - an
        // exception standing in for AccountService.activateFromVerificationToken's rejection (R5)
        // and one standing in for AccountService.resetPassword's rejection (R15) are literally the
        // same exception class, so onVerificationTokenRejected necessarily produces the same
        // ProblemDetail for both - made explicit here rather than left as an inferred consequence
        // of shared plumbing.
        ProblemDetail verifyEmailRejection = handler.onVerificationTokenRejected(
                new AccountService.VerificationTokenRejectedException());
        ProblemDetail passwordResetRejection = handler.onVerificationTokenRejected(
                new AccountService.VerificationTokenRejectedException());

        assertThat(verifyEmailRejection.getStatus()).isEqualTo(passwordResetRejection.getStatus());
        assertThat(verifyEmailRejection.getType()).isEqualTo(passwordResetRejection.getType());
        assertThat(verifyEmailRejection.getTitle()).isEqualTo(passwordResetRejection.getTitle());
        assertThat(verifyEmailRejection.getDetail()).isEqualTo(passwordResetRejection.getDetail());
        // Kimi Phase 8 Finding 6: guard against a future handler change leaking an account/token
        // identifier via instance or extension properties instead of detail.
        assertThat(verifyEmailRejection.getInstance()).isNull();
        assertThat(verifyEmailRejection.getProperties()).isNull();
        assertThat(passwordResetRejection.getInstance()).isNull();
        assertThat(passwordResetRejection.getProperties()).isNull();
    }

    @Test
    void onCurrentPasswordMismatchReturns400WithCurrentPasswordMismatchType() {
        ProblemDetail problem = handler.onCurrentPasswordMismatch(
                new AccountService.CurrentPasswordMismatchException());

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getType()).isEqualTo(ProblemTypes.CURRENT_PASSWORD_MISMATCH);
        assertThat(problem.getTitle()).isEqualTo("Current password is incorrect");
        assertThat(problem.getDetail()).isNull();
    }

    @Test
    void onCurrentPasswordMismatchResponseIsIdenticalRegardlessOfConstructionSite() {
        ProblemDetail first = handler.onCurrentPasswordMismatch(
                new AccountService.CurrentPasswordMismatchException());
        ProblemDetail second = handler.onCurrentPasswordMismatch(
                new AccountService.CurrentPasswordMismatchException());

        assertThat(first.getStatus()).isEqualTo(second.getStatus());
        assertThat(first.getType()).isEqualTo(second.getType());
        assertThat(first.getTitle()).isEqualTo(second.getTitle());
        assertThat(first.getDetail()).isEqualTo(second.getDetail());
    }

    @Test
    void onPasswordPolicyViolationReturns400WithValidationErrorTypeAndDetail() {
        ProblemDetail problem = handler.onPasswordPolicyViolation(
                new PasswordPolicy.PasswordPolicyViolationException("Password must be between 12 and 128 characters"));

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getType()).isEqualTo(ProblemTypes.VALIDATION_ERROR);
        assertThat(problem.getTitle()).isEqualTo("Password does not meet policy requirements");
        assertThat(problem.getDetail()).isEqualTo("Password must be between 12 and 128 characters");
    }
}
