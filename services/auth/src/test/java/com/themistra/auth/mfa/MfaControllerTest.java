package com.themistra.auth.mfa;

import com.themistra.auth.mfa.dto.BeginEnrollResponse;
import com.themistra.auth.mfa.dto.PasswordAndTotpRequest;
import com.themistra.auth.mfa.dto.RecoveryCodesResponse;
import com.themistra.auth.mfa.dto.TotpCodeRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Direct unit test of {@link MfaController}, mirroring {@code ApiKeyControllerTest}'s style —
 * the controller is constructed directly with a mocked {@link MfaService} and never goes through
 * Spring's dispatcher. HTTP status/body mapping for a rejection is {@link MfaExceptionHandlerTest}'s
 * job; a real end-to-end round trip is {@code MfaControllerIntegrationTest}'s job.
 */
@ExtendWith(MockitoExtension.class)
class MfaControllerTest {

    private static final UUID ACCOUNT_UUID = UUID.randomUUID();

    @Mock
    private MfaService mfaService;

    private MfaController controller;

    // ---- beginEnroll (R22) ----

    @Test // R22 - happy path returns 201 with only the provisioning URI, never the raw secret
    void beginEnrollReturns201WithProvisioningUriOnly() {
        controller = new MfaController(mfaService);
        Authentication authentication = authenticationFor(ACCOUNT_UUID);
        MfaService.BeginEnrollResult serviceResult =
                new MfaService.BeginEnrollResult(new byte[]{1, 2, 3}, "otpauth://totp/Checky:" + ACCOUNT_UUID);
        when(mfaService.beginEnroll(ACCOUNT_UUID)).thenReturn(serviceResult);

        ResponseEntity<BeginEnrollResponse> response = controller.beginEnroll(authentication);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().provisioningUri()).isEqualTo("otpauth://totp/Checky:" + ACCOUNT_UUID);
    }

    @Test // no Location header - there is no GET endpoint to resolve to, mirrors ApiKeyController.create
    void beginEnrollResponseHasNoLocationHeader() {
        controller = new MfaController(mfaService);
        Authentication authentication = authenticationFor(ACCOUNT_UUID);
        when(mfaService.beginEnroll(ACCOUNT_UUID))
                .thenReturn(new MfaService.BeginEnrollResult(new byte[]{1}, "otpauth://x"));

        ResponseEntity<BeginEnrollResponse> response = controller.beginEnroll(authentication);

        assertThat(response.getHeaders().getLocation()).isNull();
    }

    @Test
    void beginEnrollDerivesCallerFromAuthentication() {
        controller = new MfaController(mfaService);
        Authentication authentication = authenticationFor(ACCOUNT_UUID);
        when(mfaService.beginEnroll(ACCOUNT_UUID))
                .thenReturn(new MfaService.BeginEnrollResult(new byte[]{1}, "otpauth://x"));

        controller.beginEnroll(authentication);

        verify(mfaService).beginEnroll(ACCOUNT_UUID);
    }

    @Test // a confirmed enrollment already exists - propagates uncaught for
          // MfaExceptionHandler.onAlreadyEnrolled to translate
    void beginEnrollPropagatesAlreadyEnrolledUncaught() {
        controller = new MfaController(mfaService);
        Authentication authentication = authenticationFor(ACCOUNT_UUID);
        when(mfaService.beginEnroll(ACCOUNT_UUID)).thenThrow(new MfaAlreadyEnrolledException());

        assertThatThrownBy(() -> controller.beginEnroll(authentication))
                .isInstanceOf(MfaAlreadyEnrolledException.class);
    }

    // ---- confirm (R23) ----

    @Test
    void confirmReturnsRecoveryCodesResponse() {
        controller = new MfaController(mfaService);
        Authentication authentication = authenticationFor(ACCOUNT_UUID);
        List<String> codes = List.of("code1", "code2");
        when(mfaService.confirm(ACCOUNT_UUID, "123456")).thenReturn(new MfaService.ConfirmResult(codes));

        RecoveryCodesResponse response = controller.confirm(authentication, new TotpCodeRequest("123456"));

        assertThat(response.recoveryCodes()).isEqualTo(codes);
    }

    @Test
    void confirmPassesCodeFromRequestBody() {
        controller = new MfaController(mfaService);
        Authentication authentication = authenticationFor(ACCOUNT_UUID);
        when(mfaService.confirm(ACCOUNT_UUID, "654321")).thenReturn(new MfaService.ConfirmResult(List.of()));

        controller.confirm(authentication, new TotpCodeRequest("654321"));

        verify(mfaService).confirm(ACCOUNT_UUID, "654321");
    }

    @Test // wrong code - propagates uncaught for MfaExceptionHandler.onInvalidCode to translate
    void confirmPropagatesInvalidCodeUncaught() {
        controller = new MfaController(mfaService);
        Authentication authentication = authenticationFor(ACCOUNT_UUID);
        when(mfaService.confirm(ACCOUNT_UUID, "000000")).thenThrow(new InvalidTotpCodeException());

        assertThatThrownBy(() -> controller.confirm(authentication, new TotpCodeRequest("000000")))
                .isInstanceOf(InvalidTotpCodeException.class);
    }

    // ---- disable (R28) ----

    @Test
    void disableReturns204() {
        controller = new MfaController(mfaService);
        Authentication authentication = authenticationFor(ACCOUNT_UUID);

        ResponseEntity<Void> response =
                controller.disable(authentication, new PasswordAndTotpRequest("correct", "123456"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
    }

    @Test // the two fields share a name across both request record types - an easy place to
          // transpose by mistake, worth its own explicit argument-order assertion
    void disablePassesPasswordAndCodeInTheRightOrder() {
        controller = new MfaController(mfaService);
        Authentication authentication = authenticationFor(ACCOUNT_UUID);

        controller.disable(authentication, new PasswordAndTotpRequest("the-password", "the-code"));

        verify(mfaService).disable(ACCOUNT_UUID, "the-password", "the-code");
    }

    @Test // wrong password - propagates uncaught for MfaExceptionHandler.onPasswordMismatch
    void disablePropagatesPasswordMismatchUncaught() {
        controller = new MfaController(mfaService);
        Authentication authentication = authenticationFor(ACCOUNT_UUID);
        org.mockito.Mockito.doThrow(new MfaCurrentPasswordMismatchException())
                .when(mfaService).disable(ACCOUNT_UUID, "wrong", "123456");

        assertThatThrownBy(() -> controller.disable(authentication, new PasswordAndTotpRequest("wrong", "123456")))
                .isInstanceOf(MfaCurrentPasswordMismatchException.class);
    }

    // ---- regenerateRecoveryCodes (R49) ----

    @Test
    void regenerateRecoveryCodesReturnsRecoveryCodesResponse() {
        controller = new MfaController(mfaService);
        Authentication authentication = authenticationFor(ACCOUNT_UUID);
        List<String> codes = List.of("new1", "new2");
        when(mfaService.regenerateRecoveryCodes(ACCOUNT_UUID, "correct", "123456"))
                .thenReturn(new MfaService.RegenerateRecoveryCodesResult(codes));

        RecoveryCodesResponse response =
                controller.regenerateRecoveryCodes(authentication, new PasswordAndTotpRequest("correct", "123456"));

        assertThat(response.recoveryCodes()).isEqualTo(codes);
    }

    @Test // the two fields share a name across both request record types - an easy place to
          // transpose by mistake, worth its own explicit argument-order assertion
    void regenerateRecoveryCodesPassesPasswordAndCodeInTheRightOrder() {
        controller = new MfaController(mfaService);
        Authentication authentication = authenticationFor(ACCOUNT_UUID);
        when(mfaService.regenerateRecoveryCodes(ACCOUNT_UUID, "the-password", "the-code"))
                .thenReturn(new MfaService.RegenerateRecoveryCodesResult(List.of()));

        controller.regenerateRecoveryCodes(authentication, new PasswordAndTotpRequest("the-password", "the-code"));

        verify(mfaService).regenerateRecoveryCodes(ACCOUNT_UUID, "the-password", "the-code");
    }

    @Test // no confirmed enrollment - propagates uncaught for MfaExceptionHandler.onNotEnrolled
    void regenerateRecoveryCodesPropagatesNotEnrolledUncaught() {
        controller = new MfaController(mfaService);
        Authentication authentication = authenticationFor(ACCOUNT_UUID);
        when(mfaService.regenerateRecoveryCodes(ACCOUNT_UUID, "correct", "123456"))
                .thenThrow(new MfaNotEnrolledException());

        assertThatThrownBy(() -> controller.regenerateRecoveryCodes(
                authentication, new PasswordAndTotpRequest("correct", "123456")))
                .isInstanceOf(MfaNotEnrolledException.class);
    }

    private static Authentication authenticationFor(UUID accountUuid) {
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn(accountUuid.toString());
        return authentication;
    }
}
