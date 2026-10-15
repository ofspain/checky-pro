package com.themistra.payment.common.crypto;

import java.time.Instant;

/**
 * {@code POST /internal/v1/attest}'s raw {@code 200} wire shape (`design.md` §4c,
 * `contracts/api/crypto-internal.yaml` {@code AttestResponse} schema) - mirrors the real
 * crypto-service's own {@code attest.AttestResponse} record exactly, including its nullable
 * fields: unlike {@link AttestationResult} (the caller-facing, compiler-enforced outcome type),
 * this record is only ever the intermediate deserialization target {@code CryptoClient} maps
 * into an {@code AttestationResult} - it is never returned to a caller directly (Phase 3, Kimi
 * Finding 1).
 */
public record AttestResponse(String signature, String kmsKeyId, Instant signedAt, String outcome,
                              String reason) {
}
