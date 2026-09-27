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
 * {@code Long}. {@link IdempotencyGuard} is the only writer.
 */
@Entity
@Table(name = "processed_events", schema = "notifications")
public class ProcessedEvent {

    @Id
    @Column(name = "event_key", length = 200)
    private String eventKey;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private Instant processedAt;

    protected ProcessedEvent() {
        // JPA only
    }

    public static ProcessedEvent create(String eventKey, String eventType, Instant processedAt) {
        ProcessedEvent event = new ProcessedEvent();
        event.eventKey = eventKey;
        event.eventType = eventType;
        event.processedAt = processedAt;
        return event;
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
