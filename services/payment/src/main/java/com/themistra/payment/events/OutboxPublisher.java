package com.themistra.payment.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Objects;

/**
 * The only sanctioned way to emit a domain event (agents.md "Events & messaging"): appends a row
 * in the caller's own transaction (default {@code REQUIRED} propagation joins whatever
 * {@code @Transactional} method is already open), so the state change and the event are
 * atomically consistent — never a dual write. Domain-agnostic on purpose: feature modules own
 * their own payload shapes and call this with a plain object; this class only knows how to
 * serialize and append.
 *
 * <p><b>No duplicate-publish protection at this layer (frozen brief, Phase 3 finding 2).</b>
 * Unlike {@code crypto-service}'s own {@code OutboxPublisher}, this method takes no idempotency
 * key, because this service's own {@code outbox} table has no dedup column at all — that
 * migration is immutable (T02), and this service's own idempotency requirement is scoped to
 * <em>consumed</em> events (L5, via {@code processed_events}, task 11), not published ones. If a
 * future caller invokes {@link #publish} twice for the same logical event, nothing here catches
 * it — correctness depends entirely on each call site firing exactly once per real state
 * transition. Documented here so no later task assumes a dedup guarantee this layer doesn't
 * provide.</p>
 */
@Component
public class OutboxPublisher {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxPublisher(OutboxEventRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public void publish(String aggregateType, String aggregateId, String eventType, Object payload) {
        Objects.requireNonNull(aggregateType, "aggregateType must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
        requireNonBlank(aggregateType, "aggregateType");
        requireNonBlank(aggregateId, "aggregateId");
        requireNonBlank(eventType, "eventType");

        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to serialize event payload for " + aggregateType + "/" + eventType, e);
        }
        repository.save(OutboxEvent.create(aggregateType, aggregateId, eventType, json, clock.instant()));
    }

    private static void requireNonBlank(String value, String paramName) {
        if (value.isBlank()) {
            throw new IllegalArgumentException(paramName + " must not be blank");
        }
    }
}
