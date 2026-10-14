package com.themistra.payment.consumer.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Deserialized shape of {@code chain.tx.seen} (R7) — field names match
 * {@code contracts/events/chain/tx-seen.v1.schema.json} exactly (camelCase). {@code invoiceUuid}
 * is present in the real schema but not in its own {@code required} list; this service
 * correlates by {@code watchId} instead (guaranteed present in every consumed schema) and never
 * reads {@code invoiceUuid} off this record.
 */
public record ChainTxSeen(
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
