package com.themistra.notification.consumer.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

/**
 * Deserialization record for {@code payments.invoice.created}
 * ({@code contracts/events/payments/invoice-created.v1.schema.json}). Hand-written, as T06's DTOs
 * are; {@code PaymentEventContractTest} is the structural substitute for generation. {@code amount}
 * is whole token base units as a decimal string (L6), never a number.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record InvoiceCreatedEvent(
        UUID invoiceUuid,
        UUID merchantAccountUuid,
        String chain,
        String tokenContractAddress,
        Short tokenDecimals,
        String amount,
        String payToAddress,
        String reference,
        Instant expiresAt
) {
}
