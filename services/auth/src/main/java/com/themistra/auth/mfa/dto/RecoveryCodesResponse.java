package com.themistra.auth.mfa.dto;

import java.util.List;

/**
 * {@code POST /accounts/me/mfa/totp/confirm}'s and {@code POST /accounts/me/mfa/recovery-codes}'s
 * shared response body (R23, R49) — both operations hand back a freshly generated batch of raw,
 * single-use recovery codes, their only appearance in plaintext, never re-derivable afterward.
 */
public record RecoveryCodesResponse(List<String> recoveryCodes) {
}
