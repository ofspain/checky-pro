package com.themistra.notification.common.config;

import org.junit.jupiter.api.Test;

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
}
