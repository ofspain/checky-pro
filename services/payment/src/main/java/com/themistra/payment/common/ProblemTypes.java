package com.themistra.payment.common;

import java.net.URI;

/**
 * Stable RFC 9457 problem-type URIs — part of the public API contract
 * ({@code contracts/api/payments.yaml}, once task 24 authors it). Add new types here; never
 * inline a {@code URI.create(...)} literal at a call site, and never change a published value —
 * add a new constant and deprecate the old one instead (T23, R29). Mirrors {@code auth-service}'s
 * own identical {@code ProblemTypes} shape and base URL exactly.
 */
public final class ProblemTypes {

    private static final String BASE = "https://checky.pro/problems/";

    public static final URI VALIDATION_ERROR = URI.create(BASE + "validation-error");
    public static final URI MALFORMED_REQUEST = URI.create(BASE + "malformed-request");
    public static final URI MALFORMED_REQUEST_PARAMETER = URI.create(BASE + "malformed-request-parameter");
    public static final URI MISSING_REQUIRED_PARAMETER = URI.create(BASE + "missing-required-parameter");
    public static final URI FORBIDDEN = URI.create(BASE + "forbidden");
    public static final URI UNAUTHORIZED = URI.create(BASE + "unauthorized");
    public static final URI INTERNAL_ERROR = URI.create(BASE + "internal-error");
    public static final URI INVALID_INVOICE_REQUEST = URI.create(BASE + "invalid-invoice-request");
    public static final URI CRYPTO_SERVICE_UNAVAILABLE = URI.create(BASE + "crypto-service-unavailable");
    /** Shared across every "doesn't exist or isn't yours, 404 either way" endpoint (R28) — no
     * ownership oracle between the two causes. Mirrors {@code auth-service}'s own generic,
     * reusable {@code NOT_FOUND} constant rather than a one-off per-resource name. */
    public static final URI NOT_FOUND = URI.create(BASE + "not-found");
    public static final URI INVALID_EXPORT_REQUEST = URI.create(BASE + "invalid-export-request");

    private ProblemTypes() {
    }
}
