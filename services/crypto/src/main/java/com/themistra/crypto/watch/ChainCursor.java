package com.themistra.crypto.watch;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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
 * are never negative in reality, so {@code -1} cannot be confused with one. Task 16's watcher is
 * expected to overwrite both this field and {@link #lastFinalizedBlock} with real values the first time
 * it processes this watch - this row's own values are never read as chain fact by this task.</p>
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

    @Column(name = "watch_id")
    private UUID watchId;

    @Column(name = "last_block", nullable = false)
    private long lastBlock;

    @Column(name = "last_finalized_block")
    private Long lastFinalizedBlock;

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

    public Instant updatedAt() {
        return updatedAt;
    }
}
