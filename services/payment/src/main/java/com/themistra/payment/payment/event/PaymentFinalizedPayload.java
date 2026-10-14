package com.themistra.payment.payment.event;

import java.time.Instant;
import java.util.UUID;

/**
 * The {@code payment.finalized} outbox event body (R10). {@code amount} is a decimal
 * {@code String}, never a JSON number (L6 — matches {@code InvoiceCreatedPayload}'s own identical
 * convention). No formal contract schema exists yet ({@code contracts/events/payments/} is task
 * 24's own scope). Deliberately no {@code invoiceUuid} field — unlike {@code PaymentSeenPayload},
 * {@link com.themistra.payment.payment.PaymentService#applyFinalized} has no {@code Invoice}
 * object on hand (correlation for this event type is by {@code (chain, txHash)} against an
 * already-created {@code Payment} row, never the invoice) — adding one would mean a new
 * dependency on {@code InvoiceService} for a field this payload doesn't strictly need;
 * {@code paymentUuid} is this event's own natural identifier.
 */
public record PaymentFinalizedPayload(
        UUID paymentUuid,
        String chain,
        String txHash,
        String fromAddress,
        String toAddress,
        String tokenContractAddress,
        String amount,
        int confirmations,
        boolean addressPoisoningFlag,
        Instant occurredAt
) {
}
