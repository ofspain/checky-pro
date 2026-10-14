package com.themistra.payment.common.crypto;

import java.time.Instant;
import java.util.UUID;

/**
 * Mirrors {@code contracts/api/crypto-internal.yaml}'s {@code RegisterWatchRequest} schema
 * field-for-field. {@code expectedAmount} is a decimal {@code String}, never a JSON number — the
 * contract's own explicit text ("Token base units as a decimal string, never a JSON number").
 */
public record RegisterWatchRequest(
        UUID invoiceUuid,
        String chain,
        String address,
        String tokenContractAddress,
        String expectedAmount,
        Instant expiresAt
) {
}
