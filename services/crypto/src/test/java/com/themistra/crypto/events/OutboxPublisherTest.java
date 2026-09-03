package com.themistra.crypto.events;

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

/** AC7/AC8 (frozen brief), L5. */
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
    void publishSerializesPayloadAndSavesEventWithGivenMetadataIncludingIdempotencyKey() {
        publisher.publish("tx-seen", "watch-1", "chain.tx.seen", "ETHEREUM:0xabc:seen",
                new SamplePayload("value"));

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(repository).save(captor.capture());

        OutboxEvent saved = captor.getValue();
        assertThat(saved.getAggregateType()).isEqualTo("tx-seen");
        assertThat(saved.getAggregateId()).isEqualTo("watch-1");
        assertThat(saved.getEventType()).isEqualTo("chain.tx.seen");
        assertThat(saved.getIdempotencyKey()).isEqualTo("ETHEREUM:0xabc:seen");
        assertThat(saved.getPayload()).isEqualTo("{\"field\":\"value\"}");
        assertThat(saved.getCreatedAt()).isEqualTo(NOW);
        assertThat(saved.isPublished()).isFalse();
    }

    @Test
    void nullAggregateTypeThrows() {
        assertThatThrownBy(() -> publisher.publish(null, "watch-1", "chain.tx.seen", "k", new SamplePayload("v")))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("aggregateType");
    }

    @Test
    void nullAggregateIdThrows() {
        assertThatThrownBy(() -> publisher.publish("tx-seen", null, "chain.tx.seen", "k", new SamplePayload("v")))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("aggregateId");
    }

    @Test
    void nullEventTypeThrows() {
        assertThatThrownBy(() -> publisher.publish("tx-seen", "watch-1", null, "k", new SamplePayload("v")))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("eventType");
    }

    @Test
    void nullIdempotencyKeyThrows() {
        assertThatThrownBy(() -> publisher.publish("tx-seen", "watch-1", "chain.tx.seen", null, new SamplePayload("v")))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("idempotencyKey");
    }

    @Test
    void nullPayloadThrows() {
        assertThatThrownBy(() -> publisher.publish("tx-seen", "watch-1", "chain.tx.seen", "k", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("payload");
    }
}
