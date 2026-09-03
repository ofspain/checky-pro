package com.themistra.crypto.provider;

import com.themistra.crypto.events.OutboxPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Emits {@code chain.provider.degraded} (R5) via the only sanctioned publishing path,
 * {@link OutboxPublisher} (agents.md "Events & messaging"). Aggregate type is the literal string
 * {@code "provider"} - already mapped to topic {@code "chain.provider.degraded"} in {@code
 * EventTopics} (T04).
 *
 * <p><b>Idempotency key (L5).</b> {@code "{chain}:{provider}:degraded:{occurredAt}:{UUID}"}. Unlike a
 * {@code chain.tx.*} event (each a one-time-ever transition, so a fixed key suffices), a provider can
 * degrade, recover, and degrade again - a fixed key would collide with itself on the second episode
 * against the outbox's own {@code UNIQUE(idempotency_key)}. The random UUID component (Phase 3 Kimi
 * Issue 7) additionally eliminates any risk of two distinct transitions computed within the same clock
 * tick colliding - mirrors {@code ObservationSnapshotStore.buildKey}'s (T08) identical use of a random
 * UUID for the same purpose. The primary defense against duplicate emission for one episode is {@link
 * ProviderHealthTracker}'s own not-already-unhealthy application-level gate, not key uniqueness.</p>
 *
 * <p><b>Partition key.</b> {@code aggregateId = "{chain}:{provider}"} keeps one provider's health
 * events on one Kafka partition, the same role {@code watchId} plays for {@code chain.tx.*} events.</p>
 */
@Component
public class ProviderDegradedPublisher {

    private final OutboxPublisher outboxPublisher;

    public ProviderDegradedPublisher(OutboxPublisher outboxPublisher) {
        this.outboxPublisher = outboxPublisher;
    }

    public void publish(String chain, String provider, DegradationReason reason, Instant occurredAt) {
        String aggregateId = chain + ":" + provider;
        String idempotencyKey = chain + ":" + provider + ":degraded:" + occurredAt + ":" + UUID.randomUUID();
        outboxPublisher.publish("provider", aggregateId, "chain.provider.degraded", idempotencyKey,
                new Payload(chain, provider, reason, occurredAt));
    }

    /** The event payload shape (Phase 3 Kimi Issue 9) - a concrete implementation for task 23
     * (Contracts) to later formalize into {@code contracts/events/chain/provider-degraded.v1.schema.json},
     * not itself a contract file (out of this task's own scope). */
    public record Payload(String chain, String provider, DegradationReason reason, Instant occurredAt) {
    }
}
