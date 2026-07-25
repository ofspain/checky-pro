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
}
