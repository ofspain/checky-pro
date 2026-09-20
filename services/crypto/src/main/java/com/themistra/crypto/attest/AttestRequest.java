package com.themistra.crypto.attest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * {@code POST /internal/v1/attest} request body (L10, `design.md` §4c) - hand-written, not generated
 * from {@code contracts/api/crypto-internal.yaml} (that file does not exist anywhere in this repository
 * yet, matching every prior task's own disclosed precedent, e.g. {@code RegisterWatchRequest}).
 *
 * <p>{@code chain}'s pattern matches {@code RegisterWatchRequest}'s own exact regex
 * ({@code "ETHEREUM|TRON"}, no anchors) - Phase 4 Finding #11: an unsupported chain value fails bean
 * validation ({@code 400}), rather than silently reaching the quorum/cursor lookups and surfacing as a
 * {@code 409}. {@code txHash} is deliberately only {@code @NotBlank} - a chain-specific hash-format
 * check is {@code token.AddressValidator}-adjacent scope, disproportionate here; a malformed-but-non-
 * blank {@code txHash} is intentionally {@code 409 REFUSED} (no matching quorum decision or cursor will
 * ever be found for it), not {@code 400}.</p>
 */
public record AttestRequest(
        @NotBlank @Pattern(regexp = "^[0-9a-fA-F]{64}$") String receiptDigestSha256,
        @NotBlank @Pattern(regexp = "ETHEREUM|TRON") String chain,
        @NotBlank String txHash
) {
}
