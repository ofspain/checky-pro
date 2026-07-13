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
 * Bean-validation rules per NIST 800-63B (D-006): the two boundary failures from the reference
 * project's policy code (gap-analysis §3) are covered here as explicit boundary tests.
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
    void passwordBoundaries() {
        assertThat(validate("m@example.com", "a".repeat(11))).isNotEmpty();   // one under minimum
        assertThat(validate("m@example.com", "a".repeat(12))).isEmpty();      // exact minimum
        assertThat(validate("m@example.com", "a".repeat(128))).isEmpty();     // exact maximum
        assertThat(validate("m@example.com", "a".repeat(129))).isNotEmpty();  // one over maximum
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
