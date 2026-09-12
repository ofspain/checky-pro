package com.themistra.crypto.reorg;

import com.themistra.crypto.events.OutboxPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Emits {@code chain.tx.reorged} (R11, L6) via the only sanctioned publishing path, {@link
 * OutboxPublisher} (agents.md "Events & messaging") - mirrors {@code TxLifecyclePublisher}'s (T17)
 * shape exactly. Aggregate type {@code "tx-reorged"} is already mapped in {@code EventTopics} (T04,
 * never called until this task).
 *
 * <p><b>Primitive-typed API, not {@code Watch}-typed (T18 Phase 3 Finding #4, L15).</b> Unlike {@code
 * TxLifecyclePublisher}, which lives inside {@code watch/} itself and can accept a {@code Watch}
 * directly, this class lives in the new top-level {@code reorg/} package (design.md §6's own package
 * map) - accepting a {@code watch/} JPA entity here would be a cross-feature-module entity import,
 * exactly what L15 forbids. The caller ({@code Watcher}, which already holds the {@code Watch} and
 * {@code ChainCursor} entities) extracts the primitive fields this method actually needs and performs
 * the {@code ChainCursor} mutation itself - this class only ever emits the event.</p>
 *
 * <p><b>Idempotency key (L5/R12, unmodified service-wide format).</b> {@code
 * "{chain}:{txHash}:reorged"}, deterministic - like every other {@code chain.tx.*} event (T17), this
 * permits at most one {@code chain.tx.reorged} ever for a given {@code (chain, txHash)}. A transaction
 * that is reorged out and later re-included on the canonical chain cannot produce a second lifecycle
 * event of any kind (T18 Phase 3 Finding #2, disclosed, not fixed) - {@code EXISTENCE}/{@code
 * CONFIRMATIONS}/{@code FINALITY} are already permanently decided via {@code QuorumDecisionService}'s
 * own one-decision-ever guarantee, and this key format permits no second {@code reorged} either.</p>
 *
 * <p><b>Swallowing the duplicate-key violation (T18 Phase 3 Finding #10).</b> Same disposition, same
 * justification as {@code TxLifecyclePublisher}'s identical, already-settled decision (T17 Phase 9
 * Finding #10/#12): {@code idempotency_key} is the only unique constraint on {@code outbox}, every
 * other column is validated by {@link OutboxPublisher} itself before any database call, and this class
 * is the only caller ever constructing a key of this exact shape.</p>
 */
@Component
public class ReorgDetector {

    private static final Logger log = LoggerFactory.getLogger(ReorgDetector.class);

    private final OutboxPublisher outboxPublisher;
    private final Clock clock;

    public ReorgDetector(OutboxPublisher outboxPublisher, Clock clock) {
        this.outboxPublisher = outboxPublisher;
        this.clock = clock;
    }

    /** R11: a previously observed transaction invalidated by a reorg.
     *
     * @throws NullPointerException if any argument is {@code null} (T18 Phase 9, Kimi Finding #9) -
     *     fails loudly here rather than producing a silently-corrupt idempotency key/payload (e.g.
     *     {@code "ETHEREUM:null:reorged"}) that {@link OutboxPublisher}'s own validation might not
     *     name as clearly. Mirrors {@code ChainCursor.placeholder}'s identical defensive style. */
    public void reorg(UUID watchId, UUID invoiceUuid, String chain, String txHash, String tokenContractAddress) {
        Objects.requireNonNull(watchId, "watchId");
        Objects.requireNonNull(invoiceUuid, "invoiceUuid");
        Objects.requireNonNull(chain, "chain");
        Objects.requireNonNull(txHash, "txHash");
        Objects.requireNonNull(tokenContractAddress, "tokenContractAddress");
        Instant occurredAt = clock.instant();
        String idempotencyKey = chain + ":" + txHash + ":reorged";
        ReorgedPayload payload = new ReorgedPayload(idempotencyKey, watchId, invoiceUuid, chain, txHash,
                tokenContractAddress, occurredAt);
        try {
            outboxPublisher.publish("tx-reorged", watchId.toString(), "chain.tx.reorged", idempotencyKey,
                    payload);
        } catch (DataIntegrityViolationException e) {
            log.debug("chain.tx.reorged already emitted for chain={} txHash={} (idempotencyKey={}) - "
                            + "ignoring duplicate publish attempt", chain, txHash, idempotencyKey, e);
        }
    }

    public record ReorgedPayload(String idempotencyKey, UUID watchId, UUID invoiceUuid, String chain,
                                  String txHash, String tokenContractAddress, Instant occurredAt) {
    }
}
