package com.themistra.notification.consumer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

/**
 * Deserialization record for {@code payments.payment.finalized}
 * ({@code contracts/events/payments/payment-finalized.v1.schema.json}). It names neither invoice
 * nor merchant, only {@code paymentUuid}; {@code PaymentRouting} supplies both (T07 frozen brief
 * D2). {@code fromAddress}/{@code toAddress} are deserialized for schema fidelity but never
 * rendered or logged.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentFinalizedEvent(
        UUID paymentUuid,
        String chain,
        String txHash,
        String fromAddress,
        String toAddress,
        String tokenContractAddress,
        String amount,
        Integer confirmations,
        Boolean addressPoisoningFlag,
        Instant occurredAt
) {
}
