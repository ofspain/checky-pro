package com.themistra.crypto.watch;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Reorg-safe per-watch progress (L6) - maps {@code chain.chain_cursors} exactly as shipped by T02 (see
 * {@code V1__chain_baseline.sql}). This task creates exactly one placeholder row per registered watch;
 * it never advances a cursor.
 *
 * <p><b>{@code lastBlock = -1L} is a sentinel, never a real chain position (Phase 2/4 design
 * decision).</b> {@code ChainAdapter} - a VERBATIM, frozen interface - has no "current block number"
 * method independent of an existing transaction hash, so no real initial value is obtainable here.
 * {@code -1} is chosen over {@code 0} specifically because block {@code 0} is a real genesis block
 * number on both launch chains and so could be mistaken for a genuine cursor position; block numbers
 * are never negative in reality, so {@code -1} cannot be confused with one. {@code lastBlock} is
 * advanced by task 16's {@code Watcher}; {@link #lastFinalizedBlock} and the transaction snapshot
 * fields ({@link #txHash}, {@link #amount}, {@link #fromAddress}, {@link #toAddress}) are populated by
 * task 17's finality-poll and seen-transaction handling respectively - this row's own values are never
 * read as chain fact by this task.</p>
 */
@Entity
@Table(name = "chain_cursors", schema = "chain")
public class ChainCursor {

    /** Never a real chain position - see class Javadoc. */
    static final long UNSTARTED_SENTINEL = -1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String chain;

    @Column(name = "watch_id", nullable = false)
    private UUID watchId;

    @Column(name = "last_block", nullable = false)
    private long lastBlock;

    @Column(name = "last_finalized_block")
    private Long lastFinalizedBlock;

    @Column(name = "tx_hash", length = 128)
    private String txHash;

    @Column(name = "amount", precision = 78, scale = 0)
    private BigDecimal amount;

    @Column(name = "from_address", length = 128)
    private String fromAddress;

    @Column(name = "to_address", length = 128)
    private String toAddress;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ChainCursor() {
        // JPA only
    }

    /** The placeholder row created alongside a newly registered {@link Watch} (T15's own scope,
     * task statement) - {@code lastBlock = -1} (sentinel, see class Javadoc), {@code
     * lastFinalizedBlock = null}. {@code watchId} and {@code chain} always match the {@link Watch}
     * this cursor belongs to (Phase 3 Finding 8) - the schema itself enforces neither relationship, so
     * this factory is the sole place that guarantees it. */
    public static ChainCursor placeholder(String chain, UUID watchId, Instant now) {
        Objects.requireNonNull(chain, "chain");
        Objects.requireNonNull(watchId, "watchId");
        Objects.requireNonNull(now, "now");

        ChainCursor cursor = new ChainCursor();
        cursor.chain = chain;
        cursor.watchId = watchId;
        cursor.lastBlock = UNSTARTED_SENTINEL;
        cursor.lastFinalizedBlock = null;
        cursor.updatedAt = now;
        return cursor;
    }

    /** T16 AC6: forward-only. A call with {@code blockNumber <= lastBlock} is a silent no-op - never
     * regresses the cursor (that is task 18's exclusive, reorg-walk-back job). Mirrors {@code
     * ProviderHealth}'s own "no raw setters, named mutators" convention. */
    public void advanceTo(long blockNumber, Instant now) {
        if (blockNumber <= lastBlock) {
            return;
        }
        lastBlock = blockNumber;
        updatedAt = now;
    }

    /** T17 AC1 (write-once): captures the transaction this watch has seen, once, at the moment
     * {@code EXISTENCE} first quorum-agrees {@code true} for it - the durable source for the {@code
     * chain.tx.finalized} payload's {@code amount}/{@code fromAddress}/{@code toAddress} fields, since
     * {@code Watcher}'s own in-memory correlation for this transaction is pruned immediately after
     * that decision (T16). A no-op if this cursor already has a {@code txHash} recorded - a watch that
     * legitimately observes a second, distinct transaction after its first keeps only the first
     * snapshot (a disclosed, accepted limitation of this task's own scope, not a defect). */
    public void recordSeenTransaction(String txHash, BigDecimal amount, String fromAddress,
                                       String toAddress, Instant now) {
        if (this.txHash != null) {
            return;
        }
        this.txHash = Objects.requireNonNull(txHash, "txHash");
        this.amount = amount;
        this.fromAddress = fromAddress;
        this.toAddress = toAddress;
        this.updatedAt = now;
    }

    /** T17 AC8: forward-only, mirrors {@link #advanceTo} exactly - set only once {@code FINALITY}
     * quorum-agrees {@code true} for this watch's seen transaction. */
    public void advanceFinalizedTo(long finalizedBlockNumber, Instant now) {
        if (lastFinalizedBlock != null && finalizedBlockNumber <= lastFinalizedBlock) {
            return;
        }
        lastFinalizedBlock = finalizedBlockNumber;
        updatedAt = now;
    }

    public Long id() {
        return id;
    }

    public String chain() {
        return chain;
    }

    public UUID watchId() {
        return watchId;
    }

    public long lastBlock() {
        return lastBlock;
    }

    public Long lastFinalizedBlock() {
        return lastFinalizedBlock;
    }

    public String txHash() {
        return txHash;
    }

    public BigDecimal amount() {
        return amount;
    }

    public String fromAddress() {
        return fromAddress;
    }

    public String toAddress() {
        return toAddress;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
