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
                        "themistra.notification.retry.max-backoff-seconds=3600")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    RetryProperties props = context.getBean(RetryProperties.class);
                    assertThat(props.maxAttempts()).isEqualTo(5);
                    assertThat(props.initialBackoffSeconds()).isEqualTo(30);
                    assertThat(props.maxBackoffSeconds()).isEqualTo(3600);
                });
    }

    @Test
    void failsWhenMaxAttemptsIsNonPositive() {
        contextRunner.withPropertyValues(
                        "themistra.notification.retry.max-attempts=0",
                        "themistra.notification.retry.initial-backoff-seconds=30",
                        "themistra.notification.retry.max-backoff-seconds=3600")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWhenInitialBackoffIsNonPositive() {
        contextRunner.withPropertyValues(
                        "themistra.notification.retry.max-attempts=5",
                        "themistra.notification.retry.initial-backoff-seconds=0",
                        "themistra.notification.retry.max-backoff-seconds=3600")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWhenMaxBackoffIsBelowInitialBackoff() {
        contextRunner.withPropertyValues(
                        "themistra.notification.retry.max-attempts=5",
                        "themistra.notification.retry.initial-backoff-seconds=30",
                        "themistra.notification.retry.max-backoff-seconds=10")
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
                        "themistra.notification.retry.max-backoff-seconds=30")
                .run(context -> assertThat(context).hasNotFailed());
    }
}
