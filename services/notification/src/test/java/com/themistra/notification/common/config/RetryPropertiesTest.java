package com.themistra.notification.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC1 - real property binding via {@link ApplicationContextRunner} (self-review Finding 1), plus
 * the cross-field check (Kimi Phase 3 Finding #5).
 */
class RetryPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(RetryProperties.class)
    static class TestConfig {
    }

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    @Test
    void bindsFromTheRealPrefixAndKeyNames() {
        contextRunner.withPropertyValues(
                        "themistra.notification.retry.max-attempts=5",
                        "themistra.notification.retry.initial-backoff-seconds=30",
                        "themistra.notification.retry.max-backoff-seconds=3600",
                        "themistra.notification.retry.scheduler-interval-seconds=30")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    RetryProperties props = context.getBean(RetryProperties.class);
                    assertThat(props.maxAttempts()).isEqualTo(5);
                    assertThat(props.initialBackoffSeconds()).isEqualTo(30);
                    assertThat(props.maxBackoffSeconds()).isEqualTo(3600);
                    assertThat(props.schedulerIntervalSeconds()).isEqualTo(30);
                });
    }

    @Test
    void failsWhenMaxAttemptsIsNonPositive() {
        contextRunner.withPropertyValues(
                        "themistra.notification.retry.max-attempts=0",
                        "themistra.notification.retry.initial-backoff-seconds=30",
                        "themistra.notification.retry.max-backoff-seconds=3600",
                        "themistra.notification.retry.scheduler-interval-seconds=30")
                .run(context -> assertThat(context).hasFailed());
    }

    /** Phase 8 Finding #1/#2: closes both the {@code short}-column overflow risk and the
     * backoff formula's own bit-shift wraparound at the source - a value this high can never be
     * configured in the first place. */
    @Test
    void failsWhenMaxAttemptsExceedsSixtyTwo() {
        contextRunner.withPropertyValues(
                        "themistra.notification.retry.max-attempts=63",
                        "themistra.notification.retry.initial-backoff-seconds=30",
                        "themistra.notification.retry.max-backoff-seconds=3600",
                        "themistra.notification.retry.scheduler-interval-seconds=30")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void succeedsWhenMaxAttemptsEqualsSixtyTwo() {
        contextRunner.withPropertyValues(
                        "themistra.notification.retry.max-attempts=62",
                        "themistra.notification.retry.initial-backoff-seconds=30",
                        "themistra.notification.retry.max-backoff-seconds=3600",
                        "themistra.notification.retry.scheduler-interval-seconds=30")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void failsWhenInitialBackoffIsNonPositive() {
        contextRunner.withPropertyValues(
                        "themistra.notification.retry.max-attempts=5",
                        "themistra.notification.retry.initial-backoff-seconds=0",
                        "themistra.notification.retry.max-backoff-seconds=3600",
                        "themistra.notification.retry.scheduler-interval-seconds=30")
                .run(context -> assertThat(context).hasFailed());
    }

    /** T14: {@code schedulerIntervalSeconds} is independently validated too - a separate concern
     * (how often {@code RetryScheduler} polls) from the other three fields (the backoff-between-
     * attempts policy), with no cross-field relationship to either of them. */
    @Test
    void failsWhenSchedulerIntervalSecondsIsNonPositive() {
        contextRunner.withPropertyValues(
                        "themistra.notification.retry.max-attempts=5",
                        "themistra.notification.retry.initial-backoff-seconds=30",
                        "themistra.notification.retry.max-backoff-seconds=3600",
                        "themistra.notification.retry.scheduler-interval-seconds=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWhenMaxBackoffIsBelowInitialBackoff() {
        contextRunner.withPropertyValues(
                        "themistra.notification.retry.max-attempts=5",
                        "themistra.notification.retry.initial-backoff-seconds=30",
                        "themistra.notification.retry.max-backoff-seconds=10",
                        "themistra.notification.retry.scheduler-interval-seconds=30")
                .run(context -> assertThat(context.getStartupFailure())
                        .rootCause().isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("max-backoff-seconds")
                        .hasMessageContaining("initial-backoff-seconds"));
    }

    @Test
    void succeedsWhenMaxBackoffEqualsInitialBackoff() {
        // boundary: >= is allowed, not just strictly greater.
        contextRunner.withPropertyValues(
                        "themistra.notification.retry.max-attempts=5",
                        "themistra.notification.retry.initial-backoff-seconds=30",
                        "themistra.notification.retry.max-backoff-seconds=30",
                        "themistra.notification.retry.scheduler-interval-seconds=30")
                .run(context -> assertThat(context).hasNotFailed());
    }
}
