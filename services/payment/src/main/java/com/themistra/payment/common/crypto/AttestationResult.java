package com.themistra.payment.common.crypto;

import java.time.Instant;
import java.util.Objects;

/**
 * T15 (R19). The caller-facing outcome of {@code CryptoClient.attest(...)} - a sealed interface
 * rather than a nullable-field record (Phase 3, Kimi Finding 1), so a caller cannot read
 * {@code signature()} on a {@link Blocked} outcome and silently get {@code null}: the compiler
 * forces a {@code switch}/{@code instanceof} over both cases. Mirrors the real crypto-service's
 * own {@code AttestOutcome} enumeration (confirmed via direct source read), but modeled here as a
 * sealed type instead of a flag plus nullable fields, matching this task's own stated goal of a
 * "typed way to distinguish" the outcomes.
 */
public sealed interface AttestationResult {

    /** Phase 9 (Kimi Phase 8 New Finding 3): a {@code Signed} outcome is semantically broken
     * without all three fields - {@code CryptoClient}'s own guard already prevents constructing
     * one with a null field from a real response, but this compact constructor makes the type
     * itself self-guarding against any other construction path (a test, a future caller). */
    record Signed(String signature, String kmsKeyId, Instant signedAt) implements AttestationResult {
        public Signed {
            Objects.requireNonNull(signature, "signature must not be null");
            Objects.requireNonNull(kmsKeyId, "kmsKeyId must not be null");
            Objects.requireNonNull(signedAt, "signedAt must not be null");
        }
    }

    record Blocked(String reason) implements AttestationResult {
    }
}
