package com.themistra.crypto.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Polls unpublished outbox rows and forwards them to Kafka. At-least-once by design: two replicas
 * may occasionally race and both send the same row (no distributed lock here) — every consumer of
 * these events is required to dedupe on the idempotency key (L5), making that a harmless duplicate,
 * not a correctness bug, so no ShedLock is introduced for this job.
 *
 * <p>Send-then-mark ordering is deliberate: a crash between send and mark just means the event is
 * resent next poll (at-least-once). Marking before sending would risk losing an event outright if
 * the process died in between.</p>
 *
 * <p><b>No cross-event ordering guarantee.</b> A failed send is logged and left unpublished for
 * retry, but the loop continues to the next row in the batch rather than halting — a later row for
 * the same aggregate may be delivered before an earlier one that failed and is still pending retry.
 * Consumers must not assume {@code seen → confirmed → finalized} delivery order at the transport
 * level; only dedupe (via the idempotency key) is guaranteed, not ordering.</p>
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH_SIZE = 100;
    private static final long SEND_TIMEOUT_SECONDS = 30;

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

    @Scheduled(fixedDelayString = "${themistra.crypto.outbox.relay-interval-ms:2000}")
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
            // Left unpublished, re-logged and re-fetched on every poll until a code fix mapping
            // this aggregate type is deployed - no quarantine/terminal state exists (T04 scope: the
            // outbox table's shape is fixed verbatim by design.md §4c and carries no such column).
            // No aggregate type this service currently emits is unroutable; this path only fires on
            // a genuine future coding mistake.
            log.error("Unroutable outbox event {} ({}): {}", event.getId(), event.getEventType(), e.getMessage());
            return;
        }

        try {
            kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload())
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            event.markPublished(clock.instant());
            repository.save(event);
        } catch (ExecutionException | TimeoutException e) {
            log.warn("Failed to relay outbox event {} to {}; will retry", event.getId(), topic, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while relaying outbox event {} to {}; will retry", event.getId(), topic, e);
        } catch (DataAccessException e) {
            // Kafka send already succeeded here; only the mark-published save failed. The row
            // stays unpublished and will be resent next poll - a harmless duplicate under the
            // platform's idempotent-consumer/dedupe guarantee (L5), not a lost event.
            log.warn("Failed to persist published state for outbox event {}; will retry", event.getId(), e);
        }
    }
}
