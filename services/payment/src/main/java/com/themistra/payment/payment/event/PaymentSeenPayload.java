package com.themistra.payment.payment.event;

import java.time.Instant;
import java.util.UUID;

/**
 * The {@code payment.seen} outbox event body (R8). No formal contract schema exists yet
 * ({@code contracts/events/payments/} is task 24's own scope) - this mirrors
 * {@code InvoiceCreatedPayload}'s own established shape/conventions. {@code amount} is
 * deliberately absent: {@code chain.tx.seen} itself carries no observed amount (only
 * {@code chain.tx.finalized} does), so there is nothing non-placeholder to publish yet.
 */
public record PaymentSeenPayload(
        UUID paymentUuid,
        UUID invoiceUuid,
        String chain,
        String txHash,
        String tokenContractAddress,
        int confirmations,
        Instant occurredAt
) {
}
