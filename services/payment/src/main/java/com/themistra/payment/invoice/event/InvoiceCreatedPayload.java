package com.themistra.payment.invoice.event;

import java.time.Instant;
import java.util.UUID;

/**
 * The {@code invoice.created} outbox event body (R2, Phase 3 finding 9). {@code amount} is a
 * decimal {@code String}, never a JSON number (L6 — matches {@code RegisterWatchRequest}'s own
 * identical convention for the same reason: JSON numbers can silently lose precision on a
 * 78-digit base-unit value).
 */
public record InvoiceCreatedPayload(
        UUID invoiceUuid,
        UUID merchantAccountUuid,
        String chain,
        String tokenContractAddress,
        short tokenDecimals,
        String amount,
        String payToAddress,
        String reference,
        Instant expiresAt
) {
}
