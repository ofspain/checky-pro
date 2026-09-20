package com.themistra.crypto.observation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The named test from package.md §8 (`shouldLogEveryProviderResponseVerbatimToObservationLog`, R4),
 * AC1 (malformed JSON rejected), AC2 (S3-failure does not block persistence), AC4 ("Test ordering" -
 * S3 attempted before the Postgres insert). Fixed {@link Clock} per agents.md; {@link
 * ObservationSnapshotStore}/{@link ObservationRepository} are Mockito mocks - no real S3/Postgres
 * call anywhere in this class (AC6). */
@ExtendWith(MockitoExtension.class)
class ObservationLogTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-09-03T12:00:00Z");

    @Mock
    private ObservationSnapshotStore snapshotStore;
    @Mock
    private ObservationRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Clock fixedClock = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    private ObservationLog observationLog;

    @BeforeEach
    void setUp() {
        observationLog = new ObservationLog(snapshotStore, repository, objectMapper, fixedClock);
    }

    private void stubSuccessfulSave() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void shouldLogEveryProviderResponseVerbatimToObservationLog() {
        String rawJson = "{\"exists\":true,\"blockNumber\":100}";
        when(snapshotStore.store(any(), any(), any(), any(), any(), any())).thenReturn(Optional.of("key.json"));
        stubSuccessfulSave();

        Observation result = observationLog.record("ETHEREUM", "0xabc", "alchemy", FactType.EXISTENCE,
                rawJson);

        assertThat(result.rawResponse()).isEqualTo(rawJson);
        assertThat(result.chain()).isEqualTo("ETHEREUM");
        assertThat(result.txHash()).isEqualTo("0xabc");
        assertThat(result.provider()).isEqualTo("alchemy");
        assertThat(result.factType()).isEqualTo(FactType.EXISTENCE);
    }

    @Test
    void recordAttemptsTheS3WriteBeforeThePostgresInsert() {
        // "Test ordering" (AC4), scoped to this task's own internal ordering (frozen brief amendment
        // #10) - proves the S3 attempt completes before the single Postgres write, not a cross-task
        // claim about QuorumEvaluator (task 9, doesn't exist yet).
        when(snapshotStore.store(any(), any(), any(), any(), any(), any())).thenReturn(Optional.of("key.json"));
        stubSuccessfulSave();

        observationLog.record("ETHEREUM", "0xabc", "alchemy", FactType.EXISTENCE, "{}");

        InOrder order = inOrder(snapshotStore, repository);
        order.verify(snapshotStore).store(any(), any(), any(), any(), any(), any());
        order.verify(repository).save(any());
    }

    @Test
    void recordUsesTheReturnedS3KeyWhenPresent() {
        when(snapshotStore.store(any(), any(), any(), any(), any(), any())).thenReturn(Optional.of("the-real-key.json"));
        ArgumentCaptor<Observation> captor = ArgumentCaptor.forClass(Observation.class);
        when(repository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        observationLog.record("ETHEREUM", "0xabc", "alchemy", FactType.EXISTENCE, "{}");

        assertThat(captor.getValue().s3SnapshotKey()).isEqualTo("the-real-key.json");
    }

    @Test
    void recordPersistsWithNullS3KeyWhenTheSnapshotStoreReturnsEmpty() {
        // AC2: a failed/timed-out S3 write does not block the Postgres insert.
        when(snapshotStore.store(any(), any(), any(), any(), any(), any())).thenReturn(Optional.empty());
        ArgumentCaptor<Observation> captor = ArgumentCaptor.forClass(Observation.class);
        when(repository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        Observation result = observationLog.record("ETHEREUM", "0xabc", "alchemy", FactType.EXISTENCE, "{}");

        assertThat(result.s3SnapshotKey()).isNull();
        assertThat(captor.getValue().s3SnapshotKey()).isNull();
    }

    @Test
    void recordThrowsForMalformedJsonBeforeAttemptingAnyWrite() {
        // AC1: malformed input is rejected before either write is attempted.
        assertThatThrownBy(() -> observationLog.record("ETHEREUM", "0xabc", "alchemy",
                FactType.EXISTENCE, "not valid json"))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(snapshotStore, repository);
    }

    @Test
    void recordUsesTheInjectedClockNotWallClockTime() {
        when(snapshotStore.store(any(), any(), any(), any(), any(), any())).thenReturn(Optional.of("key.json"));
        ArgumentCaptor<Observation> captor = ArgumentCaptor.forClass(Observation.class);
        when(repository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        observationLog.record("ETHEREUM", "0xabc", "alchemy", FactType.EXISTENCE, "{}");

        assertThat(captor.getValue().observedAt()).isEqualTo(FIXED_INSTANT);
    }

    @Test
    void recordPropagatesWhenPostgresInsertFailsAfterASuccessfulS3Write() {
        // Phase 11 Gap 6: proves, rather than assumes, the accepted orphan-S3-object outcome (frozen
        // brief amendment #4) - a DB failure after a successful S3 write must still surface as a
        // clear exception to the caller, not be silently swallowed.
        when(snapshotStore.store(any(), any(), any(), any(), any(), any())).thenReturn(Optional.of("key.json"));
        RuntimeException dbFailure = new RuntimeException("connection refused");
        when(repository.save(any())).thenThrow(dbFailure);

        assertThatThrownBy(() -> observationLog.record("ETHEREUM", "0xabc", "alchemy",
                FactType.EXISTENCE, "{}"))
                .isSameAs(dbFailure);
    }

    @Test
    void recordIsNotAnnotatedTransactional() {
        // Phase 11 Gap 8: regression guard for the Phase 9 fix (Kimi/self-review Issue 2) - a
        // returning @Transactional would once again hold a DB connection open for the S3 call above
        // repository.save(...).
        assertThat(ObservationLog.class.isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class))
                .isFalse();
        java.lang.reflect.Method recordMethod = java.util.Arrays.stream(ObservationLog.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("record"))
                .findFirst()
                .orElseThrow();
        assertThat(recordMethod.isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class))
                .isFalse();
    }
}
