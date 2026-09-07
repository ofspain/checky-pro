package com.themistra.crypto.watch;

import com.themistra.crypto.adapter.Chain;
import com.themistra.crypto.adapter.FakeChainAdapter;
import com.themistra.crypto.adapter.ProviderSet;
import com.themistra.crypto.adapter.model.TxResult;
import com.themistra.crypto.observation.FactType;
import com.themistra.crypto.observation.ObservationLog;
import com.themistra.crypto.provider.DegradationReason;
import com.themistra.crypto.provider.ProviderHealthTracker;
import com.themistra.crypto.quorum.QuorumDecisionService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** {@link FakeChainAdapter}'s {@code simulateReorg} is this codebase's only push mechanism into a live
 * {@code ObservationSink} subscription (mirrors {@code FakeChainAdapterTest}'s own established use of
 * it as the general "deliver an observation" mechanic, not only for reorg-labeled scenarios). */
class WatcherTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final String TX_HASH = "0xabc";
    private static final String ADDRESS = "0xwatched";
    private static final String TOKEN = "0xtoken";

    private final FakeChainAdapter providerA = new FakeChainAdapter(Chain.ETHEREUM, "provider-a");
    private final FakeChainAdapter providerB = new FakeChainAdapter(Chain.ETHEREUM, "provider-b");
    private final FakeChainAdapter providerC = new FakeChainAdapter(Chain.ETHEREUM, "provider-c");
    private final List<ProviderSet.NamedAdapter> adapters = List.of(
            new ProviderSet.NamedAdapter("provider-a", providerA),
            new ProviderSet.NamedAdapter("provider-b", providerB),
            new ProviderSet.NamedAdapter("provider-c", providerC));

    private final ObservationLog observationLog = mock(ObservationLog.class);
    private final QuorumDecisionService quorumDecisionService = mock(QuorumDecisionService.class);
    private final ProviderHealthTracker providerHealthTracker = mock(ProviderHealthTracker.class);
    private final ChainCursorRepository chainCursorRepository = mock(ChainCursorRepository.class);
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final MutableClock clock = new MutableClock(NOW);

    private Watch watch;

    @BeforeEach
    void setUp() {
        watch = Watch.register(UUID.randomUUID(), UUID.randomUUID(), "ETHEREUM", ADDRESS, TOKEN,
                BigDecimal.valueOf(1_000_000L), NOW.plus(1, ChronoUnit.DAYS), NOW);
        when(chainCursorRepository.findByWatchId(any())).thenReturn(java.util.Optional.empty());
    }

    private Watcher newWatcher(long correlationWindowMs) {
        return new Watcher(watch, adapters, observationLog, quorumDecisionService, providerHealthTracker,
                chainCursorRepository, correlationWindowMs, meterRegistry, clock);
    }

    private static TxResult tx(boolean exists, long blockNumber, BigDecimal amount, int confirmations) {
        return new TxResult(exists, TX_HASH, "0xfrom", ADDRESS, TOKEN, amount, confirmations, blockNumber);
    }

    private void deliver(FakeChainAdapter provider, TxResult result) {
        provider.simulateReorg(TX_HASH, result);
    }

    // ---------- AC1: exactly-3, corrected ----------

    @Test
    void evaluatesEachFactExactlyOnceOnceAllThreeProvidersAgree() {
        Watcher watcher = newWatcher(60_000);
        watcher.start();
        TxResult agreed = tx(true, 100L, BigDecimal.TEN, 3);

        deliver(providerA, agreed);
        deliver(providerB, agreed);
        deliver(providerC, agreed);

        verify(quorumDecisionService).evaluate(eq("ETHEREUM"), eq(TX_HASH), eq(FactType.EXISTENCE), anyList());
        verify(quorumDecisionService).evaluate(eq("ETHEREUM"), eq(TX_HASH), eq(FactType.AMOUNT), anyList());
        verify(quorumDecisionService).evaluate(eq("ETHEREUM"), eq(TX_HASH), eq(FactType.TOKEN), anyList());
        verify(quorumDecisionService).evaluate(eq("ETHEREUM"), eq(TX_HASH), eq(FactType.CONFIRMATIONS), anyList());
    }

    @Test
    void doesNotEvaluateWithOnlyTwoOfThreeProvidersAnswering() {
        Watcher watcher = newWatcher(60_000);
        watcher.start();
        TxResult agreed = tx(true, 100L, BigDecimal.TEN, 3);

        deliver(providerA, agreed);
        deliver(providerB, agreed);

        verifyNoInteractions(quorumDecisionService);
    }

    @Test
    void laggingProviderNeverForcesEvaluationWithFewerThanThreeRealAnswers() {
        // Phase 4 follow-up correction: advancing the clock past correlationWindowMs (rather than
        // using a 0ms window, which scheduleWithFixedDelay's own contract rejects as an invalid
        // period) makes sweepStaleCorrelations treat the still-incomplete correlation as stale.
        long correlationWindowMs = 60_000;
        Watcher watcher = newWatcher(correlationWindowMs);
        watcher.start();
        TxResult agreed = tx(true, 100L, BigDecimal.TEN, 3);
        deliver(providerA, agreed);
        deliver(providerB, agreed);

        clock.advanceBy(Duration.ofMillis(correlationWindowMs + 1));
        assertThatCode(watcher::sweepStaleCorrelations).doesNotThrowAnyException();

        verifyNoInteractions(quorumDecisionService);
        verify(providerHealthTracker).recordUnhealthy("ETHEREUM", "provider-c", DegradationReason.LAGGING);
    }

    // ---------- AC2: log before decide ----------

    @Test
    void logsEveryProviderObservationBeforeEvaluatingQuorumForTheSameFact() {
        Watcher watcher = newWatcher(60_000);
        watcher.start();
        TxResult agreed = tx(true, 100L, BigDecimal.TEN, 3);

        deliver(providerA, agreed);
        deliver(providerB, agreed);
        deliver(providerC, agreed);

        InOrder order = inOrder(observationLog, quorumDecisionService);
        order.verify(observationLog, times(3)).record(eq("ETHEREUM"), eq(TX_HASH), anyString(),
                eq(FactType.EXISTENCE), anyString());
        order.verify(quorumDecisionService).evaluate(eq("ETHEREUM"), eq(TX_HASH), eq(FactType.EXISTENCE), anyList());
    }

    // ---------- AC3: health signals ----------

    @Test
    void recordsHealthyForEveryProviderThatAnswers() {
        Watcher watcher = newWatcher(60_000);
        watcher.start();
        TxResult agreed = tx(true, 100L, BigDecimal.TEN, 3);

        deliver(providerA, agreed);

        verify(providerHealthTracker).recordHealthy("ETHEREUM", "provider-a");
    }

    @Test
    void recordsDisagreementForTheMinorityProviderInATwoOneSplit() {
        Watcher watcher = newWatcher(60_000);
        watcher.start();
        TxResult majority = tx(true, 100L, BigDecimal.TEN, 3);
        TxResult minority = tx(true, 100L, BigDecimal.ONE, 3);

        deliver(providerA, majority);
        deliver(providerB, majority);
        deliver(providerC, minority);

        verify(providerHealthTracker).recordDisagreement("ETHEREUM", "provider-c");
        verify(providerHealthTracker, never()).recordDisagreement(eq("ETHEREUM"), eq("provider-a"));
        verify(providerHealthTracker, never()).recordDisagreement(eq("ETHEREUM"), eq("provider-b"));
    }

    @Test
    void doesNotRecordDisagreementForAGenuineThreeWaySplit() {
        // Phase 7/8 Finding 3 (corrected): no true majority exists with three distinct values - none
        // of the three providers is flagged.
        Watcher watcher = newWatcher(60_000);
        watcher.start();

        deliver(providerA, tx(true, 100L, BigDecimal.valueOf(1), 3));
        deliver(providerB, tx(true, 100L, BigDecimal.valueOf(2), 3));
        deliver(providerC, tx(true, 100L, BigDecimal.valueOf(3), 3));

        verify(providerHealthTracker, never()).recordDisagreement(anyString(), anyString());
    }

    @Test
    void recordsDisagreementAtMostOncePerProviderPerTransactionEvenWhenMultipleFactsDiverge() {
        // Phase 8 Finding 8: provider-c disagrees on both AMOUNT and CONFIRMATIONS for the same
        // transaction - recordDisagreement must fire once for provider-c, not twice.
        Watcher watcher = newWatcher(60_000);
        watcher.start();

        deliver(providerA, tx(true, 100L, BigDecimal.TEN, 3));
        deliver(providerB, tx(true, 100L, BigDecimal.TEN, 3));
        deliver(providerC, tx(true, 100L, BigDecimal.ONE, 99));

        verify(providerHealthTracker, times(1)).recordDisagreement("ETHEREUM", "provider-c");
    }

    @Test
    void marksALaggingProviderAtMostOncePerCorrelationAcrossRepeatedSweeps() {
        // Phase 8 Finding 10.
        long correlationWindowMs = 60_000;
        Watcher watcher = newWatcher(correlationWindowMs);
        watcher.start();
        deliver(providerA, tx(true, 100L, BigDecimal.TEN, 3));
        clock.advanceBy(Duration.ofMillis(correlationWindowMs + 1));

        watcher.sweepStaleCorrelations();
        watcher.sweepStaleCorrelations();
        watcher.sweepStaleCorrelations();

        verify(providerHealthTracker, times(1))
                .recordUnhealthy("ETHEREUM", "provider-b", DegradationReason.LAGGING);
        verify(providerHealthTracker, times(1))
                .recordUnhealthy("ETHEREUM", "provider-c", DegradationReason.LAGGING);
    }

    // ---------- AC4: exists=false exclusion ----------

    @Test
    void excludesProvidersReportingExistsFalseFromAmountTokenAndConfirmationsButNotExistence() {
        Watcher watcher = newWatcher(60_000);
        watcher.start();

        deliver(providerA, tx(true, 100L, BigDecimal.TEN, 3));
        deliver(providerB, tx(true, 100L, BigDecimal.TEN, 3));
        deliver(providerC, tx(false, 0L, null, 0));

        verify(quorumDecisionService).evaluate(eq("ETHEREUM"), eq(TX_HASH), eq(FactType.EXISTENCE), anyList());
        verify(quorumDecisionService, never())
                .evaluate(eq("ETHEREUM"), eq(TX_HASH), eq(FactType.AMOUNT), anyList());
        verify(quorumDecisionService, never())
                .evaluate(eq("ETHEREUM"), eq(TX_HASH), eq(FactType.TOKEN), anyList());
        verify(quorumDecisionService, never())
                .evaluate(eq("ETHEREUM"), eq(TX_HASH), eq(FactType.CONFIRMATIONS), anyList());
    }

    @Test
    void doesNotLogAmountTokenOrConfirmationsForAProviderReportingExistsFalse() {
        Watcher watcher = newWatcher(60_000);
        watcher.start();

        deliver(providerA, tx(false, 0L, null, 0));

        verify(observationLog).record(eq("ETHEREUM"), eq(TX_HASH), eq("provider-a"), eq(FactType.EXISTENCE), anyString());
        verify(observationLog, never()).record(anyString(), anyString(), anyString(), eq(FactType.AMOUNT), anyString());
        verify(observationLog, never()).record(anyString(), anyString(), anyString(), eq(FactType.TOKEN), anyString());
        verify(observationLog, never()).record(anyString(), anyString(), anyString(), eq(FactType.CONFIRMATIONS), anyString());
    }

    // ---------- AC5: duplicate-decision swallowed ----------

    @Test
    void swallowsADuplicateDecisionExceptionRatherThanPropagatingIt() {
        Watcher watcher = newWatcher(60_000);
        watcher.start();
        when(quorumDecisionService.evaluate(anyString(), anyString(), any(), anyList()))
                .thenThrow(new IllegalStateException("a quorum decision already exists"));

        TxResult agreed = tx(true, 100L, BigDecimal.TEN, 3);
        deliver(providerA, agreed);
        deliver(providerB, agreed);

        assertThatCode(() -> deliver(providerC, agreed)).doesNotThrowAnyException();
    }

    // ---------- AC6: cursor forward-only, ordered after writes ----------

    @Test
    void advancesTheCursorAfterObservationAndQuorumWritesComplete() {
        ChainCursor cursor = ChainCursor.placeholder("ETHEREUM", watch.watchId(), NOW);
        when(chainCursorRepository.findByWatchId(watch.watchId())).thenReturn(java.util.Optional.of(cursor));

        Watcher watcher = newWatcher(60_000);
        watcher.start();
        deliver(providerA, tx(true, 555L, BigDecimal.TEN, 3));

        InOrder order = inOrder(observationLog, chainCursorRepository);
        order.verify(observationLog).record(anyString(), anyString(), anyString(), eq(FactType.EXISTENCE), anyString());
        order.verify(chainCursorRepository).save(cursor);
        assertThat(cursor.lastBlock()).isEqualTo(555L);
    }

    // ---------- Finding 10 (Phase 3): stop() lifecycle ----------

    @Test
    void aCallbackDeliveredAfterStopPerformsNoWork() {
        Watcher watcher = newWatcher(60_000);
        watcher.start();
        watcher.stop();

        deliver(providerA, tx(true, 100L, BigDecimal.TEN, 3));

        verifyNoInteractions(observationLog, quorumDecisionService, providerHealthTracker, chainCursorRepository);
    }

    // ---------- Findings 1/9 (Phase 7/8): resource cleanup on stop() ----------

    @Test
    void removesItsOwnLagGaugeOnStop() {
        Watcher watcher = newWatcher(60_000);
        watcher.start();
        assertThat(meterRegistry.find("crypto.watcher.lag.seconds").gauge()).isNotNull();

        watcher.stop();

        assertThat(meterRegistry.find("crypto.watcher.lag.seconds").gauge()).isNull();
    }

    @Test
    void theLagGaugeIsTaggedWithWatchIdSoTwoWatchesSharingAnAddressDoNotCollide() {
        // Phase 8 Finding 5.
        Watcher watcher = newWatcher(60_000);
        watcher.start();

        assertThat(meterRegistry.find("crypto.watcher.lag.seconds")
                .tag("watchId", watch.watchId().toString())
                .gauge()).isNotNull();
    }

    @Test
    void stopShutsDownItsOwnPrivateSweepScheduler() throws Exception {
        // Phase 11 Finding 1: removesItsOwnLagGaugeOnStop only proves the gauge is deregistered, not
        // that the sweep task/scheduler stop() is supposed to clean up are actually shut down. The
        // scheduler is private with no accessor, so its post-stop state is read via reflection rather
        // than adding test-only production API surface.
        Watcher watcher = newWatcher(60_000);
        watcher.start();

        Field schedulerField = Watcher.class.getDeclaredField("sweepScheduler");
        schedulerField.setAccessible(true);
        ScheduledExecutorService sweepScheduler = (ScheduledExecutorService) schedulerField.get(watcher);
        assertThat(sweepScheduler.isShutdown()).isFalse();

        watcher.stop();

        assertThat(sweepScheduler.isShutdown()).isTrue();
    }

    @Test
    void aFailureLoggingOneProvidersObservationDoesNotPreventTheOtherTwoFromReachingQuorum() {
        // Phase 8 Finding 4 / Phase 11 Finding 2: EthereumAdapter.pollOnce has no catch-all of its
        // own (verified by reading its source at Phase 8), so an exception thrown while processing an
        // observation must never propagate out of the sink callback - it would otherwise permanently
        // cancel that provider's polling subscription. The failing delivery's own answer is lost
        // (handleObservation's try block aborts at the first line), but the adapter naturally retries
        // on its next poll tick (T06/T07's own established behavior) - simulated here by re-delivering
        // the same observation afterward.
        Watcher watcher = newWatcher(60_000);
        watcher.start();
        TxResult agreed = tx(true, 100L, BigDecimal.TEN, 3);
        when(observationLog.record(eq("ETHEREUM"), eq(TX_HASH), eq("provider-a"), eq(FactType.EXISTENCE), anyString()))
                .thenThrow(new IllegalStateException("transient log failure"))
                .thenReturn(null);

        assertThatCode(() -> deliver(providerA, agreed)).doesNotThrowAnyException();
        verifyNoInteractions(quorumDecisionService);

        deliver(providerA, agreed);
        deliver(providerB, agreed);
        deliver(providerC, agreed);

        verify(quorumDecisionService).evaluate(eq("ETHEREUM"), eq(TX_HASH), eq(FactType.EXISTENCE), anyList());
    }

    @Test
    void duplicateObservationsFromTheSameProviderDoNotPrematurelyCompleteACorrelation() {
        // Phase 11 Finding 9: TxCorrelation.answers is a Map keyed by provider name, so a redundant
        // re-delivery from the same provider (e.g. overlapping poll ranges) overwrites its own entry
        // rather than counting as a second distinct answer - it must never let a correlation with only
        // 2 real providers reach the exactly-3 threshold.
        Watcher watcher = newWatcher(60_000);
        watcher.start();
        TxResult agreed = tx(true, 100L, BigDecimal.TEN, 3);

        deliver(providerA, agreed);
        deliver(providerA, agreed);
        verifyNoInteractions(quorumDecisionService);

        deliver(providerB, agreed);
        deliver(providerC, agreed);

        verify(quorumDecisionService).evaluate(eq("ETHEREUM"), eq(TX_HASH), eq(FactType.EXISTENCE), anyList());
        verify(observationLog, times(4)).record(eq("ETHEREUM"), eq(TX_HASH), anyString(), eq(FactType.EXISTENCE), anyString());
    }

    /** A settable {@link Clock} - {@code Clock.fixed} never advances, but the lagging-provider tests
     * need to genuinely move time forward past {@code correlationWindowMs} without relying on {@code
     * scheduleWithFixedDelay}'s own period (which rejects 0 as invalid) to do it automatically. */
    private static final class MutableClock extends Clock {
        private Instant instant;

        MutableClock(Instant initial) {
            this.instant = initial;
        }

        void advanceBy(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
