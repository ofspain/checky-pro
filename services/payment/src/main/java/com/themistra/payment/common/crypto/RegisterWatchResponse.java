package com.themistra.payment.common.crypto;

import java.util.UUID;

/** Mirrors {@code contracts/api/crypto-internal.yaml}'s {@code RegisterWatchResponse} schema. */
public record RegisterWatchResponse(
        UUID watchId,
        String status
) {
}
