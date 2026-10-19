package com.themistra.payment.receipt.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code GET /api/v1/receipts/{receiptUuid}} (R28, FU-T29-1). The stored, signed receipt: the same
 * 15 fields, in the same order, as the canonical JSON {@code ReceiptStore} writes to S3
 * ({@code ReceiptStore.toCanonicalMap}). Built from that S3 object, not from the database row, so
 * the merchant sees exactly what was signed. {@code amount} stays a decimal string of whole token
 * base units, as everywhere else in this API.
 */
public record ReceiptResponse(
        UUID receiptUuid,
        UUID invoiceUuid,
        UUID paymentUuid,
        UUID merchantAccountUuid,
        String chain,
        String txHash,
        String tokenContractAddress,
        int tokenDecimals,
        String amount,
        String payToAddress,
        String fromAddress,
        Instant finalizedAt,
        String signature,
        String kmsKeyId,
        Instant signedAt) {
}
