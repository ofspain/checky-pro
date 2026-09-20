package com.themistra.crypto.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC5/AC6 (frozen brief), L12, Phase 9 Finding 3, Phase 11 Gaps 2/9/10 — see class Javadoc on
 * {@link ScreeningProperties}.
 */
class ScreeningPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(ScreeningProperties.class)
    static class TestConfig {
    }

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    @Test
    void bindsTheLocalProfileShape_disabledWithNoBaseUrl() {
        contextRunner.withPropertyValues(
                        "themistra.crypto.screening.enabled=false",
                        "themistra.crypto.screening.connect-timeout-seconds=5",
                        "themistra.crypto.screening.read-timeout-seconds=5",
                        "themistra.crypto.screening.retry-max-attempts=2")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ScreeningProperties props = context.getBean(ScreeningProperties.class);
                    assertThat(props.enabled()).isFalse();
                    assertThat(props.baseUrl()).isNull();
                });
    }

    @Test
    void bindsWhenEnabledWithBaseUrlAndApiKey() {
        contextRunner.withPropertyValues(
                        "themistra.crypto.screening.enabled=true",
                        "themistra.crypto.screening.base-url=https://fake-screening-vendor.example",
                        "themistra.crypto.screening.connect-timeout-seconds=5",
                        "themistra.crypto.screening.read-timeout-seconds=5",
                        "themistra.crypto.screening.retry-max-attempts=2",
                        "themistra.crypto.screening.api-key-secret-name=fake-secret")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void failsWhenEnabledTrueWithoutBaseUrl() {
        contextRunner.withPropertyValues(
                        "themistra.crypto.screening.enabled=true",
                        "themistra.crypto.screening.api-key-secret-name=fake-secret")
                .run(context -> assertThat(context.getStartupFailure())
                        .rootCause().isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("base-url"));
    }

    @Test
    void failsWhenEnabledTrueWithoutApiKeySecretName() {
        contextRunner.withPropertyValues(
                        "themistra.crypto.screening.enabled=true",
                        "themistra.crypto.screening.base-url=https://fake-screening-vendor.example")
                .run(context -> assertThat(context.getStartupFailure())
                        .rootCause().isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("api-key-secret-name"));
    }

    @Test
    void failsWhenBaseUrlSetButNotEnabled() {
        // Phase 9 Finding: the reverse direction - a forgotten `enabled=true` must not silently
        // no-op screening.
        contextRunner.withPropertyValues(
                        "themistra.crypto.screening.enabled=false",
                        "themistra.crypto.screening.base-url=https://fake-screening-vendor.example",
                        "themistra.crypto.screening.api-key-secret-name=fake-secret")
                .run(context -> assertThat(context.getStartupFailure())
                        .rootCause().isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("base-url").hasMessageContaining("enabled=false"));
    }

    @Test
    void failsWhenBaseUrlSetAndEnabledOmittedEntirely() {
        // enabled omitted entirely binds the primitive default (false) - same reverse-direction
        // guard must still catch it.
        contextRunner.withPropertyValues(
                        "themistra.crypto.screening.base-url=https://fake-screening-vendor.example",
                        "themistra.crypto.screening.api-key-secret-name=fake-secret")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWhenApiKeySecretNameSetButNotEnabled() {
        // Phase 11 Gap 9: the reverse guard originally only checked base-url - widened to also
        // catch api-key-secret-name configured alone with enabled left false.
        contextRunner.withPropertyValues(
                        "themistra.crypto.screening.enabled=false",
                        "themistra.crypto.screening.api-key-secret-name=fake-secret")
                .run(context -> assertThat(context.getStartupFailure())
                        .rootCause().isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("api-key-secret-name").hasMessageContaining("enabled=false"));
    }

    @Test
    void failsWhenConnectTimeoutIsNonPositive() {
        contextRunner.withPropertyValues(
                        "themistra.crypto.screening.enabled=false",
                        "themistra.crypto.screening.connect-timeout-seconds=0",
                        "themistra.crypto.screening.read-timeout-seconds=5",
                        "themistra.crypto.screening.retry-max-attempts=2")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWhenReadTimeoutIsNegative() {
        contextRunner.withPropertyValues(
                        "themistra.crypto.screening.enabled=false",
                        "themistra.crypto.screening.connect-timeout-seconds=5",
                        "themistra.crypto.screening.read-timeout-seconds=-1",
                        "themistra.crypto.screening.retry-max-attempts=2")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWhenRetryMaxAttemptsIsNegative() {
        contextRunner.withPropertyValues(
                        "themistra.crypto.screening.enabled=false",
                        "themistra.crypto.screening.connect-timeout-seconds=5",
                        "themistra.crypto.screening.read-timeout-seconds=5",
                        "themistra.crypto.screening.retry-max-attempts=-1")
                .run(context -> assertThat(context).hasFailed());
    }
}
