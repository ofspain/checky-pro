package com.themistra.crypto.attest;

import java.util.List;

/**
 * {@code GET /.well-known/themistra-verification-keys}'s {@code 200} response body (R24, `design.md`
 * §4c) - {@code { keys: [ { kid, kmsKeyId, alg, publicKeyPem } ] }} exactly. Always a single-element
 * list today (one configured {@code KmsProperties.keyId()}) - the wire shape's own plural naming is
 * honored structurally without inventing a multi-key config surface nothing else in this spec calls for
 * (Phase 2, unchallenged at Phase 3).
 */
public record VerificationKeysResponse(List<PublicKeyInfo> keys) {
}
