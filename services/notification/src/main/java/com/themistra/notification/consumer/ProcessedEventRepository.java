package com.themistra.notification.consumer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {

    /**
     * {@code INSERT ... ON CONFLICT DO NOTHING} - not a JPA {@code save}/exists-then-insert. A real
     * duplicate key never raises a constraint violation at all here (Postgres silently skips the
     * conflicting row), so there is nothing to catch and nothing that can mark the caller's
     * transaction rollback-only (Kimi Phase 3 Finding #1's own concurrency requirement, discovered
     * during Phase 6 to need this shape specifically - see {@link IdempotencyGuard}'s own Javadoc).
     * Returns the number of rows actually inserted: {@code 1} for a genuinely new key, {@code 0} for
     * a duplicate.
     */
    @Modifying
    @Query(value = "INSERT INTO notifications.processed_events (event_key, event_type, processed_at) "
            + "VALUES (:eventKey, :eventType, :processedAt) ON CONFLICT (event_key) DO NOTHING",
            nativeQuery = true)
    int insertIfNew(@Param("eventKey") String eventKey, @Param("eventType") String eventType,
                     @Param("processedAt") Instant processedAt);
}
