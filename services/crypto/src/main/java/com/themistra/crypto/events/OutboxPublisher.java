package com.themistra.crypto.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * The only sanctioned way to emit a domain event (agents.md "Events & messaging"): appends a row in
 * the caller's own transaction (default REQUIRED propagation joins whatever {@code @Transactional}
 * method is already open), so the state change and the event are atomically consistent — never a
 * dual write. Domain-agnostic on purpose: feature modules own their own payload shapes and call this
 * with a plain object; this class only knows how to serialize and append.
 *
 * <p><b>Idempotency key (L5).</b> {@code idempotencyKey} is a required parameter with no
 * defaulting — every emitted event must carry the deterministic key {@code chain:txhash:eventtype}
 * so consumers can dedupe. A duplicate key propagates as an unchecked
 * {@code DataIntegrityViolationException} from the database's {@code UNIQUE} constraint; this method
 * does not catch, dedupe, or silently swallow it — callers must not call {@code publish} twice with
 * the same logical key.</p>
 *
 * <p><b>Partition-key convention.</b> For {@code chain.tx.*} events, callers MUST pass the watch's
 * {@code watchId} as {@code aggregateId} — {@link OutboxRelay} uses {@code aggregateId} as the Kafka
 * message key, and design.md §4c fixes {@code watchId} as the partition key for these events.</p>
 */
@Component
public class OutboxPublisher {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public OutboxPublisher(OutboxEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void publish(String aggregateType, String aggregateId, String eventType,
                         String idempotencyKey, Object payload) {
        Objects.requireNonNull(aggregateType, "aggregateType must not be null");
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        Objects.requireNonNull(payload, "payload must not be null");

        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to serialize event payload for " + aggregateType + "/" + eventType, e);
        }
        repository.save(OutboxEvent.create(aggregateType, aggregateId, eventType, idempotencyKey, json));
    }
}
