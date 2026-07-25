package com.themistra.auth.account;

import com.themistra.auth.account.dto.AccountResponse;
import com.themistra.auth.account.dto.RegisterAccountRequest;
import com.themistra.auth.account.dto.RegistrationAcknowledgement;
import com.themistra.auth.account.dto.ResendVerificationRequest;
import com.themistra.auth.account.dto.VerifyEmailRequest;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

    @Mock
    private AccountService accountService;

    private AccountController controller;

    @Test
    void registerReturnsUniformAcknowledgementOnSuccess() {
        controller = new AccountController(accountService);
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
        controller = new AccountController(accountService);
        when(accountService.register(any())).thenThrow(new DuplicateEmailException());

        ResponseEntity<RegistrationAcknowledgement> response =
                controller.register(new RegisterAccountRequest("taken@example.com", "correct-horse-battery"));

        // identical status and body to the success case — a caller cannot distinguish the two
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isEqualTo(RegistrationAcknowledgement.standard());
    }

    @Test
    void meDerivesAccountFromAuthenticationPrincipalNotAPathVariable() {
        controller = new AccountController(accountService);
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
        controller = new AccountController(accountService);
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
        controller = new AccountController(accountService);
        when(accountService.activateFromVerificationToken(any()))
                .thenThrow(new AccountService.VerificationTokenRejectedException());

        assertThatThrownBy(() -> controller.verifyEmail(new VerifyEmailRequest("bad-token")))
                .isInstanceOf(AccountService.VerificationTokenRejectedException.class);
    }

    @Test
    void resendVerificationAlwaysReturnsTheSameAcknowledgementRegardlessOfMatch() {
        // resendVerificationIfPending is void - nothing for the controller to branch on, whether
        // it actually issued a token or silently no-opped (R6, as modified: uniform response).
        controller = new AccountController(accountService);

        RegistrationAcknowledgement matchResponse =
                controller.resendVerification(new ResendVerificationRequest("pending@example.com"));
        RegistrationAcknowledgement noMatchResponse =
                controller.resendVerification(new ResendVerificationRequest("unknown@example.com"));

        assertThat(matchResponse).isEqualTo(RegistrationAcknowledgement.standard());
        assertThat(noMatchResponse).isEqualTo(RegistrationAcknowledgement.standard());
        verify(accountService).resendVerificationIfPending("pending@example.com");
        verify(accountService).resendVerificationIfPending("unknown@example.com");
    }
}
