package com.themistra.auth.cleanup;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bean Validation tests for {@link CleanupProperties} (Kimi Phase 11 Gap 1): without this, a
 * future refactor could drop {@code @Validated} or a bound and silently allow a zero-retention
 * (immediate mass-delete) or missing-cron configuration to reach {@code CleanupJob} unnoticed.
 * Mirrors {@code ApiKeyPropertiesTest}'s established shape (plain JSR-380 {@link Validator},
 * no Spring context, Docker-independent).
 */
class CleanupPropertiesTest {

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
        Set<ConstraintViolation<CleanupProperties>> violations =
                validator.validate(new CleanupProperties("0 2 * * *", 7, 90));

        assertThat(violations).isEmpty();
    }

    @Test
    void shouldRejectBlankCron() {
        Set<ConstraintViolation<CleanupProperties>> violations =
                validator.validate(new CleanupProperties("", 7, 90));

        assertThatSingleViolationIsOn(violations, "cron", NotBlank.class);
    }

    @Test
    void shouldRejectZeroTokenRetentionDays() {
        Set<ConstraintViolation<CleanupProperties>> violations =
                validator.validate(new CleanupProperties("0 2 * * *", 0, 90));

        assertThatSingleViolationIsOn(violations, "tokenRetentionDays", Min.class);
    }

    @Test
    void shouldRejectNegativeFamilyRetentionDays() {
        Set<ConstraintViolation<CleanupProperties>> violations =
                validator.validate(new CleanupProperties("0 2 * * *", 7, -1));

        assertThatSingleViolationIsOn(violations, "familyRetentionDays", Min.class);
    }

    @Test
    void shouldAllowRetentionAtOneDayBoundary() {
        Set<ConstraintViolation<CleanupProperties>> violations =
                validator.validate(new CleanupProperties("0 2 * * *", 1, 1));

        assertThat(violations).isEmpty();
    }

    private static void assertThatSingleViolationIsOn(
            Set<ConstraintViolation<CleanupProperties>> violations,
            String expectedProperty,
            Class<? extends java.lang.annotation.Annotation> expectedAnnotation) {
        assertThat(violations).hasSize(1);
        ConstraintViolation<CleanupProperties> violation = violations.iterator().next();
        assertThat(violation.getPropertyPath().toString()).isEqualTo(expectedProperty);
        assertThat(violation.getConstraintDescriptor().getAnnotation().annotationType())
                .isEqualTo(expectedAnnotation);
    }
}
