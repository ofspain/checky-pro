package com.themistra.notification.consumer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

/**
 * Deserialization record for {@code payments.receipt.issued}
 * ({@code contracts/events/payments/receipt-issued.v1.schema.json}). {@code merchantAccountUuid}
 * is optional in the schema. {@code receiptUri} is an internal {@code s3://} location: it is
 * deserialized for schema fidelity but never passed to a template or a log (T07 frozen brief).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ReceiptIssuedEvent(
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
