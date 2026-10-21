package com.themistra.payment.history.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code GET /api/v1/invoices/{invoiceUuid}/payments} item (frontend Phase 1b, 2026-10-21).
 *
 * <p>{@code state} is the payment's verification state (R32): {@code CREATED}, {@code WATCHING},
 * {@code SEEN}, {@code CONFIRMING}, {@code FINALIZED}, {@code ATTESTED} or {@code HELD}. {@code amount}
 * is token base units as a decimal string, never a JSON number, and is null until finality reports
 * it; scale it by {@code tokenDecimals}. {@code receiptUuid} is set only once a signed receipt
 * exists. A {@code HELD} payment with no {@code attestedAt} was held before attestation (R56).</p>
 */
public record PaymentResponse(
        UUID paymentUuid,
        UUID invoiceUuid,
        String chain,
        String txHash,
        String state,
        int confirmations,
        String amount,
        String tokenContractAddress,
        short tokenDecimals,
        String fromAddress,
        String amountDiscrepancy,
        boolean addressPoisoningFlag,
        Instant seenAt,
        Instant confirmedAt,
        Instant finalizedAt,
        Instant reorgedAt,
        Instant attestedAt,
        UUID receiptUuid
) {
}
