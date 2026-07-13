package com.themistra.auth.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * The only sanctioned way to emit a domain event (D-009): appends a row in the caller's own
 * transaction (default REQUIRED propagation joins whatever @Transactional method is already
 * open), so the state change and the event are atomically consistent — never a dual write.
 * Domain-agnostic on purpose: feature modules own their own payload shapes and call this with
 * a plain object; this class only knows how to serialize and append.
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
                        int schemaVersion, Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to serialize event payload for " + aggregateType + "/" + eventType, e);
        }
        repository.save(OutboxEvent.create(aggregateType, aggregateId, eventType, schemaVersion, json));
    }
}
