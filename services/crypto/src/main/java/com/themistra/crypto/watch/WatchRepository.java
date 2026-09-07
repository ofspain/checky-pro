package com.themistra.crypto.watch;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

interface WatchRepository extends JpaRepository<Watch, Long> {

    Optional<Watch> findByWatchId(UUID watchId);

    boolean existsByWatchId(UUID watchId);

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
