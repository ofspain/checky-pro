package com.themistra.crypto.watch;

import com.themistra.crypto.events.OutboxPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Emits {@code chain.tx.seen}/{@code chain.tx.confirmed}/{@code chain.tx.finalized} (R8-R10) via the
 * only sanctioned publishing path, {@link OutboxPublisher} (agents.md "Events & messaging") - mirrors
 * {@code ProviderDegradedPublisher}'s (T10) shape exactly. Aggregate types {@code "tx-seen"}/
 * {@code "tx-confirmed"}/{@code "tx-finalized"} are already mapped in {@code EventTopics} (T04, never
 * called until this task).
 *
 * <p><b>Idempotency key (L5/R12, frozen literally - T17 Phase 3 Finding 1).</b> {@code
 * "{chain}:{txHash}:{seen|confirmed|finalized}"}, deterministic and fixed for the life of a
 * transaction - unlike {@code ProviderDegradedPublisher}'s randomized key (a provider can degrade
 * repeatedly; a transaction reaches each lifecycle stage at most once). This exact format is LOCKED
 * (L5) and is not widened with a {@code watchId} component even though two watches can legitimately
 * share an address (T15/T16's own accepted design) and thus both independently detect the same
 * {@code txHash} - in that case only one event of a given type is ever emitted system-wide for that
 * {@code (chain, txHash)}; the second attempt's unique-constraint violation is caught by {@link
 * #publish} and treated as benign, not propagated (Finding #12).</p>
 *
 * <p><b>Swallowing the duplicate-key violation (Finding #12).</b> Unlike {@code
 * TokenAllowlistSeeder}'s (T11) equivalent catch, which re-queries to confirm the row now exists before
 * treating a {@link DataIntegrityViolationException} as benign (since that table's rows carry
 * externally-influenced data with other paths to a constraint violation), this class does not: {@code
 * idempotency_key} is the only unique constraint on {@code outbox}, every other column is validated by
 * {@link OutboxPublisher} itself (null/blank checks) before any database call is made, and this class
 * is the only caller ever constructing a key of this exact shape - a {@link
 * DataIntegrityViolationException} reaching this call site has no other plausible cause than the
 * documented idempotency collision. Re-querying would need a new public method on {@code
 * OutboxPublisher} or direct repository access, both outside this task's frozen scope (`OutboxPublisher`
 * is explicitly not to be modified).</p>
 *
 * <p><b>Amount as a decimal string (agents.md).</b> {@code FinalizedPayload.amount} is a {@link
 * String} ({@code BigDecimal.toPlainString()}), never a raw {@code BigDecimal} - Jackson would
 * otherwise serialize it as a JSON number, which agents.md and this event's own schema (design.md
 * §4c) both explicitly forbid.</p>
 */
@Component
public class TxLifecyclePublisher {

    private static final Logger log = LoggerFactory.getLogger(TxLifecyclePublisher.class);

    private final OutboxPublisher outboxPublisher;
    private final Clock clock;

    public TxLifecyclePublisher(OutboxPublisher outboxPublisher, Clock clock) {
        this.outboxPublisher = outboxPublisher;
        this.clock = clock;
    }

    /** R8: first quorum-agreed sighting of a transaction. {@code confirmations} is the majority
     * confirmation count observed alongside the agreeing {@code EXISTENCE} answers - best-effort, not
     * itself independently quorum-decided at this point (T17 Phase 9, Kimi Finding 6). */
    public void seen(Watch watch, String txHash, int confirmations) {
        Instant occurredAt = clock.instant();
        SeenPayload payload = new SeenPayload(idempotencyKey(watch.chain(), txHash, "seen"),
                watch.watchId(), watch.invoiceUuid(), watch.chain(), txHash,
                watch.tokenContractAddress(), confirmations, occurredAt);
        publish("tx-seen", watch, txHash, "seen", "chain.tx.seen", payload);
    }

    /** R9: emitted exactly once, at the transaction's single {@code CONFIRMATIONS} quorum decision
     * (T17 frozen brief's one-shot resolution of R9's "gains confirmations" framing). */
    public void confirmed(Watch watch, String txHash, int confirmations) {
        Instant occurredAt = clock.instant();
        ConfirmedPayload payload = new ConfirmedPayload(idempotencyKey(watch.chain(), txHash, "confirmed"),
                watch.watchId(), watch.invoiceUuid(), watch.chain(), txHash,
                watch.tokenContractAddress(), confirmations, occurredAt);
        publish("tx-confirmed", watch, txHash, "confirmed", "chain.tx.confirmed", payload);
    }

    /** R10: emitted only once {@code FINALITY} quorum-agrees {@code true}. {@code cursor} supplies the
     * durable {@code amount}/{@code fromAddress}/{@code toAddress} snapshot captured at {@link #seen} -
     * see {@link ChainCursor#recordSeenTransaction}. {@code confirmations} is deliberately not carried
     * on this event (T17 Phase 9, Kimi Finding 6): unlike {@code amount}/{@code fromAddress}/
     * {@code toAddress}, no durable source for it exists by the time finality is reached - the
     * {@code CONFIRMATIONS} quorum decision's own agreed value is not persisted anywhere queryable
     * (like every {@code QuorumDecision}, it stores only the outcome, never the value), and adding one
     * would mean a further {@code chain_cursors} column outside this review-resolution phase's
     * proportionate scope. Optional per the schema (design.md §4c), so omitting it is not a violation. */
    public void finalized(Watch watch, ChainCursor cursor) {
        Instant occurredAt = clock.instant();
        String txHash = cursor.txHash();
        FinalizedPayload payload = new FinalizedPayload(idempotencyKey(watch.chain(), txHash, "finalized"),
                watch.watchId(), watch.invoiceUuid(), watch.chain(), txHash,
                watch.tokenContractAddress(),
                cursor.amount() == null ? null : cursor.amount().toPlainString(),
                cursor.fromAddress(), cursor.toAddress(), occurredAt);
        publish("tx-finalized", watch, txHash, "finalized", "chain.tx.finalized", payload);
    }

    private static String idempotencyKey(String chain, String txHash, String eventTypeSuffix) {
        return chain + ":" + txHash + ":" + eventTypeSuffix;
    }

    private void publish(String aggregateType, Watch watch, String txHash, String eventTypeSuffix,
                          String eventType, Object payload) {
        String idempotencyKey = idempotencyKey(watch.chain(), txHash, eventTypeSuffix);
        try {
            outboxPublisher.publish(aggregateType, watch.watchId().toString(), eventType, idempotencyKey,
                    payload);
        } catch (DataIntegrityViolationException e) {
            log.debug("chain.tx.{} already emitted for chain={} txHash={} (idempotencyKey={}) - "
                            + "ignoring duplicate publish attempt", eventTypeSuffix, watch.chain(), txHash,
                    idempotencyKey, e);
        }
    }

    public record SeenPayload(String idempotencyKey, UUID watchId, UUID invoiceUuid, String chain,
                               String txHash, String tokenContractAddress, int confirmations,
                               Instant occurredAt) {
    }

    public record ConfirmedPayload(String idempotencyKey, UUID watchId, UUID invoiceUuid, String chain,
                                    String txHash, String tokenContractAddress, int confirmations,
                                    Instant occurredAt) {
    }

    public record FinalizedPayload(String idempotencyKey, UUID watchId, UUID invoiceUuid, String chain,
                                    String txHash, String tokenContractAddress, String amount,
                                    String fromAddress, String toAddress, Instant occurredAt) {
    }
}
