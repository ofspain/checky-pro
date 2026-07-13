package com.themistra.auth.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;

/**
 * Polls unpublished outbox rows and forwards them to Kafka. At-least-once by design: two
 * replicas may occasionally race and both send the same row (no distributed lock here) — the
 * platform-wide rule that every Kafka consumer is idempotent (README, ARCHITECTURE.md) makes
 * that a harmless duplicate, not a correctness bug, so no ShedLock is introduced for this job.
 *
 * Send-then-mark ordering is deliberate: a crash between send and mark just means the event is
 * resent next poll (at-least-once). Marking before sending would risk losing an event outright
 * if the process died in between — a strictly worse failure mode for an event-sourced audit trail.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH_SIZE = 100;

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final Clock clock;

    public OutboxRelay(OutboxEventRepository repository,
                       KafkaTemplate<String, String> kafkaTemplate,
                       Clock clock) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${themistra.auth.outbox.relay-interval-ms:2000}")
    public void relay() {
        List<OutboxEvent> batch =
                repository.findByPublishedAtIsNullOrderByCreatedAtAsc(PageRequest.of(0, BATCH_SIZE));

        for (OutboxEvent event : batch) {
            relayOne(event);
        }
    }

    private void relayOne(OutboxEvent event) {
        String topic;
        try {
            topic = EventTopics.forAggregateType(event.getAggregateType());
        } catch (IllegalStateException e) {
            log.error("Unroutable outbox event {} ({}): {}", event.getId(), event.getEventType(), e.getMessage());
            return; // left unpublished; a config fix + redeploy will pick it up on the next poll
        }

        try {
            kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload()).get();
            event.markPublished(clock.instant());
            repository.save(event);
        } catch (Exception e) {
            log.warn("Failed to relay outbox event {} to {}; will retry", event.getId(), topic, e);
        }
    }
}
