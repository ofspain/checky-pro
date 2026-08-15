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

    private ProblemTypes() {
    }
}
