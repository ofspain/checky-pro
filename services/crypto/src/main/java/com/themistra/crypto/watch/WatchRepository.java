package com.themistra.crypto.watch;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persists and queries watches.
 *
 * <p>A single query path for active watches: indexed by chain and expiry, with no subqueries or
 * predicates that vary per caller. This is where the quorum-verified fact lives, replicated.
 */
public interface WatchRepository extends JpaRepository<Watch, Long> {

    Optional<Watch> findByWatchUuid(UUID watchUuid);

    Optional<Watch> findByCallerReference(String callerReference);

    @Query("""
            select w from Watch w
            where w.chainId = :chainId
              and w.status = com.themistra.crypto.watch.WatchStatus.ACTIVE
              and w.expiresAt > :now
            """)
    List<Watch> findActive(@Param("chainId") String chainId, @Param("now") Instant now);

    @Query("""
            select w from Watch w
            where w.status = com.themistra.crypto.watch.WatchStatus.ACTIVE
              and w.expiresAt > :now
            """)
    List<Watch> findAllActive(@Param("now") Instant now);

    /**
     * A modifying query needs a transaction of its own: without one it throws, and a caller
     * that swallows the exception will re-find and re-publish the same payment every cycle.
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query("update Watch w set w.status = :status where w.id = :id")
    void updateStatus(@Param("id") Long id, @Param("status") WatchStatus status);
}
