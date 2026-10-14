package com.themistra.payment.consumer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Idempotency ledger row (L5, R13) - maps onto {@code payments.processed_events}, already
 * migrated by T02's own {@code V1__payments_baseline.sql}. {@code eventKey} is the client-
 * assigned {@code @Id} - the source event's own {@code idempotencyKey} field IS the primary key.
 * Only 2 columns here (no {@code event_type}, unlike {@code notification-service}'s own identical
 * table of the same name - a real, confirmed schema difference, not an oversight).
 *
 * <p>Read-only in practice: the only write path is {@link ProcessedEventRepository#insertIfNew},
 * a native {@code INSERT ... ON CONFLICT DO NOTHING} query that binds its parameters directly and
 * never constructs an instance of this class - mirrors {@code notification-service}'s own
 * identical, battle-tested precedent exactly (see that service's own {@code IdempotencyGuard}
 * Javadoc for the full empirically-discovered rationale: a JPA {@code existsById}-then-{@code
 * save} has a real TOCTOU race, and catching a constraint violation around a plain {@code save}
 * still throws {@code UnexpectedRollbackException} regardless of whether it's caught).</p>
 */
@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    @Id
    @Column(name = "event_key", nullable = false, length = 200)
    private String eventKey;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    protected ProcessedEvent() {
        // JPA only
    }

    public String getEventKey() {
        return eventKey;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
