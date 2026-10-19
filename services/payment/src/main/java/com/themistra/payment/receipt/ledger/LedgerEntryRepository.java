package com.themistra.payment.receipt.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * T19. Also hosts the advisory-lock native query (Phase 3/4, Kimi Finding 3) - no entity backs
 * that query, but a {@code JpaRepository} is still the established place in this codebase for a
 * custom query, native or not, mirroring every other repository's own shape.
 */
interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    Optional<LedgerEntry> findTopByOrderBySeqDesc();

    /** For the integrity check: {@code receipt_id} is {@code UNIQUE}, so at most one entry. */
    Optional<LedgerEntry> findByReceiptId(Long receiptId);

    /** T20 (R22) - the full chain, head (highest {@code seq}) first, for {@code
     * LedgerVerifier.verify()}'s own head-to-tail walk. Fetches the whole table into memory - a
     * reasonable Phase 1/MVP choice (no pagination/streaming), matching this codebase's own
     * established "no premature optimization" stance. */
    List<LedgerEntry> findAllByOrderBySeqDesc();

    /**
     * Serializes every ledger append through one critical section - a fixed, namespaced
     * advisory-lock key, not derived from any row, so it protects the genesis case too (no row
     * exists yet for the very first append, so a row-level {@code SELECT ... FOR UPDATE} would
     * have nothing to lock). {@code pg_advisory_xact_lock} (not the session-scoped
     * {@code pg_advisory_lock}) is held until the enclosing transaction commits or rolls back,
     * so it is automatically released - no explicit unlock call anywhere in this codebase.
     */
    @Query(value = "SELECT pg_advisory_xact_lock(hashtext('themistra.payment.ledger.append')::bigint)",
            nativeQuery = true)
    void acquireAppendLock();
}
