package com.themistra.auth.events;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayTest {

    private static final Instant NOW = Instant.parse("2026-07-13T00:00:00Z");

    @Mock
    private OutboxEventRepository repository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private OutboxRelay relay;

    @BeforeEach
    void setUp() {
        relay = new OutboxRelay(repository, kafkaTemplate, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private OutboxEvent pendingEvent(String aggregateType) {
        return OutboxEvent.create(aggregateType, "acct-1", "user.registered", 1, "{}");
    }

    @Test
    void successfulSendMarksEventPublishedAndPersistsIt() {
        OutboxEvent event = pendingEvent("account");
        when(repository.findByPublishedAtIsNullOrderByCreatedAtAsc(any(Pageable.class)))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send("auth.user.lifecycle", "acct-1", "{}"))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        relay.relay();

        assertThat(event.isPublished()).isTrue();
        assertThat(event.getPublishedAt()).isEqualTo(NOW);
        verify(repository).save(event);
    }

    @Test
    void failedSendLeavesEventUnpublishedForRetry() {
        OutboxEvent event = pendingEvent("account");
        when(repository.findByPublishedAtIsNullOrderByCreatedAtAsc(any(Pageable.class)))
                .thenReturn(List.of(event));
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker unavailable"));
        when(kafkaTemplate.send("auth.user.lifecycle", "acct-1", "{}")).thenReturn(failed);

        relay.relay();

        assertThat(event.isPublished()).isFalse();
        verify(repository, never()).save(any());
    }

    @Test
    void unroutableAggregateTypeIsSkippedWithoutThrowingOrSaving() {
        OutboxEvent event = pendingEvent("unmapped-thing");
        when(repository.findByPublishedAtIsNullOrderByCreatedAtAsc(any(Pageable.class)))
                .thenReturn(List.of(event));

        relay.relay();

        assertThat(event.isPublished()).isFalse();
        verify(kafkaTemplate, never()).send(any(), any(), any());
        verify(repository, never()).save(any());
    }

    @Test
    void emptyBatchIsANoOp() {
        when(repository.findByPublishedAtIsNullOrderByCreatedAtAsc(any(Pageable.class)))
                .thenReturn(List.of());

        relay.relay();

        verify(kafkaTemplate, never()).send(any(), any(), any());
    }
}
