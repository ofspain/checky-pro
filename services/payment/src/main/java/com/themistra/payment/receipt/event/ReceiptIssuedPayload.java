package com.themistra.payment.receipt.event;

import java.time.Instant;
import java.util.UUID;

/**
 * The {@code receipt.issued} outbox event body (R25). Fields frozen to match `design.md`'s own
 * pinned JSON Schema exactly, field-for-field (Phase 3/4, Kimi Finding 1) - the physical
 * {@code contracts/events/payments/receipt-issued.v1.schema.json} file itself is task 24's own
 * job, the identical "fields match the pinned schema now, the file is authored later" precedent
 * every other published event in this service already follows ({@code InvoiceCreatedPayload}/
 * {@code PaymentSeenPayload}/{@code PaymentFinalizedPayload} - none of the three has a contract
 * file yet either). {@code amount} is a decimal {@code String}, never a JSON number (L6).
 */
public record ReceiptIssuedPayload(
        UUID invoiceUuid,
        UUID paymentUuid,
        UUID receiptUuid,
        UUID merchantAccountUuid,
        String chain,
        String txHash,
        String amount,
        String tokenContractAddress,
        String kmsKeyId,
        String receiptUri,
        Instant occurredAt
) {
}
