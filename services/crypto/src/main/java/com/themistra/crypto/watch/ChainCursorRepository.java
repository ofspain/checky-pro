package com.themistra.crypto.watch;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ChainCursorRepository extends JpaRepository<ChainCursor, Long> {

    /** T16: {@code Watcher} looks up its own watch's cursor to advance {@code lastBlock} forward. */
    Optional<ChainCursor> findByWatchId(UUID watchId);

    /** T21: {@code AttestationService} (via {@link WatchService#findChainCursors}) resolves the
     * counterparty address(es) to screen for a given transaction. Returns a {@code List}, not an
     * {@code Optional} - {@code chain_cursors} has no unique constraint on {@code (chain, tx_hash)}
     * (verified directly against {@code V1__chain_baseline.sql}), so more than one watch can
     * legitimately observe the same transaction (e.g. a sweep, or two invoices paid in one tx).
     * Supported by the new {@code V10} index. */
    List<ChainCursor> findByChainAndTxHash(String chain, String txHash);
}
