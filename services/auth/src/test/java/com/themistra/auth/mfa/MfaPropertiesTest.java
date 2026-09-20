package com.themistra.auth.mfa;

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
 * Bean Validation tests for {@link MfaProperties} (L14, ADR-0003) — mirrors
 * {@code LockoutPropertiesTest}'s approach: the JSR-380 {@link Validator} directly, the same
 * mechanism Spring's {@code @ConfigurationProperties} + {@code @Validated} binding delegates to
 * at startup. Plain JUnit, no Spring context. {@code seedKekArn} is deliberately unannotated (a
 * blank value is legal in the local profile — see {@link MfaSeedEncryption}'s own guard) so it is
 * not exercised here.
 */
class MfaPropertiesTest {

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
    void shouldBeValidWithAnIssuerAndBlankArn() {
        MfaProperties properties = new MfaProperties("Themistra", "");

        Set<ConstraintViolation<MfaProperties>> violations = validator.validate(properties);

        assertThat(violations).isEmpty();
    }

    @Test
    void shouldRejectBlankIssuerName() {
        assertThat(validator.validate(new MfaProperties("", ""))).isNotEmpty();
        assertThat(validator.validate(new MfaProperties("   ", ""))).isNotEmpty();
    }

    @Test
    void shouldRejectNullIssuerName() {
        assertThat(validator.validate(new MfaProperties(null, ""))).isNotEmpty();
    }
}
