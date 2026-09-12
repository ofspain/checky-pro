package com.themistra.crypto.watch;

import com.themistra.crypto.events.OutboxPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/** AC1/AC2/AC3 (R8/R9/R10), AC4/R12/L5 (idempotency key), AC5 (watchId as aggregateId), AC6 (duplicate
 * publish swallowed) - mirrors {@code ProviderDegradedPublisherTest}'s (T10) mocked-{@code
 * OutboxPublisher} style. */
@ExtendWith(MockitoExtension.class)
class TxLifecyclePublisherTest {

    private static final Instant NOW = Instant.parse("2026-09-12T00:00:00Z");
    private static final String TX_HASH = "0xabc";

    @Mock
    private OutboxPublisher outboxPublisher;

    private TxLifecyclePublisher publisher;
    private Watch watch;

    @BeforeEach
    void setUp() {
        publisher = new TxLifecyclePublisher(outboxPublisher, Clock.fixed(NOW, ZoneOffset.UTC));
        watch = Watch.register(UUID.randomUUID(), UUID.randomUUID(), "ETHEREUM", "0xwatched", "0xtoken",
                BigDecimal.TEN, NOW.plus(1, ChronoUnit.DAYS), NOW);
    }

    // ---------- shouldEmitChainTxSeenOnQuorumAgreedFirstSighting (R8) ----------

    @Test
    void seenPublishesWithTheTxSeenAggregateTypeAndWatchIdAsAggregateId() {
        publisher.seen(watch, TX_HASH, 3);

        verify(outboxPublisher).publish(eq("tx-seen"), eq(watch.watchId().toString()),
                eq("chain.tx.seen"), any(), any());
    }

    @Test
    void seenBuildsTheDocumentedPayloadShape() {
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);

        publisher.seen(watch, TX_HASH, 3);

