package com.themistra.crypto.reorg;

import com.themistra.crypto.events.OutboxPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/** AC4 (R12/L5, idempotency key), AC6 (L15, primitive-typed API) - mirrors {@code
 * TxLifecyclePublisherTest}'s (T17) mocked-{@code OutboxPublisher} style. */
@ExtendWith(MockitoExtension.class)
class ReorgDetectorTest {

    private static final Instant NOW = Instant.parse("2026-09-12T00:00:00Z");
    private static final String TX_HASH = "0xabc";
    private static final UUID WATCH_ID = UUID.randomUUID();
    private static final UUID INVOICE_UUID = UUID.randomUUID();

    @Mock
    private OutboxPublisher outboxPublisher;

    private ReorgDetector detector;

    @BeforeEach
    void setUp() {
        detector = new ReorgDetector(outboxPublisher, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void reorgPublishesWithTheTxReorgedAggregateTypeAndWatchIdAsAggregateId() {
        detector.reorg(WATCH_ID, INVOICE_UUID, "ETHEREUM", TX_HASH, "0xtoken");

        verify(outboxPublisher).publish(eq("tx-reorged"), eq(WATCH_ID.toString()),
                eq("chain.tx.reorged"), any(), any());
    }

    @Test
    void reorgBuildsTheDocumentedPayloadShape() {
        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);

        detector.reorg(WATCH_ID, INVOICE_UUID, "ETHEREUM", TX_HASH, "0xtoken");

        verify(outboxPublisher).publish(any(), any(), any(), any(), payloadCaptor.capture());
        ReorgDetector.ReorgedPayload payload = (ReorgDetector.ReorgedPayload) payloadCaptor.getValue();
        assertThat(payload.watchId()).isEqualTo(WATCH_ID);
        assertThat(payload.invoiceUuid()).isEqualTo(INVOICE_UUID);
        assertThat(payload.chain()).isEqualTo("ETHEREUM");
        assertThat(payload.txHash()).isEqualTo(TX_HASH);
        assertThat(payload.tokenContractAddress()).isEqualTo("0xtoken");
        assertThat(payload.occurredAt()).isEqualTo(NOW);
    }

    @Test
    void reorgUsesTheExactDeterministicIdempotencyKeyFormat() {
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);

        detector.reorg(WATCH_ID, INVOICE_UUID, "ETHEREUM", TX_HASH, "0xtoken");

        verify(outboxPublisher).publish(any(), any(), any(), keyCaptor.capture(), any());
        assertThat(keyCaptor.getValue()).isEqualTo("ETHEREUM:" + TX_HASH + ":reorged");
    }

    @Test
    void repeatedCallsForTheSameTransactionProduceTheIdenticalIdempotencyKey() {
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);

        detector.reorg(WATCH_ID, INVOICE_UUID, "ETHEREUM", TX_HASH, "0xtoken");
        detector.reorg(WATCH_ID, INVOICE_UUID, "ETHEREUM", TX_HASH, "0xtoken");

        verify(outboxPublisher, org.mockito.Mockito.times(2))
                .publish(any(), any(), any(), keyCaptor.capture(), any());
        assertThat(keyCaptor.getAllValues().get(0)).isEqualTo(keyCaptor.getAllValues().get(1));
    }

    @Test
    void aDuplicateKeyViolationIsSwallowedRatherThanPropagated() {
        doThrow(new DataIntegrityViolationException("duplicate key"))
                .when(outboxPublisher).publish(any(), any(), any(), any(), any());

        assertThatCode(() -> detector.reorg(WATCH_ID, INVOICE_UUID, "ETHEREUM", TX_HASH, "0xtoken"))
                .doesNotThrowAnyException();
    }

    @Test
    void aNonDuplicateKeyRuntimeExceptionIsNotSwallowed() {
        doThrow(new IllegalStateException("serialization failed"))
                .when(outboxPublisher).publish(any(), any(), any(), any(), any());

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> detector.reorg(WATCH_ID, INVOICE_UUID, "ETHEREUM", TX_HASH, "0xtoken"));
    }

    // ---------- Phase 9 (Kimi Finding #9): null-checks ----------

    @Test
    void reorgRejectsANullWatchId() {
        assertThatNullPointerException().isThrownBy(
                () -> detector.reorg(null, INVOICE_UUID, "ETHEREUM", TX_HASH, "0xtoken"));
    }

    @Test
    void reorgRejectsANullInvoiceUuid() {
        assertThatNullPointerException().isThrownBy(
                () -> detector.reorg(WATCH_ID, null, "ETHEREUM", TX_HASH, "0xtoken"));
    }

    @Test
    void reorgRejectsANullChain() {
        assertThatNullPointerException().isThrownBy(
                () -> detector.reorg(WATCH_ID, INVOICE_UUID, null, TX_HASH, "0xtoken"));
    }

    @Test
    void reorgRejectsANullTxHash() {
        assertThatNullPointerException().isThrownBy(
                () -> detector.reorg(WATCH_ID, INVOICE_UUID, "ETHEREUM", null, "0xtoken"));
    }

    @Test
    void reorgRejectsANullTokenContractAddress() {
        assertThatNullPointerException().isThrownBy(
                () -> detector.reorg(WATCH_ID, INVOICE_UUID, "ETHEREUM", TX_HASH, null));
    }
}
