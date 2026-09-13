package com.themistra.crypto.attest;

import java.time.Instant;

/**
 * The result of one {@link KmsSigner#sign} call. Package-private (frozen brief Phase 4, Finding #15):
 * its only legitimate consumer is the future {@code AttestationService} (task 21), which lives in this
 * same {@code attest} package per design.md's own package map - making this type public would let a
 * class outside {@code attest} depend on it without tripping {@link KmsSignerArchitectureTest}'s
 * dependency-ban rules, quietly leaking the signing abstraction's shape past the intended boundary.
 *
 * @param signatureBase64 the KMS-produced signature, standard RFC 4648 Base64 ({@link
 *                         java.util.Base64#getEncoder()}), no line-wrapping, no URL-safe variant
 * @param kmsKeyId        the key identifier KMS itself asserts it signed with ({@code
 *                         SignResponse.keyId()}) - not necessarily identical to the configured {@code
 *                         KmsProperties.keyId()} if that value is an alias (frozen brief Finding #4)
 * @param signedAt         when the signature was produced, from the injected {@link java.time.Clock}
 */
record SignatureResult(String signatureBase64, String kmsKeyId, Instant signedAt) {
}
