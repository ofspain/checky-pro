package com.themistra.notification.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Plain JUnit, no Spring context - the validation logic itself is a plain constructor check,
 * exercised directly rather than by booting a context for each of the 3 real Kimi Phase 8
 * Finding #8 scenarios (which would need Testcontainers-scale setup for no added value here).
 */
class EmailTransportStartupValidationTest {

    @Test
    void acceptsSesWithoutThrowing() {
        assertThatCode(() -> new EmailTransportStartupValidation(new EmailProperties("no-reply@checky.pro", "ses")))
                .doesNotThrowAnyException();
    }

    @Test
    void acceptsFakeWithoutThrowing() {
        assertThatCode(() -> new EmailTransportStartupValidation(new EmailProperties("no-reply@checky.pro", "fake")))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAnyOtherValueWithAClearMessageNamingTheActualValue() {
        assertThatThrownBy(() -> new EmailTransportStartupValidation(new EmailProperties("no-reply@checky.pro", "sendgrid")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sendgrid")
                .hasMessageContaining("ses")
                .hasMessageContaining("fake");
    }

    @Test
    void rejectsACaseMismatchedValue() {
        assertThatThrownBy(() -> new EmailTransportStartupValidation(new EmailProperties("no-reply@checky.pro", "SES")))
                .isInstanceOf(IllegalStateException.class);
    }

    // --- Kimi Phase 11 Gap #2: proves this runs as a real Spring startup guard, not only as a ---
    // --- plain constructor check - i.e. that Spring actually instantiates it eagerly and that ---
    // --- its own clear failure surfaces instead of a generic NoSuchBeanDefinitionException. ---

    @Configuration
    @EnableConfigurationProperties(EmailProperties.class)
    static class TestConfig {
    }

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class, EmailTransportStartupValidation.class);

    @Test
    void springContextFailsToStartWithAClearMessageWhenTransportIsInvalid() {
        contextRunner.withPropertyValues(
                        "themistra.notification.email.from=no-reply@checky.pro",
                        "themistra.notification.email.transport=sendgrid")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .rootCause()
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining("sendgrid")
                            .hasMessageContaining("ses")
                            .hasMessageContaining("fake");
                });
    }

    @Test
    void springContextStartsNormallyWhenTransportIsValid() {
        contextRunner.withPropertyValues(
                        "themistra.notification.email.from=no-reply@checky.pro",
                        "themistra.notification.email.transport=fake")
                .run(context -> assertThat(context).hasNotFailed());
    }
}
