package com.themistra.payment.events;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

/**
 * Phase 11 Gap 5 — AC2 requires null/blank guards and real Jackson serialization; without this
 * test, a future refactor could drop a guard or break the {@code createdAt} wiring with nothing
 * failing. Mirrors {@code crypto-service}'s identical {@code OutboxPublisherTest}, minus the
 * idempotency-key parameter payment's own 4-parameter {@link OutboxPublisher#publish} does not
 * take (this service's own {@code outbox} table has no dedup column - Phase 3/6 disposition).
 */
@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    private static final Instant NOW = Instant.parse("2026-07-13T00:00:00Z");

    private record SamplePayload(String field) {
    }

    @Mock
    private OutboxEventRepository repository;

    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OutboxPublisher(repository, new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void publishSerializesPayloadAndSavesEventWithGivenMetadata() {
        publisher.publish("invoice", "invoice-1", "payments.invoice.created", new SamplePayload("value"));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());

        OutboxEvent saved = captor.getValue();
        assertThat(saved.getAggregateType()).isEqualTo("invoice");
        assertThat(saved.getAggregateId()).isEqualTo("invoice-1");
        assertThat(saved.getEventType()).isEqualTo("payments.invoice.created");
        assertThat(saved.getPayload()).isEqualTo("{\"field\":\"value\"}");
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
        assertThat(saved.isPublished()).isFalse();
    }

    @Test
    void nullAggregateTypeThrows() {
        assertThatThrownBy(() -> publisher.publish(null, "invoice-1", "payments.invoice.created", new SamplePayload("v")))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("aggregateType");
    }

    @Test
    void nullAggregateIdThrows() {
        assertThatThrownBy(() -> publisher.publish("invoice", null, "payments.invoice.created", new SamplePayload("v")))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("aggregateId");
    }

    @Test
    void nullEventTypeThrows() {
        assertThatThrownBy(() -> publisher.publish("invoice", "invoice-1", null, new SamplePayload("v")))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("eventType");
    }

    @Test
    void nullPayloadThrows() {
        assertThatThrownBy(() -> publisher.publish("invoice", "invoice-1", "payments.invoice.created", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("payload");
    }

    @Test
    void blankAggregateTypeThrows() {
        assertThatThrownBy(() -> publisher.publish("  ", "invoice-1", "payments.invoice.created", new SamplePayload("v")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aggregateType");
    }

    @Test
    void blankAggregateIdThrows() {
        assertThatThrownBy(() -> publisher.publish("invoice", "", "payments.invoice.created", new SamplePayload("v")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("aggregateId");
    }

    @Test
    void blankEventTypeThrows() {
        assertThatThrownBy(() -> publisher.publish("invoice", "invoice-1", " ", new SamplePayload("v")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("eventType");
    }

    /** A getter that throws is Jackson's own reliable way to turn any serialization attempt into a
     * real {@code JsonProcessingException} (wrapped as {@code JsonMappingException}), without
     * relying on any special ObjectMapper configuration. */
    private static final class UnserializablePayload {
        public String getBoom() {
            throw new RuntimeException("cannot serialize this field");
        }
    }

    @Test
    void unserializablePayloadWrapsJsonProcessingExceptionAsIllegalStateException() {
        assertThatThrownBy(() -> publisher.publish("invoice", "invoice-1", "payments.invoice.created",
                new UnserializablePayload()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invoice")
                .hasMessageContaining("payments.invoice.created")
                .hasCauseInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
    }
}
