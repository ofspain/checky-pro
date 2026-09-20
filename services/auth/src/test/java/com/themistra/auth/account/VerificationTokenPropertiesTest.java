package com.themistra.auth.account;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

        assertThatSingleViolationIsOnTtlMinutesWithAnnotation(violations, Min.class);
    }

    @Test
    void shouldRejectNegativeTtl() {
        Set<ConstraintViolation<VerificationTokenProperties>> violations =
                validator.validate(new VerificationTokenProperties(-5));

        assertThatSingleViolationIsOnTtlMinutesWithAnnotation(violations, Min.class);
    }

    @Test
    void shouldRejectTtlAboveOneYear() {
        Set<ConstraintViolation<VerificationTokenProperties>> violations =
                validator.validate(new VerificationTokenProperties(525_601));

        assertThatSingleViolationIsOnTtlMinutesWithAnnotation(violations, Max.class);
    }

    @Test
    void shouldAllowTtlAtOneYearBoundary() {
        Set<ConstraintViolation<VerificationTokenProperties>> violations =
                validator.validate(new VerificationTokenProperties(525_600));

        assertThat(violations).isEmpty();
    }

    /**
     * Asserts exactly one violation, on the {@code ttlMinutes} property path, raised by the given
     * constraint annotation — resilient to a future property being added to the record (Kimi
     * Phase 11 Gap 6): a regression that made the wrong field invalid, or violated the wrong
     * constraint, would fail this instead of silently satisfying a bare "not empty" check.
     */
    private static void assertThatSingleViolationIsOnTtlMinutesWithAnnotation(
            Set<ConstraintViolation<VerificationTokenProperties>> violations,
            Class<? extends java.lang.annotation.Annotation> expectedAnnotation) {
        assertThat(violations).hasSize(1);
        ConstraintViolation<VerificationTokenProperties> violation = violations.iterator().next();
        assertThat(violation.getPropertyPath().toString()).isEqualTo("ttlMinutes");
        assertThat(violation.getConstraintDescriptor().getAnnotation().annotationType())
                .isEqualTo(expectedAnnotation);
    }
}
