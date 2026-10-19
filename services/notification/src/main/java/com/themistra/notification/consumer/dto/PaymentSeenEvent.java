package com.themistra.notification.consumer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

/**
 * Deserialization record for {@code payments.payment.seen}
 * ({@code contracts/events/payments/payment-seen.v1.schema.json}). It names no merchant, only its
 * invoice; {@code PaymentRouting} supplies the merchant (T07 frozen brief D2).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentSeenEvent(
        UUID paymentUuid,
        UUID invoiceUuid,
        String chain,
        String txHash,
        String tokenContractAddress,
        Integer confirmations,
        Instant occurredAt
) {
}
