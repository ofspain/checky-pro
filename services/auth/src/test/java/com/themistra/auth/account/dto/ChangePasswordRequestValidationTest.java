package com.themistra.auth.account.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Bean-validation boundaries for the change-password DTO (R11) — Kimi Phase 11 Gap 3. */
class ChangePasswordRequestValidationTest {

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

    @Test
    void validRequestPasses() {
        assertThat(validator.validate(new ChangePasswordRequest("current", "new"))).isEmpty();
    }

    @Test
    void blankCurrentPasswordRejected() {
        assertThat(validator.validate(new ChangePasswordRequest("", "new"))).isNotEmpty();
    }

    @Test
    void blankNewPasswordRejected() {
        assertThat(validator.validate(new ChangePasswordRequest("current", ""))).isNotEmpty();
    }

    @Test
    void nullFieldsRejected() {
        assertThat(validator.validate(new ChangePasswordRequest(null, null))).isNotEmpty();
    }

    @Test
    void toStringOmitsBothCredentials() {
        var request = new ChangePasswordRequest("super-secret-current", "super-secret-new");

        assertThat(request.toString())
                .doesNotContain("super-secret-current")
                .doesNotContain("super-secret-new");
    }
}
