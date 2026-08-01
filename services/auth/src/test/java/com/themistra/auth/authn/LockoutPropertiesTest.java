package com.themistra.auth.authn;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bean Validation tests for {@link LockoutProperties} (L4) — mirrors
 * {@code PasswordPolicyPropertiesTest}'s approach: the JSR-380 {@link Validator} directly, the
 * same mechanism Spring's {@code @ConfigurationProperties} + {@code @Validated} binding delegates
 * to at startup. Plain JUnit, no Spring context.
 */
class LockoutPropertiesTest {

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
    void shouldBeValidWithL4Defaults() {
        LockoutProperties properties = new LockoutProperties(5, 30, 15);

        Set<ConstraintViolation<LockoutProperties>> violations = validator.validate(properties);

        assertThat(violations).isEmpty();
    }

    @Test
    void shouldRejectNonPositiveMaxAttempts() {
        Set<ConstraintViolation<LockoutProperties>> violations =
                validator.validate(new LockoutProperties(0, 30, 15));

        assertThat(violations).isNotEmpty();
    }

    @Test
    void shouldRejectNonPositiveWindowMinutes() {
        Set<ConstraintViolation<LockoutProperties>> violations =
                validator.validate(new LockoutProperties(5, 0, 15));

        assertThat(violations).isNotEmpty();
    }

    @Test
    void shouldRejectNonPositiveBaseLockMinutes() {
        Set<ConstraintViolation<LockoutProperties>> violations =
                validator.validate(new LockoutProperties(5, 30, 0));

        assertThat(violations).isNotEmpty();
    }
}
