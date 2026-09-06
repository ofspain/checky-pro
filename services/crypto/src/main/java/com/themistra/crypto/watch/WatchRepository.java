package com.themistra.crypto.watch;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Watch persistence.
 *
 * <p>There is exactly one query path for active watches, and it always takes the instant to
 * evaluate expiry against. A second path that forgot the time predicate would silently resurrect
 * expired watches and quietly spend provider calls on invoices nobody paid.
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
}
