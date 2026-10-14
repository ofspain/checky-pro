package com.themistra.payment.consumer.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Deserialized shape of {@code chain.tx.reorged} (R11/R12) — field names match
 * {@code contracts/events/chain/tx-reorged.v1.schema.json} exactly (camelCase). Correlation is
 * by {@code (chain, txHash)} against an already-created {@code Payment} row, same as
 * {@code chain.tx.confirmed}/{@code chain.tx.finalized} — not by {@code watchId} (which is only
 * used afterward, if the reversal lands on {@code WATCHING}, to find the invoice a replacement
 * watch must be registered for).
 */
public record ChainTxReorged(
        String idempotencyKey,
        UUID watchId,
        UUID invoiceUuid,
        String chain,
        String txHash,
        String tokenContractAddress,
        Instant occurredAt
) {
}
