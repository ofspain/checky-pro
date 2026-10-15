package com.themistra.payment.common.crypto;

import java.time.Instant;

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

    record Signed(String signature, String kmsKeyId, Instant signedAt) implements AttestationResult {
    }

    record Blocked(String reason) implements AttestationResult {
    }
}
