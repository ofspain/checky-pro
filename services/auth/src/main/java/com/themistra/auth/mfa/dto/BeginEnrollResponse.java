package com.themistra.auth.mfa.dto;

/**
 * {@code POST /accounts/me/mfa/totp}'s response body (R22). Carries only the provisioning URI —
 * never the raw TOTP secret bytes {@code MfaService.BeginEnrollResult} also holds. The secret is
 * already encoded inside the URI for a client that needs manual entry instead of a QR scan.
 */
public record BeginEnrollResponse(String provisioningUri) {
}
