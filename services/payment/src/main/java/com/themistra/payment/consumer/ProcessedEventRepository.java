package com.themistra.payment.consumer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {

    /** {@code INSERT ... ON CONFLICT DO NOTHING} - not a JPA {@code save}/exists-then-insert, for
     * the exact concurrency reason {@code notification-service}'s own identical
     * {@code ProcessedEventRepository.insertIfNew} documents. Returns the number of rows actually
     * inserted: {@code 1} for a genuinely new key, {@code 0} for a duplicate.
     * {@code clearAutomatically}/{@code flushAutomatically}: a native modifying query bypasses the
     * persistence context entirely, so without clearing it, a caller that read this same
     * {@code event_key} earlier in the same transaction could see stale first-level-cache state
     * on a subsequent read. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "INSERT INTO payments.processed_events (event_key, processed_at) "
            + "VALUES (:eventKey, :processedAt) ON CONFLICT (event_key) DO NOTHING",
            nativeQuery = true)
    int insertIfNew(@Param("eventKey") String eventKey, @Param("processedAt") Instant processedAt);
}
