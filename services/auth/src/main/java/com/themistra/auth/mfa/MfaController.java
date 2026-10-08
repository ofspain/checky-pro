package com.themistra.auth.mfa;

import com.themistra.auth.mfa.dto.BeginEnrollResponse;
import com.themistra.auth.mfa.dto.PasswordAndTotpRequest;
import com.themistra.auth.mfa.dto.RecoveryCodesResponse;
import com.themistra.auth.mfa.dto.TotpCodeRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * {@code /accounts/me/mfa} — the HTTP surface for {@link MfaService}'s already-built TOTP
 * enrollment/disable logic (T16-T18, R22/R23/R28) plus recovery-code regeneration (R49, new this
 * task). Every endpoint requires authentication like any other resource-server API on this
 * service's {@code @Order(2)} chain — none are registered in
 * {@link com.themistra.auth.common.PublicEndpoints} (L11).
 *
 * <p>Depends only on {@link MfaService} and framework/DTO types, never on {@code Account} or
 * another module's entity (L12).</p>
 */
@RestController
@RequestMapping("/accounts/me/mfa")
public class MfaController {

    private final MfaService mfaService;

    public MfaController(MfaService mfaService) {
        this.mfaService = mfaService;
    }

    /**
     * Begins TOTP enrollment (R22). Returns {@code 201} with only the provisioning URI — never the
     * raw secret bytes {@link MfaService.BeginEnrollResult} also holds; the secret is already
     * encoded inside the URI for a client that needs manual entry. No {@code Location} header,
     * mirroring {@code ApiKeyController.create}'s own identical precedent: there is no
     * {@code GET} endpoint for one to correctly resolve to.
     */
    @PostMapping("/totp")
    public ResponseEntity<BeginEnrollResponse> beginEnroll(Authentication authentication) {
        UUID accountUuid = UUID.fromString(authentication.getName());
        MfaService.BeginEnrollResult result = mfaService.beginEnroll(accountUuid);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new BeginEnrollResponse(result.provisioningUri()));
    }

    /**
     * Confirms a pending TOTP enrollment (R23). Returns {@code 200} with the 10 single-use
     * recovery codes — their only appearance in plaintext, ever.
     */
    @PostMapping("/totp/confirm")
    public RecoveryCodesResponse confirm(
            Authentication authentication, @Valid @RequestBody TotpCodeRequest request) {
        UUID accountUuid = UUID.fromString(authentication.getName());
        MfaService.ConfirmResult result = mfaService.confirm(accountUuid, request.code());
        return new RecoveryCodesResponse(result.recoveryCodes());
    }

    /**
     * Disables TOTP MFA (R28): requires both the current password and a valid TOTP code. Returns
     * {@code 204}, mirroring {@code ApiKeyController.revoke}'s own identical precedent.
     */
    @DeleteMapping("/totp")
    public ResponseEntity<Void> disable(
            Authentication authentication, @Valid @RequestBody PasswordAndTotpRequest request) {
        UUID accountUuid = UUID.fromString(authentication.getName());
        mfaService.disable(accountUuid, request.currentPassword(), request.code());
        return ResponseEntity.noContent().build();
    }

    /**
     * Regenerates every recovery code (R49): requires both the current password and a valid TOTP
     * code, the same shape {@link #disable} requires — reused rather than duplicated into a second
     * near-identical request record. Returns {@code 200} with the 10 new codes, their only
     * appearance in plaintext.
     */
    @PostMapping("/recovery-codes")
    public RecoveryCodesResponse regenerateRecoveryCodes(
            Authentication authentication, @Valid @RequestBody PasswordAndTotpRequest request) {
        UUID accountUuid = UUID.fromString(authentication.getName());
        MfaService.RegenerateRecoveryCodesResult result =
                mfaService.regenerateRecoveryCodes(accountUuid, request.currentPassword(), request.code());
        return new RecoveryCodesResponse(result.recoveryCodes());
    }
}
