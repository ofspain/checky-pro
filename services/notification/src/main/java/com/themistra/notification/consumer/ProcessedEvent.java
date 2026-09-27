package com.themistra.notification.consumer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Idempotency ledger row (L1, R7/R8) - maps onto {@code notifications.processed_events}, already
 * migrated by T02's own {@code V1__notifications_baseline.sql}. {@code eventKey} is the client-
 * assigned {@code @Id} - the source event's own stable key IS the primary key, unlike
 * {@code services/crypto}'s own {@code OutboxEvent}, which uses a DB-generated surrogate
 * {@code Long}.
 *
 * <p>Read-only in practice: the only write path is
 * {@link ProcessedEventRepository#insertIfNew}, a native {@code INSERT ... ON CONFLICT DO NOTHING}
 * query that binds its parameters directly and never constructs an instance of this class (Kimi
 * Phase 8 Finding #2) - a JPA {@code save} of a constructed entity would reintroduce the
 * transaction-poisoning problem {@link IdempotencyGuard}'s own Javadoc documents. This entity
 * exists for JPA's own mapping/read purposes (inherited {@code existsById}/{@code findById} on
 * {@link ProcessedEventRepository}), populated only via its protected no-arg constructor and
 * Hibernate's own field access.</p>
 */
@Entity
@Table(name = "processed_events", schema = "notifications")
public class ProcessedEvent {

    @Id
    @Column(name = "event_key", nullable = false, length = 200)
    private String eventKey;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    protected ProcessedEvent() {
        // JPA only
    }

    public String getEventKey() {
        return eventKey;
    }

    public String getEventType() {
        return eventType;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
