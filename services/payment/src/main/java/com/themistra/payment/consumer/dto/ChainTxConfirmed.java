package com.themistra.payment.consumer.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Deserialized shape of {@code chain.tx.confirmed} (R9) — field names match
 * {@code contracts/events/chain/tx-confirmed.v1.schema.json} exactly (camelCase). Correlation
 * for this event type is by {@code (chain, txHash)} against an already-created {@code Payment}
 * row (created by {@code chain.tx.seen}), not by {@code watchId}.
 */
public record ChainTxConfirmed(
        String idempotencyKey,
        UUID watchId,
        UUID invoiceUuid,
        String chain,
        String txHash,
        String tokenContractAddress,
        int confirmations,
        Instant occurredAt
) {
}
