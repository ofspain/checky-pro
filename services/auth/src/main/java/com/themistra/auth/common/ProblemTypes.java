package com.themistra.auth.common;

import java.net.URI;

/**
 * Stable RFC 9457 problem-type URIs — part of the public API contract (contracts/api/auth.yaml).
 * Add new types here; never inline URIs at call sites, and never change a published value.
 */
public final class ProblemTypes {

    private static final String BASE = "https://checky.pro/problems/";

    public static final URI VALIDATION_ERROR = URI.create(BASE + "validation-error");
    public static final URI MALFORMED_REQUEST = URI.create(BASE + "malformed-request");
    public static final URI NOT_FOUND = URI.create(BASE + "not-found");
    public static final URI CONFLICT = URI.create(BASE + "conflict");
    public static final URI INVALID_STATE = URI.create(BASE + "invalid-state");
    public static final URI INTERNAL_ERROR = URI.create(BASE + "internal-error");
    /** Uniform rejection for a verification token that is invalid, expired, or already used —
     * deliberately a single type covering every rejection reason (R5, enumeration safety). */
    public static final URI INVALID_TOKEN = URI.create(BASE + "invalid-token");
    /** Wrong current password on {@code POST /accounts/me/password} (R11) — not
     * enumeration-sensitive (the caller is already authenticated as this exact account). */
    public static final URI CURRENT_PASSWORD_MISMATCH = URI.create(BASE + "current-password-mismatch");
    /** Uniform rejection for {@code POST /api-keys/token} (R33, T25) — a revoked, expired,
     * malformed, unknown-prefix, or hash-mismatched key, plus a missing/wrong-scheme/blank/
     * over-length {@code Authorization} header, all map here identically; never distinguished. */
    public static final URI API_KEY_EXCHANGE_REJECTED = URI.create(BASE + "api-key-exchange-rejected");
    /** {@code DELETE /api-keys/{keyUuid}} (R35, T26) — identical whether the key doesn't exist or
     * exists but isn't owned by the caller (no enumeration oracle between the two causes). */
    public static final URI API_KEY_NOT_FOUND = URI.create(BASE + "api-key-not-found");
    /** {@code POST /api-keys} (R30, T26) — the caller lacks {@code MERCHANT} or confirmed MFA. */
    public static final URI API_KEY_NOT_AUTHORIZED = URI.create(BASE + "api-key-not-authorized");
    /** {@code DELETE /accounts/me/sessions/{familyId}} (R37, T28) — identical whether the family
     * doesn't exist or exists but isn't owned by the caller (no enumeration oracle). */
    public static final URI SESSION_NOT_FOUND = URI.create(BASE + "session-not-found");
    /** Per-account request-rate backstop exceeded (R41, T31) — login (incl. MFA verification,
     * which happens inside the same request), password-reset confirmation, or the
     * {@code /oauth2/token} refresh_token grant. */
    public static final URI RATE_LIMIT_EXCEEDED = URI.create(BASE + "rate-limit-exceeded");
    /** {@code POST /accounts/me/mfa/totp} (R22, T19) — the caller already has a confirmed TOTP
     * enrollment; enroll again only after disabling the existing one. */
    public static final URI MFA_ALREADY_ENROLLED = URI.create(BASE + "mfa-already-enrolled");
    /** {@code DELETE /accounts/me/mfa/totp} and {@code POST /accounts/me/mfa/recovery-codes}
     * (R28/R49, T19) — no confirmed TOTP enrollment exists to disable or regenerate codes for. */
    public static final URI MFA_NOT_ENROLLED = URI.create(BASE + "mfa-not-enrolled");
    /** {@code POST /accounts/me/mfa/totp/confirm}, {@code DELETE /accounts/me/mfa/totp}, and
     * {@code POST /accounts/me/mfa/recovery-codes} (R23/R28/R29/R49, T19) — the submitted TOTP code
     * was rejected. 401 here means "the second credential was rejected," not "not logged in"; the
     * caller is already authenticated. */
    public static final URI MFA_INVALID_CODE = URI.create(BASE + "mfa-invalid-code");

    private ProblemTypes() {
    }
}
