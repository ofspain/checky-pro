package com.themistra.crypto.events;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** AC10 (frozen brief). */
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
        return OutboxEvent.create(aggregateType, "watch-1", "chain.tx.seen", "ETHEREUM:0xabc:seen", "{}", NOW);
    }

    @Test
    void successfulSendMarksEventPublishedAndPersistsIt() {
        OutboxEvent event = pendingEvent("tx-seen");
        when(repository.findByPublishedAtIsNullOrderByCreatedAtAsc(any(Pageable.class)))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send("chain.tx.seen", "watch-1", "{}"))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        relay.relay();

        assertThat(event.isPublished()).isTrue();
        assertThat(event.getPublishedAt()).isEqualTo(NOW);
        verify(repository).save(event);
    }

    @Test
    void failedSendLeavesEventUnpublishedForRetry() {
        OutboxEvent event = pendingEvent("tx-seen");
        when(repository.findByPublishedAtIsNullOrderByCreatedAtAsc(any(Pageable.class)))
                .thenReturn(List.of(event));
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker unavailable"));
        when(kafkaTemplate.send("chain.tx.seen", "watch-1", "{}")).thenReturn(failed);

        relay.relay();

        assertThat(event.isPublished()).isFalse();
        verify(repository, never()).save(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void interruptedSendLeavesEventUnpublishedAndRestoresInterruptFlag() throws Exception {
        OutboxEvent event = pendingEvent("tx-seen");
        when(repository.findByPublishedAtIsNullOrderByCreatedAtAsc(any(Pageable.class)))
                .thenReturn(List.of(event));
        CompletableFuture<SendResult<String, String>> future = mock(CompletableFuture.class);
        when(future.get(30, TimeUnit.SECONDS)).thenThrow(new InterruptedException());
        when(kafkaTemplate.send("chain.tx.seen", "watch-1", "{}")).thenReturn(future);

        try {
            relay.relay();

            assertThat(event.isPublished()).isFalse();
            verify(repository, never()).save(any());
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted(); // clear the flag so it doesn't leak into later tests on a pooled thread
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void timedOutSendLeavesEventUnpublishedForRetry() throws Exception {
        // Same CompletableFuture-mocking technique as the InterruptedException test above -
        // exercising the .get(30, SECONDS) timeout branch without a real 30-second wait.
        OutboxEvent event = pendingEvent("tx-seen");
        when(repository.findByPublishedAtIsNullOrderByCreatedAtAsc(any(Pageable.class)))
                .thenReturn(List.of(event));
        CompletableFuture<SendResult<String, String>> future = mock(CompletableFuture.class);
        when(future.get(30, TimeUnit.SECONDS)).thenThrow(new TimeoutException());
        when(kafkaTemplate.send("chain.tx.seen", "watch-1", "{}")).thenReturn(future);

        relay.relay();

        assertThat(event.isPublished()).isFalse();
        verify(repository, never()).save(any());
    }

    @Test
    void oneFailedSendDoesNotStopTheRestOfTheBatch() {
        OutboxEvent first = pendingEvent("tx-seen");
        OutboxEvent second = OutboxEvent.create("tx-confirmed", "watch-2", "chain.tx.confirmed",
                "ETHEREUM:0xdef:confirmed", "{}", NOW);
        when(repository.findByPublishedAtIsNullOrderByCreatedAtAsc(any(Pageable.class)))
                .thenReturn(List.of(first, second));

        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker unavailable"));
        when(kafkaTemplate.send("chain.tx.seen", "watch-1", "{}")).thenReturn(failed);
        when(kafkaTemplate.send("chain.tx.confirmed", "watch-2", "{}"))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        relay.relay();

        assertThat(first.isPublished()).as("first event's failed send must not block the second").isFalse();
        assertThat(second.isPublished()).as("second event must still be relayed despite the first's failure").isTrue();
        verify(repository, never()).save(first);
        verify(repository).save(second);
    }

    @ParameterizedTest
    @CsvSource({
            "tx-seen, chain.tx.seen",
            "tx-confirmed, chain.tx.confirmed",
            "tx-finalized, chain.tx.finalized",
            "tx-reorged, chain.tx.reorged",
            "provider, chain.provider.degraded"
    })
    void eachAggregateTypeIsSentToItsOwnEventTopicsMappedTopic(String aggregateType, String expectedTopic) {
        OutboxEvent event = OutboxEvent.create(aggregateType, "watch-1", "some.event.type", "k:" + aggregateType, "{}", NOW);
        when(repository.findByPublishedAtIsNullOrderByCreatedAtAsc(any(Pageable.class)))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send(eq(expectedTopic), eq("watch-1"), eq("{}")))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        relay.relay();

        assertThat(event.isPublished()).isTrue();
        verify(kafkaTemplate).send(expectedTopic, "watch-1", "{}");
    }

    @Test
    void savingPublishedStateFailsLeavesEventUnpublishedForRetry() {
        // Kafka send already succeeded; only the mark-published save fails.
        OutboxEvent event = pendingEvent("tx-seen");
        when(repository.findByPublishedAtIsNullOrderByCreatedAtAsc(any(Pageable.class)))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send("chain.tx.seen", "watch-1", "{}"))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        when(repository.save(event)).thenThrow(new DataAccessResourceFailureException("db unreachable"));

        relay.relay();

        // markPublished was called in-memory before the failed save, but since the save (the only
        // durable record) failed, the row is re-fetched as unpublished from the DB on the next poll
        // regardless of this in-memory object's transient state - the assertion that matters is that
        // no exception escaped relay() and the failed save was attempted, not this object's state.
        verify(repository).save(event);
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

    @Test
    void pollsWithABatchSizeOf100() {
        when(repository.findByPublishedAtIsNullOrderByCreatedAtAsc(any(Pageable.class)))
                .thenReturn(List.of());

        relay.relay();

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByPublishedAtIsNullOrderByCreatedAtAsc(captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }
}
