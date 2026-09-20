package com.themistra.auth.account.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import jakarta.validation.ValidatorFactory;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bean-validation rules for {@link RegisterAccountRequest}. Password length/breach content (NIST
 * 800-63B, L2) is enforced by {@link com.themistra.auth.account.PasswordPolicy} (T09), not this
 * layer — {@code passwordBoundaries()}'s length-boundary assertions were removed for that reason;
 * {@link com.themistra.auth.account.PasswordPolicyTest} owns that named test now. This layer only
 * rejects a blank password.
 */
class RegisterAccountRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void openFactory() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeFactory() {
        factory.close();
    }

    private Set<ConstraintViolation<RegisterAccountRequest>> validate(String email, String password) {
        return validator.validate(new RegisterAccountRequest(email, password));
    }

    @Test
    void validRequestPasses() {
        assertThat(validate("merchant@example.com", "a".repeat(12))).isEmpty();
    }

    @Test
    void passwordLengthIsNoLongerBeanValidated() {
        // T09: @Size(min=12,max=128) was removed from password() so PasswordPolicy.validate is
        // the sole length-enforcement point (matches PasswordResetConfirmRequest/ChangePasswordRequest,
        // neither of which ever had a @Size here). A too-short/too-long password passes this
        // layer now - it must be rejected downstream by PasswordPolicy, not here.
        assertThat(validate("m@example.com", "a".repeat(11))).isEmpty();
        assertThat(validate("m@example.com", "a".repeat(129))).isEmpty();
    }

    @Test
    void noCompositionRules_longSimplePassphraseIsAllowed() {
        assertThat(validate("m@example.com", "correct horse battery staple")).isEmpty();
    }

    @Test
    void blankAndMalformedEmailsRejected() {
        assertThat(validate("", "a".repeat(12))).isNotEmpty();
        assertThat(validate("not-an-email", "a".repeat(12))).isNotEmpty();
        assertThat(validate("a".repeat(250) + "@x.io", "a".repeat(12))).isNotEmpty(); // > 254 chars
    }

    @Test
    void blankPasswordRejected() {
        assertThat(validate("m@example.com", "")).isNotEmpty();
    }
}