        verify(outboxPublisher).publish(any(), any(), any(), any(), payloadCaptor.capture());
        TxLifecyclePublisher.SeenPayload payload = (TxLifecyclePublisher.SeenPayload) payloadCaptor.getValue();
        assertThat(payload.watchId()).isEqualTo(watch.watchId());
        assertThat(payload.invoiceUuid()).isEqualTo(watch.invoiceUuid());
        assertThat(payload.chain()).isEqualTo("ETHEREUM");
        assertThat(payload.txHash()).isEqualTo(TX_HASH);
        assertThat(payload.tokenContractAddress()).isEqualTo("0xtoken");
        assertThat(payload.confirmations()).isEqualTo(3);
        assertThat(payload.occurredAt()).isEqualTo(NOW);
    }

    // ---------- shouldEmitChainTxConfirmedWithConfirmationCount (R9) ----------

    @Test
    void confirmedPublishesWithTheTxConfirmedAggregateTypeAndTheAgreedCount() {
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);

        publisher.confirmed(watch, TX_HASH, 42);

        verify(outboxPublisher).publish(eq("tx-confirmed"), eq(watch.watchId().toString()),
                eq("chain.tx.confirmed"), any(), payloadCaptor.capture());
        TxLifecyclePublisher.ConfirmedPayload payload =
                (TxLifecyclePublisher.ConfirmedPayload) payloadCaptor.getValue();
        assertThat(payload.confirmations()).isEqualTo(42);
        assertThat(payload.chain()).isEqualTo("ETHEREUM");
        assertThat(payload.txHash()).isEqualTo(TX_HASH);
    }

    // ---------- shouldEmitChainTxFinalizedOnlyAtPerChainFinality (R10) ----------

    @Test
    void finalizedPublishesWithTheTxFinalizedAggregateTypeAndTheCursorSnapshot() {
        ChainCursor cursor = ChainCursor.placeholder("ETHEREUM", watch.watchId(), NOW);
        cursor.recordSeenTransaction(TX_HASH, BigDecimal.valueOf(12345), "0xfrom", "0xto", NOW);
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);

        publisher.finalized(watch, cursor);

        verify(outboxPublisher).publish(eq("tx-finalized"), eq(watch.watchId().toString()),
                eq("chain.tx.finalized"), any(), payloadCaptor.capture());
        TxLifecyclePublisher.FinalizedPayload payload =
                (TxLifecyclePublisher.FinalizedPayload) payloadCaptor.getValue();
        assertThat(payload.txHash()).isEqualTo(TX_HASH);
        assertThat(payload.tokenContractAddress()).isEqualTo("0xtoken");
        assertThat(payload.amount()).isEqualTo("12345");
        assertThat(payload.fromAddress()).isEqualTo("0xfrom");
        assertThat(payload.toAddress()).isEqualTo("0xto");
    }

    @Test
    void finalizedSerializesAmountAsADecimalStringNeverAJsonNumber() {
        // agents.md: token base units as a decimal string, never a JSON number - the whole reason
        // FinalizedPayload.amount is typed String, not BigDecimal.
        ChainCursor cursor = ChainCursor.placeholder("ETHEREUM", watch.watchId(), NOW);
        cursor.recordSeenTransaction(TX_HASH, new BigDecimal("100000000000000000000"), null, null, NOW);
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);

        publisher.finalized(watch, cursor);

        verify(outboxPublisher).publish(any(), any(), any(), any(), payloadCaptor.capture());
        TxLifecyclePublisher.FinalizedPayload payload =
                (TxLifecyclePublisher.FinalizedPayload) payloadCaptor.getValue();
        assertThat(payload.amount()).isInstanceOf(String.class).isEqualTo("100000000000000000000");
    }

    @Test
    void finalizedToleratesANullAmountOnTheCursor() {
        ChainCursor cursor = ChainCursor.placeholder("ETHEREUM", watch.watchId(), NOW);
        cursor.recordSeenTransaction(TX_HASH, null, null, null, NOW);

        assertThatCode(() -> publisher.finalized(watch, cursor)).doesNotThrowAnyException();
    }

    // ---------- shouldCarryDeterministicIdempotencyKeyOnEveryEmittedEvent (R12/L5) ----------

    @Test
    void everyEventTypeCarriesTheExactDeterministicIdempotencyKeyFormat() {
        ChainCursor cursor = ChainCursor.placeholder("ETHEREUM", watch.watchId(), NOW);
        cursor.recordSeenTransaction(TX_HASH, BigDecimal.TEN, "0xfrom", "0xto", NOW);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);

        publisher.seen(watch, TX_HASH, 1);
        publisher.confirmed(watch, TX_HASH, 2);
        publisher.finalized(watch, cursor);

        verify(outboxPublisher, org.mockito.Mockito.times(3))
                .publish(any(), any(), any(), keyCaptor.capture(), any());
        assertThat(keyCaptor.getAllValues()).containsExactly(
                "ETHEREUM:" + TX_HASH + ":seen",
                "ETHEREUM:" + TX_HASH + ":confirmed",
                "ETHEREUM:" + TX_HASH + ":finalized");
    }

    @Test
    void repeatedCallsForTheSameEventTypeProduceTheIdenticalIdempotencyKey() {
        // Deterministic, not randomized (unlike ProviderDegradedPublisher's key) - a re-delivered
        // observation must reproduce the exact same key, not a fresh one, so the outbox's own unique
        // constraint is what prevents a duplicate emission.
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);

        publisher.seen(watch, TX_HASH, 1);
        publisher.seen(watch, TX_HASH, 1);

        verify(outboxPublisher, org.mockito.Mockito.times(2))
                .publish(any(), any(), any(), keyCaptor.capture(), any());
        assertThat(keyCaptor.getAllValues().get(0)).isEqualTo(keyCaptor.getAllValues().get(1));
    }

    // ---------- AC6: duplicate publish swallowed ----------

    @Test
    void aDuplicateKeyViolationIsSwallowedRatherThanPropagated() {
        doThrow(new DataIntegrityViolationException("duplicate key"))
                .when(outboxPublisher).publish(any(), any(), any(), any(), any());

        assertThatCode(() -> publisher.seen(watch, TX_HASH, 1)).doesNotThrowAnyException();
    }

    @Test
    void aNonDuplicateKeyRuntimeExceptionIsNotSwallowed() {
        doThrow(new IllegalStateException("serialization failed"))
                .when(outboxPublisher).publish(any(), any(), any(), any(), any());

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> publisher.seen(watch, TX_HASH, 1));
    }
}
