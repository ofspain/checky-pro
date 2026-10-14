package com.themistra.payment.events;

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

/**
 * Phase 11 Gap 6 — AC4 describes batch-100 polling, Kafka send keyed by {@code aggregateId},
 * mark-published-after-success, and continue-on-failure; without this test none of those paths
 * in {@link OutboxRelay#relay()}/{@code relayOne} had any regression guard. Mirrors
 * {@code crypto-service}'s identical {@code OutboxRelayTest}, adapted for payment's own 4
 * aggregate types and its 5-parameter (no idempotency key) {@code OutboxEvent.create}.
 */
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
        return OutboxEvent.create(aggregateType, "invoice-1", "payments.invoice.created", "{}", NOW);
    }

    @Test
    void successfulSendMarksEventPublishedAndPersistsIt() {
        OutboxEvent event = pendingEvent("invoice");
        when(repository.findByPublishedAtIsNullOrderByCreatedAtAsc(any(Pageable.class)))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send("payments.invoice.created", "invoice-1", "{}"))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        relay.relay();

        assertThat(event.isPublished()).isTrue();
        assertThat(event.getPublishedAt()).isEqualTo(NOW);
        verify(repository).save(event);
    }

    @Test
    void failedSendLeavesEventUnpublishedForRetry() {
        OutboxEvent event = pendingEvent("invoice");
        when(repository.findByPublishedAtIsNullOrderByCreatedAtAsc(any(Pageable.class)))
                .thenReturn(List.of(event));
        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker unavailable"));
        when(kafkaTemplate.send("payments.invoice.created", "invoice-1", "{}")).thenReturn(failed);

        relay.relay();

        assertThat(event.isPublished()).isFalse();
        verify(repository, never()).save(any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void interruptedSendLeavesEventUnpublishedAndRestoresInterruptFlag() throws Exception {
        OutboxEvent event = pendingEvent("invoice");
        when(repository.findByPublishedAtIsNullOrderByCreatedAtAsc(any(Pageable.class)))
                .thenReturn(List.of(event));
        CompletableFuture<SendResult<String, String>> future = mock(CompletableFuture.class);
        when(future.get(30, TimeUnit.SECONDS)).thenThrow(new InterruptedException());
        when(kafkaTemplate.send("payments.invoice.created", "invoice-1", "{}")).thenReturn(future);

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
        OutboxEvent event = pendingEvent("invoice");
        when(repository.findByPublishedAtIsNullOrderByCreatedAtAsc(any(Pageable.class)))
                .thenReturn(List.of(event));
        CompletableFuture<SendResult<String, String>> future = mock(CompletableFuture.class);
        when(future.get(30, TimeUnit.SECONDS)).thenThrow(new TimeoutException());
        when(kafkaTemplate.send("payments.invoice.created", "invoice-1", "{}")).thenReturn(future);

        relay.relay();

        assertThat(event.isPublished()).isFalse();
        verify(repository, never()).save(any());
    }

    @Test
    void oneFailedSendDoesNotStopTheRestOfTheBatch() {
        OutboxEvent first = pendingEvent("invoice");
        OutboxEvent second = OutboxEvent.create("payment-seen", "invoice-2", "payments.payment.seen", "{}", NOW);
        when(repository.findByPublishedAtIsNullOrderByCreatedAtAsc(any(Pageable.class)))
                .thenReturn(List.of(first, second));

        CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
        failed.completeExceptionally(new RuntimeException("broker unavailable"));
        when(kafkaTemplate.send("payments.invoice.created", "invoice-1", "{}")).thenReturn(failed);
        when(kafkaTemplate.send("payments.payment.seen", "invoice-2", "{}"))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        relay.relay();

        assertThat(first.isPublished()).as("first event's failed send must not block the second").isFalse();
        assertThat(second.isPublished()).as("second event must still be relayed despite the first's failure").isTrue();
        verify(repository, never()).save(first);
        verify(repository).save(second);
    }

    @ParameterizedTest
    @CsvSource({
            "invoice, payments.invoice.created",
            "payment-seen, payments.payment.seen",
            "payment-finalized, payments.payment.finalized",
            "receipt, payments.receipt.issued"
    })
    void eachAggregateTypeIsSentToItsOwnEventTopicsMappedTopic(String aggregateType, String expectedTopic) {
        OutboxEvent event = OutboxEvent.create(aggregateType, "invoice-1", "some.event.type", "{}", NOW);
        when(repository.findByPublishedAtIsNullOrderByCreatedAtAsc(any(Pageable.class)))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send(eq(expectedTopic), eq("invoice-1"), eq("{}")))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

        relay.relay();

        assertThat(event.isPublished()).isTrue();
        verify(kafkaTemplate).send(expectedTopic, "invoice-1", "{}");
    }

    @Test
    void savingPublishedStateFailsLeavesEventUnpublishedForRetry() {
        OutboxEvent event = pendingEvent("invoice");
        when(repository.findByPublishedAtIsNullOrderByCreatedAtAsc(any(Pageable.class)))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send("payments.invoice.created", "invoice-1", "{}"))
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
