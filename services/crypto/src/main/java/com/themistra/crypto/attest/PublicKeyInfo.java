package com.themistra.crypto.attest;

/**
 * One published verification key (R24, `design.md` §4c). Public, not package-private like {@link
 * SignatureResult} - this record *is* the serialized wire payload, matching {@link AttestResponse}'s
 * own convention rather than {@code SignatureResult}'s internal-transfer-object one.
 *
 * @param kid          the key id third parties key their verification lookup on - deliberately equal to
 *                     {@code kmsKeyId} (Phase 2, unchallenged at Phase 3): no other spec document
 *                     defines a distinct value, and reusing {@code kmsKeyId} is the simplest
 *                     single-source-of-truth answer
 * @param kmsKeyId     the key identifier KMS itself returns ({@code GetPublicKeyResponse.keyId()}) - not
 *                     necessarily identical to the configured {@code KmsProperties.keyId()} if that
 *                     value is an alias (mirrors {@link SignatureResult#kmsKeyId()}'s identical
 *                     reasoning, T20/T21 precedent)
 * @param alg          the key's own authoritative supported signing algorithm
 *                     ({@code GetPublicKeyResponse.signingAlgorithms()}), not a hardcoded literal
 * @param publicKeyPem the key's X.509 SubjectPublicKeyInfo DER, PEM-encoded
 */
public record PublicKeyInfo(String kid, String kmsKeyId, String alg, String publicKeyPem) {
}
