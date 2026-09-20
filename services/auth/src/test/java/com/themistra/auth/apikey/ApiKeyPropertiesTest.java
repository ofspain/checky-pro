package com.themistra.auth.apikey;

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
 * Bean Validation tests for {@link ApiKeyProperties} (Kimi Phase 11 Gap 2): the Phase 9 gate
 * lowered {@code tokenTtlMinutes}'s upper bound from 525,600 to 1,440 specifically to guard
 * against an operator typo minting a long-lived bearer token — untested, that bound could be
 * silently reverted or removed without CI noticing. Mirrors
 * {@code VerificationTokenPropertiesTest}'s established shape.
 */
class ApiKeyPropertiesTest {

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
        Set<ConstraintViolation<ApiKeyProperties>> violations =
                validator.validate(new ApiKeyProperties("ck_live_", 10));

        assertThat(violations).isEmpty();
    }

    @Test
    void shouldRejectZeroTtl() {
        Set<ConstraintViolation<ApiKeyProperties>> violations =
                validator.validate(new ApiKeyProperties("ck_live_", 0));

        assertThatSingleViolationIsOnTokenTtlMinutesWithAnnotation(violations, Min.class);
    }

    @Test
    void shouldRejectNegativeTtl() {
        Set<ConstraintViolation<ApiKeyProperties>> violations =
                validator.validate(new ApiKeyProperties("ck_live_", -5));

        assertThatSingleViolationIsOnTokenTtlMinutesWithAnnotation(violations, Min.class);
    }

    @Test // Phase 9 gate: the tightened bound (24h), not the old one-year bound
    void shouldRejectTtlAboveTwentyFourHours() {
        Set<ConstraintViolation<ApiKeyProperties>> violations =
                validator.validate(new ApiKeyProperties("ck_live_", 1441));

        assertThatSingleViolationIsOnTokenTtlMinutesWithAnnotation(violations, Max.class);
    }

    @Test
    void shouldAllowTtlAtTwentyFourHourBoundary() {
        Set<ConstraintViolation<ApiKeyProperties>> violations =
                validator.validate(new ApiKeyProperties("ck_live_", 1440));

        assertThat(violations).isEmpty();
    }

    @Test
    void shouldRejectBlankPrefix() {
        Set<ConstraintViolation<ApiKeyProperties>> violations =
                validator.validate(new ApiKeyProperties("", 10));

        assertThat(violations).hasSize(1);
        ConstraintViolation<ApiKeyProperties> violation = violations.iterator().next();
        assertThat(violation.getPropertyPath().toString()).isEqualTo("prefix");
    }

    private static void assertThatSingleViolationIsOnTokenTtlMinutesWithAnnotation(
            Set<ConstraintViolation<ApiKeyProperties>> violations,
            Class<? extends java.lang.annotation.Annotation> expectedAnnotation) {
        assertThat(violations).hasSize(1);
        ConstraintViolation<ApiKeyProperties> violation = violations.iterator().next();
        assertThat(violation.getPropertyPath().toString()).isEqualTo("tokenTtlMinutes");
        assertThat(violation.getConstraintDescriptor().getAnnotation().annotationType())
                .isEqualTo(expectedAnnotation);
    }
}
