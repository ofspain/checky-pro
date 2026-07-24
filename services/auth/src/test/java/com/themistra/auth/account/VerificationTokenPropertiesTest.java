package com.themistra.auth.account;

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
 * Bean Validation tests for {@link VerificationTokenProperties} (Phase 8/9 finding: TTL bounds
 * needed a test, same as T03's {@code PasswordPolicyPropertiesTest} precedent). Plain JUnit, no
 * Spring context — exercises the JSR-380 {@link Validator} directly, the same mechanism Spring's
 * {@code @ConfigurationProperties} + {@code @Validated} binding delegates to at startup.
 */
class VerificationTokenPropertiesTest {

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
    void shouldBeValidWithinBounds() {
        Set<ConstraintViolation<VerificationTokenProperties>> violations =
                validator.validate(new VerificationTokenProperties(30));

        assertThat(violations).isEmpty();
    }

    @Test
    void shouldRejectZeroTtl() {
        Set<ConstraintViolation<VerificationTokenProperties>> violations =
                validator.validate(new VerificationTokenProperties(0));

        assertThat(violations).isNotEmpty();
    }

    @Test
    void shouldRejectNegativeTtl() {
        Set<ConstraintViolation<VerificationTokenProperties>> violations =
                validator.validate(new VerificationTokenProperties(-5));

        assertThat(violations).isNotEmpty();
    }

    @Test
    void shouldRejectTtlAboveOneYear() {
        Set<ConstraintViolation<VerificationTokenProperties>> violations =
                validator.validate(new VerificationTokenProperties(525_601));

        assertThat(violations).isNotEmpty();
    }

    @Test
    void shouldAllowTtlAtOneYearBoundary() {
        Set<ConstraintViolation<VerificationTokenProperties>> violations =
                validator.validate(new VerificationTokenProperties(525_600));

        assertThat(violations).isEmpty();
    }
}
