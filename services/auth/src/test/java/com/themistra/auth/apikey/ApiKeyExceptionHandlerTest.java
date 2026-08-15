package com.themistra.auth.apikey;

import com.themistra.auth.common.ProblemTypes;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Direct unit test of the handler method itself — {@code ApiKeyControllerTest} constructs
 * {@link ApiKeyController} directly and never goes through Spring's dispatcher, so
 * {@code @RestControllerAdvice} translation isn't observable there. Mirrors
 * {@code AccountExceptionHandlerTest}'s established shape for this exact kind of
 * single-cause-hidden rejection.
 */
class ApiKeyExceptionHandlerTest {

    private final ApiKeyExceptionHandler handler = new ApiKeyExceptionHandler();

    @Test // R33, R46 - fixed status/type/title, no variable detail
    void onExchangeRejectedReturnsUniform401() {
        ProblemDetail problem = handler.onExchangeRejected(new ApiKeyExchangeRejectedException());

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(problem.getType()).isEqualTo(ProblemTypes.API_KEY_EXCHANGE_REJECTED);
        assertThat(problem.getTitle()).isEqualTo("API key is invalid or revoked");
        assertThat(problem.getDetail()).isNull();
        // No instance/properties leakage either (mirrors AccountExceptionHandlerTest's Kimi
        // Phase 8 Finding 6 guard) - nothing here may vary by or hint at the rejection cause.
        assertThat(problem.getInstance()).isNull();
        assertThat(problem.getProperties()).isNull();
    }

    @Test // R33 - revoked, expired, malformed, unknown-prefix, and hash-mismatch all construct
          // the identical exception type with no distinguishing state, so the handler necessarily
          // produces byte-for-byte identical bodies regardless of which one triggered it
    void onExchangeRejectedResponseIsIdenticalRegardlessOfConstructionSite() {
        ProblemDetail first = handler.onExchangeRejected(new ApiKeyExchangeRejectedException());
        ProblemDetail second = handler.onExchangeRejected(new ApiKeyExchangeRejectedException());

        assertThat(first.getStatus()).isEqualTo(second.getStatus());
        assertThat(first.getType()).isEqualTo(second.getType());
        assertThat(first.getTitle()).isEqualTo(second.getTitle());
        assertThat(first.getDetail()).isEqualTo(second.getDetail());
    }
}
