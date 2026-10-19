package com.themistra.crypto.watch;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface WatchRepository extends JpaRepository<Watch, Long> {

    Optional<Watch> findByWatchId(UUID watchId);

    boolean existsByWatchId(UUID watchId);

    /** T16: the set of watches {@code WatcherRegistry} distributes across shards. Every other status
     * ({@code UNREGISTERED}/{@code EXPIRED}) is excluded - the watcher only ever drives an active
     * watch's address. */
    List<Watch> findByStatus(WatchStatus status);

    /** Open-issues sweep (2026-10-19): watches no longer registered (unregistered or expired) whose
     * cursor holds a seen transaction that is not finalized yet. {@code WatcherRegistry} keeps these
     * running tracking-only, so finality and reorgs are still reported after payment-service
     * unregisters at SEEN. A reorg clears the cursor's {@code txHash} and finality sets {@code
     * lastFinalizedBlock}, so either one drops the watch from this list. */
    @Query("SELECT w FROM Watch w WHERE w.status <> com.themistra.crypto.watch.WatchStatus.REGISTERED "
            + "AND EXISTS (SELECT c FROM ChainCursor c WHERE c.watchId = w.watchId "
            + "AND c.txHash IS NOT NULL AND c.lastFinalizedBlock IS NULL)")
    List<Watch> findUnregisteredWithUnfinalizedSeenTransaction();

    /** The sole {@code DELETE}-time mutation (R19, Phase 3 Finding 4) - a single atomic conditional
     * {@code UPDATE}, not a load-then-save. Returns the number of rows updated (0 or 1); callers treat
     * either outcome as success (idempotent no-op vs. real transition) - the 404-vs-204 distinction is
     * decided separately, by {@link #existsByWatchId} in {@link WatchService#unregister}, before this
     * method ever runs. Race-safe by construction: two concurrent calls for the same {@code watchId}
     * can never both match {@code status = 'REGISTERED'} and double-set {@code unregisteredAt}. */
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Watch w SET w.status = com.themistra.crypto.watch.WatchStatus.UNREGISTERED, "
            + "w.unregisteredAt = :now WHERE w.watchId = :watchId "
            + "AND w.status = com.themistra.crypto.watch.WatchStatus.REGISTERED")
    int markUnregisteredIfRegistered(@Param("watchId") UUID watchId, @Param("now") Instant now);
}
