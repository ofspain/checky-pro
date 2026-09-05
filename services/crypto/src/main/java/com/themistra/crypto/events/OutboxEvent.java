package com.themistra.crypto.events;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * One row per domain event awaiting Kafka delivery, written in the same transaction as the state
 * change it describes (agents.md "Events & messaging" — no dual-write bugs). {@link OutboxRelay} is
 * the only reader; {@code payload} is already-serialized JSON so the relay never needs domain types.
 *
 * <p>{@code id} is DB-generated ({@code BIGINT GENERATED ALWAYS AS IDENTITY} per
 * {@code V1__chain_baseline.sql}), not client-assigned — unlike {@code services/auth}'s equivalent
 * entity, which uses a client-assigned {@code UUID}. No {@code schemaVersion}/{@code headers} field
 * exists: {@code outbox}'s shape is fixed (verbatim, immutable) by design.md §4c and carries neither
 * column; schema versioning for this service lives inside the serialized payload only.</p>
 */
@Entity
@Table(name = "outbox")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 128)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "idempotency_key", nullable = false, length = 200, unique = true)
    private String idempotencyKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false)
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    protected OutboxEvent() {
        // JPA only
    }

    public static OutboxEvent create(String aggregateType, String aggregateId, String eventType,
                                      String idempotencyKey, String payloadJson, Instant createdAt) {
        OutboxEvent event = new OutboxEvent();
        event.aggregateType = aggregateType;
        event.aggregateId = aggregateId;
        event.eventType = eventType;
        event.idempotencyKey = idempotencyKey;
        event.payload = payloadJson;
        event.createdAt = createdAt;
        return event;
    }

    public void markPublished(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public boolean isPublished() {
        return publishedAt != null;
    }

    /**
     * Fallback guard only (agents.md: "use java.time with an injectable Clock") — {@link
     * OutboxPublisher} always sets {@code createdAt} explicitly from its injected {@link
     * java.time.Clock} via {@link #create}; this only fires if some future path ever persists an
     * {@code OutboxEvent} without going through that factory method.
     */
    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public String getAggregateId() {
        return aggregateId;
    }

    public String getEventType() {
        return eventType;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }
}
