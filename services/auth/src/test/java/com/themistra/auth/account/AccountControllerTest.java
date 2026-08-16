package com.themistra.auth.account;

import com.themistra.auth.account.dto.AccountResponse;
import com.themistra.auth.account.dto.ChangePasswordRequest;
import com.themistra.auth.account.dto.PasswordResetConfirmRequest;
import com.themistra.auth.account.dto.PasswordResetRequest;
import com.themistra.auth.account.dto.RegisterAccountRequest;
import com.themistra.auth.account.dto.RegistrationAcknowledgement;
import com.themistra.auth.account.dto.ResendVerificationRequest;
import com.themistra.auth.account.dto.VerifyEmailRequest;
import com.themistra.auth.token.SessionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

    @Mock
    private AccountService accountService;

    @Mock
    private SessionService sessionService;

    private AccountController controller;

    @Test
    void registerReturnsUniformAcknowledgementOnSuccess() {
        controller = new AccountController(accountService, sessionService);
        when(accountService.register(any())).thenReturn(
                new AccountResponse(UUID.randomUUID(), "new@example.com", false,
                        AccountStatus.PENDING_VERIFICATION, Instant.now()));

        ResponseEntity<RegistrationAcknowledgement> response =
                controller.register(new RegisterAccountRequest("new@example.com", "correct-horse-battery"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isEqualTo(RegistrationAcknowledgement.standard());
    }

    @Test
    void registerReturnsTheIdenticalAcknowledgementOnDuplicateEmail_enumerationSafety() {
        controller = new AccountController(accountService, sessionService);
        when(accountService.register(any())).thenThrow(new DuplicateEmailException());

        ResponseEntity<RegistrationAcknowledgement> response =
                controller.register(new RegisterAccountRequest("taken@example.com", "correct-horse-battery"));

        // identical status and body to the success case — a caller cannot distinguish the two
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isEqualTo(RegistrationAcknowledgement.standard());
    }

    @Test
    void registerPropagatesPolicyViolationForTheExceptionHandlerToTranslate() {
        // No local catch for this exception type - only DuplicateEmailException is swallowed
        // here (enumeration safety). A policy violation must reach AccountExceptionHandler.
        controller = new AccountController(accountService, sessionService);
        when(accountService.register(any()))
                .thenThrow(new PasswordPolicy.PasswordPolicyViolationException("too short"));

        assertThatThrownBy(() ->
                controller.register(new RegisterAccountRequest("new@example.com", "short")))
                .isInstanceOf(PasswordPolicy.PasswordPolicyViolationException.class);
    }

    @Test
    void meDerivesAccountFromAuthenticationPrincipalNotAPathVariable() {
        controller = new AccountController(accountService, sessionService);
        UUID accountUuid = UUID.randomUUID();
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(accountUuid.toString());
        AccountResponse expected = new AccountResponse(
                accountUuid, "me@example.com", true, AccountStatus.ACTIVE, Instant.now());
        when(accountService.getByUuid(accountUuid)).thenReturn(expected);

        AccountResponse response = controller.me(authentication);

        assertThat(response).isEqualTo(expected);
    }

    @Test
    void verifyEmailReturnsNoContentOnSuccess() {
        controller = new AccountController(accountService, sessionService);
        when(accountService.activateFromVerificationToken("a-valid-token")).thenReturn(
                new AccountResponse(UUID.randomUUID(), "verified@example.com", true,
                        AccountStatus.ACTIVE, Instant.now()));

        ResponseEntity<Void> response = controller.verifyEmail(new VerifyEmailRequest("a-valid-token"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
    }

    @Test
    void verifyEmailPropagatesRejectionForTheExceptionHandlerToTranslate() {
        // No local catch here (unlike register): the actual 400/INVALID_TOKEN response is
        // AccountExceptionHandler's job (AccountExceptionHandlerTest), not observable through
        // this plain-Mockito controller test.
        controller = new AccountController(accountService, sessionService);
        when(accountService.activateFromVerificationToken(any()))
                .thenThrow(new AccountService.VerificationTokenRejectedException());

        assertThatThrownBy(() -> controller.verifyEmail(new VerifyEmailRequest("bad-token")))
                .isInstanceOf(AccountService.VerificationTokenRejectedException.class);
    }

    @Test
    void resendVerificationAlwaysReturnsTheSameAcknowledgementRegardlessOfMatch() {
        // resendVerificationIfPending is void - nothing for the controller to branch on, whether
        // it actually issued a token or silently no-opped (R6, as modified: uniform response).
        controller = new AccountController(accountService, sessionService);

        RegistrationAcknowledgement matchResponse =
                controller.resendVerification(new ResendVerificationRequest("pending@example.com"));
        RegistrationAcknowledgement noMatchResponse =
                controller.resendVerification(new ResendVerificationRequest("unknown@example.com"));

        assertThat(matchResponse).isEqualTo(RegistrationAcknowledgement.standard());
        assertThat(noMatchResponse).isEqualTo(RegistrationAcknowledgement.standard());
        verify(accountService).resendVerificationIfPending("pending@example.com");
        verify(accountService).resendVerificationIfPending("unknown@example.com");
    }

    @Test
    void passwordResetRequestReturnsForPasswordResetAcknowledgementRegardlessOfMatch() {
        // Distinct wording from resendVerification's standard() acknowledgement (Finding 5/R12) —
        // and, like resendVerification, nothing here to branch on since requestPasswordReset never
        // throws for a non-match. Kimi Phase 11 Gap 1: prove both a match and a non-match return
        // the identical acknowledgement, mirroring resendVerificationAlwaysReturnsTheSameAcknowledgementRegardlessOfMatch.
        controller = new AccountController(accountService, sessionService);

        RegistrationAcknowledgement matchResponse =
                controller.passwordResetRequest(new PasswordResetRequest("reset-me@example.com"));
        RegistrationAcknowledgement noMatchResponse =
                controller.passwordResetRequest(new PasswordResetRequest("unknown@example.com"));

        assertThat(matchResponse).isEqualTo(RegistrationAcknowledgement.forPasswordReset());
        assertThat(noMatchResponse).isEqualTo(RegistrationAcknowledgement.forPasswordReset());
        verify(accountService).requestPasswordReset("reset-me@example.com");
        verify(accountService).requestPasswordReset("unknown@example.com");
    }

    @Test
    void passwordResetReturnsNoContentOnSuccess() {
        controller = new AccountController(accountService, sessionService);

        ResponseEntity<Void> response =
                controller.passwordReset(new PasswordResetConfirmRequest("valid-reset-token", "new-password"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        verify(accountService).resetPassword("valid-reset-token", "new-password");
    }

    @Test
    void passwordResetPropagatesRejectionForTheExceptionHandlerToTranslate() {
        // No local catch here (mirrors verifyEmail): the actual 400/INVALID_TOKEN response is
        // AccountExceptionHandler's job, not observable through this plain-Mockito controller test.
        controller = new AccountController(accountService, sessionService);
        doThrow(new AccountService.VerificationTokenRejectedException())
                .when(accountService).resetPassword(any(), any());

        assertThatThrownBy(() ->
                controller.passwordReset(new PasswordResetConfirmRequest("bad-token", "new-password")))
                .isInstanceOf(AccountService.VerificationTokenRejectedException.class);
    }

    @Test
    void passwordResetPropagatesPolicyViolationForTheExceptionHandlerToTranslate() {
        controller = new AccountController(accountService, sessionService);
        org.mockito.Mockito.doThrow(new PasswordPolicy.PasswordPolicyViolationException("too short"))
                .when(accountService).resetPassword(any(), any());

        assertThatThrownBy(() ->
                controller.passwordReset(new PasswordResetConfirmRequest("valid-reset-token", "short")))
                .isInstanceOf(PasswordPolicy.PasswordPolicyViolationException.class);
    }

    @Test
    void changePasswordReturnsNoContentOnSuccess() {
        controller = new AccountController(accountService, sessionService);
        UUID accountUuid = UUID.randomUUID();
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(accountUuid.toString());

        ResponseEntity<Void> response = controller.changePassword(
                authentication, new ChangePasswordRequest("current-password", "new-password"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        verify(accountService).changePassword(accountUuid, "current-password", "new-password");
    }

    @Test
    void changePasswordPropagatesCurrentPasswordMismatchForTheExceptionHandlerToTranslate() {
        controller = new AccountController(accountService, sessionService);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(UUID.randomUUID().toString());
        org.mockito.Mockito.doThrow(new AccountService.CurrentPasswordMismatchException())
                .when(accountService).changePassword(any(), any(), any());

        assertThatThrownBy(() -> controller.changePassword(
                authentication, new ChangePasswordRequest("wrong-password", "new-password")))
                .isInstanceOf(AccountService.CurrentPasswordMismatchException.class);
    }

    @Test
    void changePasswordPropagatesPolicyViolationForTheExceptionHandlerToTranslate() {
        controller = new AccountController(accountService, sessionService);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(UUID.randomUUID().toString());
        org.mockito.Mockito.doThrow(new PasswordPolicy.PasswordPolicyViolationException("too short"))
                .when(accountService).changePassword(any(), any(), any());

        assertThatThrownBy(() -> controller.changePassword(
                authentication, new ChangePasswordRequest("current-password", "short")))
                .isInstanceOf(PasswordPolicy.PasswordPolicyViolationException.class);
    }
}
