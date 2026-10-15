package com.themistra.payment.common.crypto;

/**
 * {@code POST /internal/v1/attest} request body (`design.md` §4c, `contracts/api/crypto-internal.yaml`
 * {@code AttestRequest} schema) - confirmed field-for-field against the real, built
 * {@code services/crypto}'s own {@code attest.AttestRequest}. This client trusts its caller and
 * performs no local validation of these fields (mirrors {@link RegisterWatchRequest}'s own
 * identical stance) - T16's {@code ReceiptDigest} does not exist yet, so {@code
 * receiptDigestSha256} arrives here as a plain, already-computed {@code String}.
 */
public record AttestRequest(String receiptDigestSha256, String chain, String txHash) {
}
