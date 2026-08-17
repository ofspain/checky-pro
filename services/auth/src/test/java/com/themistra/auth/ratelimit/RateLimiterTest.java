package com.themistra.auth.ratelimit;

import io.github.bucket4j.ConsumptionProbe;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RateLimiter} — T31, R41. Plain JUnit, no Spring context. Each test uses
 * its own small, distinct threshold via a fresh {@link RateLimitProperties} instance so bucket
 * exhaustion can be proven quickly without waiting out a real refill window (the window itself is
 * a fixed 60 real seconds, not configurable). {@link #exhaustedBucketAllowsARequestAgainAfterItsWindowRefills}
 * proves genuine refill using a high per-minute threshold (60) so the greedy refill rate is one
 * token per second, keeping the real wait to just over a second instead of a full minute.
 */
class RateLimiterTest {

    private static RateLimiter limiterWith(int loginPerMinute, int passwordResetPerMinute, int oauthTokenPerMinute) {
        return new RateLimiter(new RateLimitProperties(loginPerMinute, passwordResetPerMinute, oauthTokenPerMinute));
    }

    @Test
    void tryConsumeLoginAllowsRequestsWithinThreshold() {
        RateLimiter limiter = limiterWith(3, 5, 30);
        String key = "user@example.com";

        assertThat(limiter.tryConsumeLogin(key).isConsumed()).isTrue();
        assertThat(limiter.tryConsumeLogin(key).isConsumed()).isTrue();
        assertThat(limiter.tryConsumeLogin(key).isConsumed()).isTrue();
    }

    @Test
    void tryConsumeLoginRejectsRequestBeyondThreshold() {
        RateLimiter limiter = limiterWith(3, 5, 30);
        String key = "user@example.com";

        limiter.tryConsumeLogin(key);
        limiter.tryConsumeLogin(key);
        limiter.tryConsumeLogin(key);

        assertThat(limiter.tryConsumeLogin(key).isConsumed()).isFalse();
    }

    @Test
    void rejectedProbeReportsAPositiveBoundedWaitTime() {
        RateLimiter limiter = limiterWith(1, 5, 30);
        String key = "user@example.com";
        limiter.tryConsumeLogin(key);

        ConsumptionProbe rejected = limiter.tryConsumeLogin(key);

        assertThat(rejected.isConsumed()).isFalse();
        long waitSeconds = TimeUnit.NANOSECONDS.toSeconds(rejected.getNanosToWaitForRefill());
        assertThat(waitSeconds).isGreaterThan(0).isLessThanOrEqualTo(60);
    }

    @Test
    void differentKeysHaveIndependentLoginBuckets() {
        RateLimiter limiter = limiterWith(1, 5, 30);
        String keyA = "a@example.com";
        String keyB = "b@example.com";

        assertThat(limiter.tryConsumeLogin(keyA).isConsumed()).isTrue();
        assertThat(limiter.tryConsumeLogin(keyA).isConsumed()).isFalse(); // A now exhausted
        assertThat(limiter.tryConsumeLogin(keyB).isConsumed()).isTrue(); // B unaffected by A
    }

    @Test
    void loginPasswordResetAndOauthTokenBucketsAreIndependentOfEachOtherForTheSameKey() {
        RateLimiter limiter = limiterWith(1, 1, 1);
        String sharedKey = UUID.randomUUID().toString();

        assertThat(limiter.tryConsumeLogin(sharedKey).isConsumed()).isTrue();
        // Exhausting the login bucket for this key must not affect the other two bucket types,
        // even though they happen to be consulted with the exact same key value.
        assertThat(limiter.tryConsumePasswordReset(sharedKey).isConsumed()).isTrue();
        assertThat(limiter.tryConsumeOauthToken(sharedKey).isConsumed()).isTrue();
    }

    @Test
    void tryConsumePasswordResetRespectsItsOwnConfiguredThreshold() {
        RateLimiter limiter = limiterWith(30, 2, 30);
        String key = "token-hash";

        assertThat(limiter.tryConsumePasswordReset(key).isConsumed()).isTrue();
        assertThat(limiter.tryConsumePasswordReset(key).isConsumed()).isTrue();
        assertThat(limiter.tryConsumePasswordReset(key).isConsumed()).isFalse();
    }

    @Test
    void tryConsumeOauthTokenRespectsItsOwnConfiguredThreshold() {
        RateLimiter limiter = limiterWith(30, 5, 2);
        String key = "refresh-token-hash";

        assertThat(limiter.tryConsumeOauthToken(key).isConsumed()).isTrue();
        assertThat(limiter.tryConsumeOauthToken(key).isConsumed()).isTrue();
        assertThat(limiter.tryConsumeOauthToken(key).isConsumed()).isFalse();
    }

    @Test // AC5 - a throttled key is not a permanent block; it recovers once its bucket refills.
          // Uses a 60/minute threshold so the greedy refill rate is one token per second, keeping
          // the real wait here to just over a second rather than a full 60-second window.
    void exhaustedBucketAllowsARequestAgainAfterItsWindowRefills() throws InterruptedException {
        RateLimiter limiter = limiterWith(60, 5, 30);
        String key = "user@example.com";
        for (int i = 0; i < 60; i++) {
            limiter.tryConsumeLogin(key);
        }
        assertThat(limiter.tryConsumeLogin(key).isConsumed()).isFalse();

        Thread.sleep(1100);

        assertThat(limiter.tryConsumeLogin(key).isConsumed()).isTrue();
    }
}
