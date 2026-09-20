package com.themistra.auth.account.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bean-validation boundaries for the two password-reset DTOs (R12/R14/R15), plus the
 * toString()/wording guards from Phase 3/8 (Findings 5, 7; Independent Finding 5).
 */
class PasswordResetDtoTest {

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
    void passwordResetRequestRejectsBlankAndMalformedEmail() {
        assertThat(validator.validate(new PasswordResetRequest("valid@example.com"))).isEmpty();
        assertThat(validator.validate(new PasswordResetRequest(""))).isNotEmpty();
        assertThat(validator.validate(new PasswordResetRequest("not-an-email"))).isNotEmpty();
    }

    @Test
    void passwordResetConfirmRequestRejectsBlankTokenOrPassword() {
        assertThat(validator.validate(new PasswordResetConfirmRequest("a-token", "a-new-password"))).isEmpty();
        assertThat(validator.validate(new PasswordResetConfirmRequest("", "a-new-password"))).isNotEmpty();
        assertThat(validator.validate(new PasswordResetConfirmRequest("a-token", ""))).isNotEmpty();
    }

    @Test
    void passwordResetConfirmRequestToStringOmitsNewPasswordButKeepsToken() {
        var request = new PasswordResetConfirmRequest("a-visible-token", "super-secret-new-password");

        assertThat(request.toString())
                .contains("a-visible-token")
                .doesNotContain("super-secret-new-password");
    }

    @Test
    void forPasswordResetWordingIsDistinctFromStandardRegistrationMessage() {
        RegistrationAcknowledgement resetAck = RegistrationAcknowledgement.forPasswordReset();
        RegistrationAcknowledgement registrationAck = RegistrationAcknowledgement.standard();

        assertThat(resetAck.message()).isNotEqualTo(registrationAck.message());
        assertThat(resetAck.message()).containsIgnoringCase("password reset");
        assertThat(registrationAck.message()).doesNotContainIgnoringCase("password reset");
    }
}
