package com.themistra.payment.events;

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
 * change it describes (agents.md "Events & messaging" — no dual-write bugs). {@link OutboxRelay}
 * is the only reader; {@code payload} is already-serialized JSON so the relay never needs domain
 * types.
 *
 * <p>Matches {@code V1__payments_baseline.sql}'s own {@code outbox} table exactly — 7 columns,
 * no {@code idempotency_key} (that migration is immutable, T02). Unlike {@code crypto-service}'s
 * own {@code OutboxEvent}, which has a {@code UNIQUE idempotency_key} column, this entity has no
 * dedup column at all: this service's own idempotency requirement (L5, agents.md) is scoped to
 * <em>consumed</em> {@code chain.tx.*} events via a separate {@code processed_events} table
 * (task 11), not to published ones. See {@link OutboxPublisher}'s own Javadoc for what this means
 * in practice.</p>
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
                                      String payloadJson, Instant createdAt) {
        OutboxEvent event = new OutboxEvent();
        event.aggregateType = aggregateType;
        event.aggregateId = aggregateId;
        event.eventType = eventType;
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
     * OutboxPublisher} always sets {@code createdAt} explicitly from its injected
     * {@link java.time.Clock} via {@link #create}; this only fires if some future path ever
     * persists an {@code OutboxEvent} without going through that factory method.
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
