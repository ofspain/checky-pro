package com.themistra.auth.ratelimit;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Min;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bean Validation tests for {@link RateLimitProperties} (Kimi Phase 11 Gap 1): without this, a
 * future refactor could drop {@code @Validated} or a {@code @Min} bound and let a zero threshold
 * silently produce a zero-capacity bucket that blocks all legitimate traffic on a protected path.
 * Mirrors {@code CleanupPropertiesTest}'s established shape (plain JSR-380 {@link Validator}, no
 * Spring context, Docker-independent).
 */
class RateLimitPropertiesTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void shouldBeValidWithSensibleValues() {
        Set<ConstraintViolation<RateLimitProperties>> violations =
                validator.validate(new RateLimitProperties(10, 5, 30));

        assertThat(violations).isEmpty();
    }

    @Test
    void shouldRejectZeroLoginPerMinute() {
        Set<ConstraintViolation<RateLimitProperties>> violations =
                validator.validate(new RateLimitProperties(0, 5, 30));

        assertThatSingleViolationIsOn(violations, "loginPerMinute");
    }

    @Test
    void shouldRejectNegativePasswordResetPerMinute() {
        Set<ConstraintViolation<RateLimitProperties>> violations =
                validator.validate(new RateLimitProperties(10, -1, 30));

        assertThatSingleViolationIsOn(violations, "passwordResetPerMinute");
    }

    @Test
    void shouldRejectZeroOauthTokenPerMinute() {
        Set<ConstraintViolation<RateLimitProperties>> violations =
                validator.validate(new RateLimitProperties(10, 5, 0));

        assertThatSingleViolationIsOn(violations, "oauthTokenPerMinute");
    }

    @Test
    void shouldAllowThresholdAtOneRequestPerMinuteBoundary() {
        Set<ConstraintViolation<RateLimitProperties>> violations =
                validator.validate(new RateLimitProperties(1, 1, 1));

        assertThat(violations).isEmpty();
    }

    private static void assertThatSingleViolationIsOn(
            Set<ConstraintViolation<RateLimitProperties>> violations, String expectedProperty) {
        assertThat(violations).hasSize(1);
        ConstraintViolation<RateLimitProperties> violation = violations.iterator().next();
        assertThat(violation.getPropertyPath().toString()).isEqualTo(expectedProperty);
        assertThat(violation.getConstraintDescriptor().getAnnotation().annotationType())
                .isEqualTo(Min.class);
    }
}
