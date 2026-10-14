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
 *
 * <p><b>Phase 9 Finding 9 (deferred, contract-level, not fixable in this task).</b> The schema's
 * own idempotency key is fixed as {@code chain:txHash:reorged} - one key per transaction hash,
 * not per occurrence. A second, genuinely distinct reorg of the identical {@code (chain,
 * txHash)} (rare, but possible on chains with weak finality) would be indistinguishable from a
 * duplicate delivery of the first and silently dropped by {@code ChainEventConsumer}'s own
 * dedupe check. Tracked as a known limitation of the shared event contract, not a code defect -
 * fixing it would mean changing the idempotency-key convention itself, out of this task's scope.</p>
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
