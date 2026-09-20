package com.themistra.auth.account;

import com.themistra.auth.account.PasswordPolicyProperties.BreachCheck;
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
 * Bean Validation tests for {@link PasswordPolicyProperties} — added in response to the Phase 11
 * test review (Gap 2): Phase 9 tightened {@code minLength}/{@code maxLength} to L2's 12/128 range,
 * added a cross-field {@code minLength <= maxLength} check, and bounded {@code timeoutMs} to fit
 * an {@code int}, but none of it had a test proving those constraints actually fire. Plain JUnit,
 * no Spring context — uses the JSR-380 {@link Validator} directly, the same mechanism Spring's
 * {@code @ConfigurationProperties} + {@code @Validated} binding delegates to at startup.
 */
class PasswordPolicyPropertiesTest {

    private static final String URL_PREFIX = "https://api.pwnedpasswords.com/range/";

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
    void shouldBeValidWithinL2Bounds() {
        PasswordPolicyProperties properties =
                new PasswordPolicyProperties(12, 128, new BreachCheck(true, URL_PREFIX, 3000));

        Set<ConstraintViolation<PasswordPolicyProperties>> violations = validator.validate(properties);

        assertThat(violations).isEmpty();
    }

    @Test
    void shouldRejectMinLengthBelowL2Bound() {
        PasswordPolicyProperties properties =
                new PasswordPolicyProperties(8, 128, new BreachCheck(true, URL_PREFIX, 3000));

        Set<ConstraintViolation<PasswordPolicyProperties>> violations = validator.validate(properties);

        assertThat(violations).isNotEmpty();
    }

    @Test
    void shouldRejectMaxLengthAboveL2Bound() {
        PasswordPolicyProperties properties =
                new PasswordPolicyProperties(12, 200, new BreachCheck(true, URL_PREFIX, 3000));

        Set<ConstraintViolation<PasswordPolicyProperties>> violations = validator.validate(properties);

        assertThat(violations).isNotEmpty();
    }

    @Test
    void shouldRejectMaxLengthBelowL2Bound() {
        PasswordPolicyProperties properties =
                new PasswordPolicyProperties(12, 5, new BreachCheck(true, URL_PREFIX, 3000));

        Set<ConstraintViolation<PasswordPolicyProperties>> violations = validator.validate(properties);

        assertThat(violations).isNotEmpty();
    }

    @Test
    void shouldRejectMinLengthGreaterThanMaxLengthEvenWhenBothIndividuallyValid() {
        // Both 100 and 20 are individually within [12, 128], but minLength > maxLength must still
        // fail via the cross-field @AssertTrue check (Phase 9 fix, Phase 11 Gap 2).
        PasswordPolicyProperties properties =
                new PasswordPolicyProperties(100, 20, new BreachCheck(true, URL_PREFIX, 3000));

        Set<ConstraintViolation<PasswordPolicyProperties>> violations = validator.validate(properties);

        assertThat(violations).isNotEmpty();
    }

    @Test
    void shouldRejectTimeoutMsBeyondIntRange() {
        PasswordPolicyProperties properties = new PasswordPolicyProperties(
                12, 128, new BreachCheck(true, URL_PREFIX, Integer.MAX_VALUE + 1L));

        Set<ConstraintViolation<PasswordPolicyProperties>> violations = validator.validate(properties);

        assertThat(violations).isNotEmpty();
    }

    @Test
    void shouldRejectBlankUrlPrefix() {
        PasswordPolicyProperties properties =
                new PasswordPolicyProperties(12, 128, new BreachCheck(true, "  ", 3000));

        Set<ConstraintViolation<PasswordPolicyProperties>> violations = validator.validate(properties);

        assertThat(violations).isNotEmpty();
    }
}
