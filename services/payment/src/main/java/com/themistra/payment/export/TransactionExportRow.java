package com.themistra.payment.export;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of a merchant's tax-ready export (R27). The eleven columns `design.md` O5 freezes, in
 * O5's own order. {@code amount} and {@code payToAddress} are the <b>observed</b> on-chain values
 * from {@code payments} - never {@code invoices}' expected values, even when they differ (Phase 3
 * Kimi Finding 2, accepted at Phase 4). {@code amount} is a decimal string (L6) - never a JSON
 * number.
 */
public record TransactionExportRow(
        UUID invoiceUuid,
        UUID paymentUuid,
        UUID receiptUuid,
        String chain,
        String txHash,
        String tokenContractAddress,
        short tokenDecimals,
        String amount,
        String payToAddress,
        Instant finalizedAt,
        Instant attestedAt,
        String receiptUri) {
}
