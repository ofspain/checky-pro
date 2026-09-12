package com.themistra.crypto.watch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.themistra.crypto.adapter.Chain;
import com.themistra.crypto.adapter.FakeChainAdapter;
import com.themistra.crypto.adapter.ProviderSet;
import com.themistra.crypto.adapter.model.FinalityStatus;
import com.themistra.crypto.adapter.model.TxResult;
import com.themistra.crypto.finality.FinalityPolicy;
import com.themistra.crypto.observation.FactType;
import com.themistra.crypto.observation.ObservationLog;
import com.themistra.crypto.provider.DegradationReason;
import com.themistra.crypto.provider.ProviderHealthTracker;
import com.themistra.crypto.quorum.ProviderAnswer;
import com.themistra.crypto.quorum.QuorumDecision;
import com.themistra.crypto.quorum.QuorumDecisionService;
import com.themistra.crypto.quorum.QuorumOutcome;
import com.themistra.crypto.reorg.ReorgDetector;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
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
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TxLifecyclePublisher txLifecyclePublisher = mock(TxLifecyclePublisher.class);
    private final FinalityPolicy finalityPolicy = mock(FinalityPolicy.class);
    private final ReorgDetector reorgDetector = mock(ReorgDetector.class);

    /** Real background thread, well beyond any of these tests' own lifetime (tests run in
     * milliseconds) - never fires during a test unless a test explicitly calls {@code
     * watcher.pollFinality()} itself (mirrors {@code sweepStaleCorrelations}'s own testability
     * convention: package-private, directly invokable rather than waiting on the real scheduler). */
    private static final long FINALITY_POLL_INTERVAL_MS = 3_600_000L;

    private Watch watch;

    @BeforeEach
    void setUp() {
        watch = Watch.register(UUID.randomUUID(), UUID.randomUUID(), "ETHEREUM", ADDRESS, TOKEN,
                BigDecimal.valueOf(1_000_000L), NOW.plus(1, ChronoUnit.DAYS), NOW);
        when(chainCursorRepository.findByWatchId(any())).thenReturn(java.util.Optional.empty());
        when(finalityPolicy.chain()).thenReturn(Chain.ETHEREUM);
    }

    private Watcher newWatcher(long correlationWindowMs) {
        return new Watcher(watch, adapters, observationLog, quorumDecisionService, providerHealthTracker,
                chainCursorRepository, correlationWindowMs, meterRegistry, clock, objectMapper,
                txLifecyclePublisher, List.of(finalityPolicy), FINALITY_POLL_INTERVAL_MS, reorgDetector);
    }

    private static TxResult tx(boolean exists, long blockNumber, BigDecimal amount, int confirmations) {
        return new TxResult(exists, TX_HASH, "0xfrom", ADDRESS, TOKEN, amount, confirmations, blockNumber);
    }

    private void deliver(FakeChainAdapter provider, TxResult result) {
        provider.simulateReorg(TX_HASH, result);
    }

    private static QuorumDecision agreed(FactType factType) {
        return QuorumDecision.create("ETHEREUM", TX_HASH, factType, QuorumOutcome.AGREED, 3, 3, NOW);
    }

    private static QuorumDecision held(FactType factType) {
        return QuorumDecision.create("ETHEREUM", TX_HASH, factType, QuorumOutcome.HELD, 1, 3, NOW);
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

    // ---------- T17 R8: chain.tx.seen ----------

    @Test
    void shouldEmitChainTxSeenOnQuorumAgreedFirstSighting() {
        ChainCursor cursor = ChainCursor.placeholder("ETHEREUM", watch.watchId(), NOW);
        when(chainCursorRepository.findByWatchId(watch.watchId())).thenReturn(Optional.of(cursor));
        when(quorumDecisionService.evaluate(eq("ETHEREUM"), eq(TX_HASH), eq(FactType.EXISTENCE), anyList()))
                .thenReturn(agreed(FactType.EXISTENCE));
        Watcher watcher = newWatcher(60_000);
        watcher.start();
        TxResult result = tx(true, 100L, BigDecimal.TEN, 3);

        deliver(providerA, result);
        deliver(providerB, result);
        deliver(providerC, result);

        verify(txLifecyclePublisher).seen(eq(watch), eq(TX_HASH), eq(3));
        assertThat(cursor.txHash()).isEqualTo(TX_HASH);
        assertThat(cursor.amount()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(cursor.fromAddress()).isEqualTo("0xfrom");
        assertThat(cursor.toAddress()).isEqualTo(ADDRESS);
    }

    @Test
    void doesNotEmitSeenWhenExistenceIsHeld() {
        when(quorumDecisionService.evaluate(eq("ETHEREUM"), eq(TX_HASH), eq(FactType.EXISTENCE), anyList()))
                .thenReturn(held(FactType.EXISTENCE));
        Watcher watcher = newWatcher(60_000);
        watcher.start();

        deliver(providerA, tx(true, 100L, BigDecimal.TEN, 3));
        deliver(providerB, tx(true, 100L, BigDecimal.ONE, 3));
        deliver(providerC, tx(true, 100L, BigDecimal.valueOf(2), 3));

        verify(txLifecyclePublisher, never()).seen(any(), any(), anyInt());
    }

    @Test
    void doesNotEmitSeenWhenExistenceAgreesFalse() {
        // T17 Phase 9 Finding #10: an AGREED false outcome for EXISTENCE is permanent for this
        // txHash - no event is ever emitted for it.
        when(quorumDecisionService.evaluate(eq("ETHEREUM"), eq(TX_HASH), eq(FactType.EXISTENCE), anyList()))
                .thenReturn(agreed(FactType.EXISTENCE));
        Watcher watcher = newWatcher(60_000);
        watcher.start();
        TxResult doesNotExist = tx(false, 0L, null, 0);

        deliver(providerA, doesNotExist);
        deliver(providerB, doesNotExist);
        deliver(providerC, doesNotExist);

        verify(txLifecyclePublisher, never()).seen(any(), any(), anyInt());
    }

    @Test
    void doesNotEmitSeenWhenExistenceMajorityIsFalseDespiteAMinorityTrueAnswer() {
        // Phase 11 Finding 15: the trivial unanimous-false case doesn't exercise the majorityValue
        // recomputation path handleSeenIfAgreed actually uses.
        when(quorumDecisionService.evaluate(eq("ETHEREUM"), eq(TX_HASH), eq(FactType.EXISTENCE), anyList()))
                .thenReturn(agreed(FactType.EXISTENCE));
        Watcher watcher = newWatcher(60_000);
        watcher.start();

        deliver(providerA, tx(false, 0L, null, 0));
        deliver(providerB, tx(false, 0L, null, 0));
        deliver(providerC, tx(true, 100L, BigDecimal.TEN, 3));

        verify(txLifecyclePublisher, never()).seen(any(), any(), anyInt());
    }

    @Test
    void seenIsEmittedExactlyOnceEvenIfTheAgreeingObservationsAreRedelivered() {
        // Phase 11 Finding 3: AC1's "exactly once" is proven at the evaluatedFacts-guard level by
        // T16's own suite, but not, until now, specifically for the seen() publisher call.
        when(quorumDecisionService.evaluate(eq("ETHEREUM"), eq(TX_HASH), eq(FactType.EXISTENCE), anyList()))
                .thenReturn(agreed(FactType.EXISTENCE))
                .thenThrow(new IllegalStateException("a quorum decision already exists"));
        Watcher watcher = newWatcher(60_000);
        watcher.start();
        TxResult result = tx(true, 100L, BigDecimal.TEN, 3);

        deliver(providerA, result);
        deliver(providerB, result);
        deliver(providerC, result);
        deliver(providerA, result);
        deliver(providerB, result);
        deliver(providerC, result);

        verify(txLifecyclePublisher, times(1)).seen(eq(watch), eq(TX_HASH), eq(3));
    }

    @Test
    void seenSourcesAmountFromTheQuorumMajorityNotAnArbitraryProvider() {
        // T17 Phase 9 (self-review Finding 1 / Kimi Finding 5): two providers agree on 100, one
        // disagrees with 999 - the snapshot must record the majority value, never the minority one,
        // regardless of Map iteration order.
        ChainCursor cursor = ChainCursor.placeholder("ETHEREUM", watch.watchId(), NOW);
        when(chainCursorRepository.findByWatchId(watch.watchId())).thenReturn(Optional.of(cursor));
        when(quorumDecisionService.evaluate(eq("ETHEREUM"), eq(TX_HASH), eq(FactType.EXISTENCE), anyList()))
                .thenReturn(agreed(FactType.EXISTENCE));
        Watcher watcher = newWatcher(60_000);
        watcher.start();

        deliver(providerA, tx(true, 100L, BigDecimal.valueOf(100), 3));
        deliver(providerB, tx(true, 100L, BigDecimal.valueOf(100), 3));
        deliver(providerC, tx(true, 100L, BigDecimal.valueOf(999), 3));

        assertThat(cursor.amount()).isEqualByComparingTo(BigDecimal.valueOf(100));
    }

    @Test
    void logsAWarningAndSkipsFinalityPollingWhenNoChainCursorExistsAtSeenTime() {
        // T17 Phase 9 (self-review Finding 3 / Kimi Finding 7): chainCursorRepository.findByWatchId
        // returns Optional.empty() by default (setUp) - "seen" is still published, but nothing is
        // added to pendingFinality since polling could never write its result back anyway.
        when(quorumDecisionService.evaluate(eq("ETHEREUM"), eq(TX_HASH), eq(FactType.EXISTENCE), anyList()))
                .thenReturn(agreed(FactType.EXISTENCE));
        Watcher watcher = newWatcher(60_000);
        watcher.start();
        TxResult result = tx(true, 100L, BigDecimal.TEN, 3);

        deliver(providerA, result);
        deliver(providerB, result);
        deliver(providerC, result);

        verify(txLifecyclePublisher).seen(eq(watch), eq(TX_HASH), eq(3));
        watcher.pollFinality();
        // Phase 11 Finding 2: the original assertion (no LAGGING recorded) is also true whenever
        // pollFinality has nothing pending at all - strengthened with a direct proof that no
        // provider was ever asked and nothing was logged for FINALITY, i.e. pendingFinality was
        // truly empty, not merely lucky.
        verify(providerHealthTracker, never())
                .recordUnhealthy(eq("ETHEREUM"), anyString(), eq(DegradationReason.LAGGING));
        verify(observationLog, never())
                .record(anyString(), anyString(), anyString(), eq(FactType.FINALITY), anyString());
    }

    // ---------- T17 R9: chain.tx.confirmed (one-shot) ----------

    @Test
    void shouldEmitChainTxConfirmedWithConfirmationCount() {
        when(quorumDecisionService.evaluate(eq("ETHEREUM"), eq(TX_HASH), eq(FactType.CONFIRMATIONS), anyList()))
                .thenReturn(agreed(FactType.CONFIRMATIONS));
        Watcher watcher = newWatcher(60_000);
        watcher.start();
        TxResult result = tx(true, 100L, BigDecimal.TEN, 42);

        deliver(providerA, result);
        deliver(providerB, result);
        deliver(providerC, result);

        verify(txLifecyclePublisher).confirmed(eq(watch), eq(TX_HASH), eq(42));
    }

    @Test
    void doesNotEmitConfirmedWhenConfirmationsIsHeld() {
        when(quorumDecisionService.evaluate(eq("ETHEREUM"), eq(TX_HASH), eq(FactType.CONFIRMATIONS), anyList()))
                .thenReturn(held(FactType.CONFIRMATIONS));
        Watcher watcher = newWatcher(60_000);
        watcher.start();

        deliver(providerA, tx(true, 100L, BigDecimal.TEN, 1));
        deliver(providerB, tx(true, 100L, BigDecimal.TEN, 2));
        deliver(providerC, tx(true, 100L, BigDecimal.TEN, 3));

        verify(txLifecyclePublisher, never()).confirmed(any(), any(), anyInt());
    }

    @Test
    void doesNotEmitConfirmedWhenExistenceHasAMinorityFalseAnswer() {
        // Phase 11 Finding 4: with only 2 of 3 providers reporting exists=true, CONFIRMATIONS never
        // reaches 3 qualifying answers - AC2's "only after EXISTENCE has itself agreed true" made
        // structural, not just asserted.
        Watcher watcher = newWatcher(60_000);
        watcher.start();

        deliver(providerA, tx(true, 100L, BigDecimal.TEN, 5));
        deliver(providerB, tx(true, 100L, BigDecimal.TEN, 5));
        deliver(providerC, tx(false, 0L, null, 0));

        verify(txLifecyclePublisher, never()).confirmed(any(), any(), anyInt());
    }

    @Test
    void confirmedUsesTheMajorityConfirmationCountWhenProvidersDisagree() {
        // Phase 11 Finding 10: the earlier named test used three identical counts.
        when(quorumDecisionService.evaluate(eq("ETHEREUM"), eq(TX_HASH), eq(FactType.CONFIRMATIONS), anyList()))
                .thenReturn(agreed(FactType.CONFIRMATIONS));
        Watcher watcher = newWatcher(60_000);
        watcher.start();

        deliver(providerA, tx(true, 100L, BigDecimal.TEN, 42));
        deliver(providerB, tx(true, 100L, BigDecimal.TEN, 42));
        deliver(providerC, tx(true, 100L, BigDecimal.TEN, 99));

        verify(txLifecyclePublisher).confirmed(eq(watch), eq(TX_HASH), eq(42));
    }

    @Test
    void confirmedIsEmittedExactlyOnceEvenIfTheAgreeingObservationsAreRedelivered() {
        // Phase 11 Finding 3, for CONFIRMATIONS.
        when(quorumDecisionService.evaluate(eq("ETHEREUM"), eq(TX_HASH), eq(FactType.CONFIRMATIONS), anyList()))
                .thenReturn(agreed(FactType.CONFIRMATIONS))
                .thenThrow(new IllegalStateException("a quorum decision already exists"));
        Watcher watcher = newWatcher(60_000);
        watcher.start();
        TxResult result = tx(true, 100L, BigDecimal.TEN, 42);

        deliver(providerA, result);
        deliver(providerB, result);
        deliver(providerC, result);
        deliver(providerA, result);
        deliver(providerB, result);
        deliver(providerC, result);

        verify(txLifecyclePublisher, times(1)).confirmed(eq(watch), eq(TX_HASH), eq(42));
    }

    // ---------- T17 R10: chain.tx.finalized ----------

    private ChainCursor seenCursor() {
        ChainCursor cursor = ChainCursor.placeholder("ETHEREUM", watch.watchId(), NOW);
        when(chainCursorRepository.findByWatchId(watch.watchId())).thenReturn(Optional.of(cursor));
        when(quorumDecisionService.evaluate(eq("ETHEREUM"), eq(TX_HASH), eq(FactType.EXISTENCE), anyList()))
                .thenReturn(agreed(FactType.EXISTENCE));
        return cursor;
    }

    @Test
    void shouldEmitChainTxFinalizedOnlyAtPerChainFinality() {
        ChainCursor cursor = seenCursor();
        Watcher watcher = newWatcher(60_000);
        watcher.start();
        TxResult result = tx(true, 100L, BigDecimal.TEN, 3);
        deliver(providerA, result);
        deliver(providerB, result);
        deliver(providerC, result);

        FinalityStatus notYetFinal = new FinalityStatus(100L, 105L, 90L);
        providerA.scriptFinalityStatus(TX_HASH, notYetFinal);
        providerB.scriptFinalityStatus(TX_HASH, notYetFinal);
        providerC.scriptFinalityStatus(TX_HASH, notYetFinal);
        when(finalityPolicy.isFinal(notYetFinal)).thenReturn(false);

        watcher.pollFinality();
        verify(txLifecyclePublisher, never()).finalized(any(), any());

        FinalityStatus isFinal = new FinalityStatus(100L, 200L, 150L);
        providerA.scriptFinalityStatus(TX_HASH, isFinal);
        providerB.scriptFinalityStatus(TX_HASH, isFinal);
        providerC.scriptFinalityStatus(TX_HASH, isFinal);
        when(finalityPolicy.isFinal(isFinal)).thenReturn(true);
        when(quorumDecisionService.evaluate(eq("ETHEREUM"), eq(TX_HASH), eq(FactType.FINALITY), anyList()))
                .thenReturn(agreed(FactType.FINALITY));

        watcher.pollFinality();

        verify(txLifecyclePublisher).finalized(eq(watch), eq(cursor));
        assertThat(cursor.lastFinalizedBlock()).isEqualTo(150L);
    }

    @Test
    void finalizedIsNeverPublishedBeforeSeenIsPublished() {
        // Phase 11 Finding 1 / Phase 9 self-review Finding 4: the ordering fix (seen() called before
        // pendingFinality.add) is documented but was never actually asserted at the call-order level.
        ChainCursor cursor = seenCursor();
        Watcher watcher = newWatcher(60_000);
        watcher.start();
        TxResult result = tx(true, 100L, BigDecimal.TEN, 3);
        deliver(providerA, result);
        deliver(providerB, result);
        deliver(providerC, result);

        FinalityStatus isFinal = new FinalityStatus(100L, 200L, 150L);
        providerA.scriptFinalityStatus(TX_HASH, isFinal);
        providerB.scriptFinalityStatus(TX_HASH, isFinal);
        providerC.scriptFinalityStatus(TX_HASH, isFinal);
        when(finalityPolicy.isFinal(isFinal)).thenReturn(true);
        when(quorumDecisionService.evaluate(eq("ETHEREUM"), eq(TX_HASH), eq(FactType.FINALITY), anyList()))
                .thenReturn(agreed(FactType.FINALITY));

        watcher.pollFinality();

        InOrder order = inOrder(txLifecyclePublisher);
        order.verify(txLifecyclePublisher).seen(eq(watch), eq(TX_HASH), eq(3));
        order.verify(txLifecyclePublisher).finalized(eq(watch), eq(cursor));
    }

    @Test
    void finalityReachesAgreementUnderTwoOfThreeMajorityAndFlagsTheDisagreeingProvider() {
        // Phase 11 Finding 5: the happy-path finality test uses unanimous answers - R10 only
        // requires 2-of-3 (L1), and R5 requires the disagreeing minority to be flagged.
        seenCursor();
        Watcher watcher = newWatcher(60_000);
        watcher.start();
        TxResult result = tx(true, 100L, BigDecimal.TEN, 3);
        deliver(providerA, result);
        deliver(providerB, result);
        deliver(providerC, result);

        FinalityStatus isFinal = new FinalityStatus(100L, 200L, 150L);
        FinalityStatus notYetFinal = new FinalityStatus(100L, 200L, 90L);
        providerA.scriptFinalityStatus(TX_HASH, isFinal);
        providerB.scriptFinalityStatus(TX_HASH, isFinal);
        providerC.scriptFinalityStatus(TX_HASH, notYetFinal);
        when(finalityPolicy.isFinal(isFinal)).thenReturn(true);
        when(finalityPolicy.isFinal(notYetFinal)).thenReturn(false);
        when(quorumDecisionService.evaluate(eq("ETHEREUM"), eq(TX_HASH), eq(FactType.FINALITY), anyList()))
                .thenReturn(agreed(FactType.FINALITY));

        watcher.pollFinality();

        verify(txLifecyclePublisher).finalized(eq(watch), any(ChainCursor.class));
        verify(providerHealthTracker).recordDisagreement("ETHEREUM", "provider-c");
    }

    @Test
    void finalityPollPassesEachProvidersActualIsFinalValueToQuorumEvaluation() {
        // Phase 11 Finding 6: only anyList() was asserted before - a bug that inverted or hardcoded
        // the boolean mapping would still have passed.
        seenCursor();
        Watcher watcher = newWatcher(60_000);
        watcher.start();
        TxResult result = tx(true, 100L, BigDecimal.TEN, 3);
        deliver(providerA, result);
        deliver(providerB, result);
        deliver(providerC, result);

        FinalityStatus isFinal = new FinalityStatus(100L, 200L, 150L);
        FinalityStatus notYetFinal = new FinalityStatus(100L, 200L, 90L);
        providerA.scriptFinalityStatus(TX_HASH, isFinal);
        providerB.scriptFinalityStatus(TX_HASH, isFinal);
        providerC.scriptFinalityStatus(TX_HASH, notYetFinal);
        when(finalityPolicy.isFinal(isFinal)).thenReturn(true);
        when(finalityPolicy.isFinal(notYetFinal)).thenReturn(false);
        when(quorumDecisionService.evaluate(eq("ETHEREUM"), eq(TX_HASH), eq(FactType.FINALITY), anyList()))
                .thenReturn(agreed(FactType.FINALITY));

        watcher.pollFinality();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ProviderAnswer<Boolean>>> answersCaptor = ArgumentCaptor.forClass(List.class);
        verify(quorumDecisionService).evaluate(eq("ETHEREUM"), eq(TX_HASH), eq(FactType.FINALITY),
                answersCaptor.capture());
        Map<String, Boolean> actual = answersCaptor.getValue().stream()
                .collect(java.util.stream.Collectors.toMap(ProviderAnswer::provider, ProviderAnswer::value));
        assertThat(actual).containsEntry("provider-a", true)
                .containsEntry("provider-b", true)
                .containsEntry("provider-c", false);
    }

    @Test
    void finalityPollRemovesFromPendingWhenADecisionAlreadyExists() {
        // Phase 11 Finding 11: the IllegalStateException catch branch (a decision already exists,
        // e.g. the in-memory guard was lost on restart) was never exercised.
        seenCursor();
        Watcher watcher = newWatcher(60_000);
        watcher.start();
        TxResult result = tx(true, 100L, BigDecimal.TEN, 3);
        deliver(providerA, result);
        deliver(providerB, result);
        deliver(providerC, result);

        FinalityStatus isFinal = new FinalityStatus(100L, 200L, 150L);
        providerA.scriptFinalityStatus(TX_HASH, isFinal);
        providerB.scriptFinalityStatus(TX_HASH, isFinal);
        providerC.scriptFinalityStatus(TX_HASH, isFinal);
        when(finalityPolicy.isFinal(isFinal)).thenReturn(true);
        when(quorumDecisionService.evaluate(eq("ETHEREUM"), eq(TX_HASH), eq(FactType.FINALITY), anyList()))
                .thenThrow(new IllegalStateException("a quorum decision already exists"));

        watcher.pollFinality();
        verify(txLifecyclePublisher, never()).finalized(any(), any());

        // no longer pending: a second tick's pollFinalityFor loop must not touch any provider again -
        // checkForReorg (T18) still runs every tick regardless, since the cursor's txHash is untouched.
        // (1 from the original EXISTENCE-agreeing delivery + 2 from tick 1 [checkForReorg +
        // pollFinalityFor] + 1 from tick 2 [checkForReorg only, pendingFinality is now empty] = 4.)
        watcher.pollFinality();
        verify(providerHealthTracker, times(4)).recordHealthy("ETHEREUM", "provider-a");
    }

    @Test
    void aFreshWatcherInstanceDoesNotResumePollingASeenButNotFinalizedCursor() {
        // Phase 11 Finding 7 / Phase 9 self-review Finding (accepted, documented-only): pendingFinality
        // is in-memory only. This is a disclosed, accepted limitation, not a defect this task fixes -
        // this test locks in the current, intentional behavior so a future change doesn't silently
        // alter it in either direction without notice.
        ChainCursor cursorFromBeforeARestart = ChainCursor.placeholder("ETHEREUM", watch.watchId(), NOW);
        cursorFromBeforeARestart.recordSeenTransaction(TX_HASH, BigDecimal.TEN, "0xfrom", ADDRESS, NOW);
        when(chainCursorRepository.findByWatchId(watch.watchId()))
                .thenReturn(Optional.of(cursorFromBeforeARestart));
        Watcher watcher = newWatcher(60_000); // a brand-new instance, as after a process restart

        watcher.pollFinality();

        verify(observationLog, never())
                .record(anyString(), anyString(), anyString(), eq(FactType.FINALITY), anyString());
    }

    @Test
    void finalityPollSkipsTheTickWhenFewerThanThreeRealAnswersExist() {
        seenCursor();
        Watcher watcher = newWatcher(60_000);
        watcher.start();
        TxResult result = tx(true, 100L, BigDecimal.TEN, 3);
        deliver(providerA, result);
        deliver(providerB, result);
        deliver(providerC, result);

        FinalityStatus status = new FinalityStatus(100L, 200L, 150L);
        providerA.scriptFinalityStatus(TX_HASH, status);
        providerB.scriptFinalityStatus(TX_HASH, status);
        // providerC left unscripted - getFinalityStatus throws, excluded from this tick's answers.
        when(finalityPolicy.isFinal(status)).thenReturn(true);

        watcher.pollFinality();

        verify(quorumDecisionService, never())
                .evaluate(eq("ETHEREUM"), eq(TX_HASH), eq(FactType.FINALITY), anyList());
        verify(providerHealthTracker).recordUnhealthy("ETHEREUM", "provider-c", DegradationReason.LAGGING);
    }

    @Test
    void finalityPollLogsTheRawResponseVerbatimBeforeEvaluatingQuorum() {
        seenCursor();
        Watcher watcher = newWatcher(60_000);
        watcher.start();
        TxResult result = tx(true, 100L, BigDecimal.TEN, 3);
        deliver(providerA, result);
        deliver(providerB, result);
        deliver(providerC, result);

        FinalityStatus status = new FinalityStatus(100L, 200L, 150L);
        providerA.scriptFinalityStatus(TX_HASH, status);
        providerB.scriptFinalityStatus(TX_HASH, status);
        providerC.scriptFinalityStatus(TX_HASH, status);
        when(finalityPolicy.isFinal(status)).thenReturn(true);
        when(quorumDecisionService.evaluate(eq("ETHEREUM"), eq(TX_HASH), eq(FactType.FINALITY), anyList()))
                .thenReturn(agreed(FactType.FINALITY));

        watcher.pollFinality();

        InOrder order = inOrder(observationLog, quorumDecisionService);
        order.verify(observationLog, times(3)).record(eq("ETHEREUM"), eq(TX_HASH), anyString(),
                eq(FactType.FINALITY), anyString());
        order.verify(quorumDecisionService).evaluate(eq("ETHEREUM"), eq(TX_HASH), eq(FactType.FINALITY), anyList());
    }

    @Test
    void finalityRawObservationIncludesTheTxHash() {
        // T17 Phase 9 (self-review Finding 6): toRawJson must embed txHash, matching the adapters'
        // own verbatim-capture convention.
        seenCursor();
        Watcher watcher = newWatcher(60_000);
        watcher.start();
        TxResult result = tx(true, 100L, BigDecimal.TEN, 3);
        deliver(providerA, result);
        deliver(providerB, result);
        deliver(providerC, result);

        FinalityStatus status = new FinalityStatus(100L, 200L, 90L);
        providerA.scriptFinalityStatus(TX_HASH, status);
        providerB.scriptFinalityStatus(TX_HASH, status);
        providerC.scriptFinalityStatus(TX_HASH, status);
        when(finalityPolicy.isFinal(status)).thenReturn(false);

        watcher.pollFinality();

        ArgumentCaptor<String> rawJsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(observationLog, times(3)).record(eq("ETHEREUM"), eq(TX_HASH), anyString(),
                eq(FactType.FINALITY), rawJsonCaptor.capture());
        assertThat(rawJsonCaptor.getAllValues()).allSatisfy(json -> assertThat(json).contains(TX_HASH));
    }

    @Test
    void finalityPollNeverPersistsADecisionWhileTheLocalMajorityIsNotYetFinal() {
        // T17 Phase 10 (test-driven correctness fix): QuorumDecisionService.evaluate can only ever
        // succeed once per (chain, txHash, FINALITY) - calling it on a "not yet final" tick would
        // permanently decide AGREED false and make chain.tx.finalized impossible to ever emit. The
        // majority is checked locally first; evaluate() must never even be attempted while it's false.
        seenCursor();
        Watcher watcher = newWatcher(60_000);
        watcher.start();
        TxResult result = tx(true, 100L, BigDecimal.TEN, 3);
        deliver(providerA, result);
        deliver(providerB, result);
        deliver(providerC, result);

        FinalityStatus status = new FinalityStatus(100L, 200L, 50L);
        providerA.scriptFinalityStatus(TX_HASH, status);
        providerB.scriptFinalityStatus(TX_HASH, status);
        providerC.scriptFinalityStatus(TX_HASH, status);
        when(finalityPolicy.isFinal(status)).thenReturn(false);

        watcher.pollFinality();
        watcher.pollFinality();

        verify(quorumDecisionService, never())
                .evaluate(eq("ETHEREUM"), eq(TX_HASH), eq(FactType.FINALITY), anyList());
        verify(txLifecyclePublisher, never()).finalized(any(), any());
        // still pending: a third tick still queries every provider rather than having given up.
        // (1 from the original EXISTENCE-agreeing delivery + 2 per tick [checkForReorg (T18) +
        // pollFinalityFor] across 3 ticks = 1 + 6 = 7.)
        watcher.pollFinality();
        verify(providerHealthTracker, times(7)).recordHealthy("ETHEREUM", "provider-a");
    }

    @Test
    void finalizedIsWithheldWhenTheCursorSnapshotBelongsToADifferentTransaction() {
        // T17 Phase 9 (Kimi Finding 1): reproduces the disclosed multi-transaction-per-watch
        // limitation producing a stale cursor snapshot - the fix must withhold the event rather than
        // publish a finalized event citing the wrong transaction.
        ChainCursor cursor = ChainCursor.placeholder("ETHEREUM", watch.watchId(), NOW);
        cursor.recordSeenTransaction("0xdifferent-tx", BigDecimal.ONE, "0xother-from", "0xother-to", NOW);
        when(chainCursorRepository.findByWatchId(watch.watchId())).thenReturn(Optional.of(cursor));
        when(quorumDecisionService.evaluate(eq("ETHEREUM"), eq(TX_HASH), eq(FactType.EXISTENCE), anyList()))
                .thenReturn(agreed(FactType.EXISTENCE));
        Watcher watcher = newWatcher(60_000);
        watcher.start();

        TxResult result = tx(true, 100L, BigDecimal.TEN, 3);
        deliver(providerA, result);
        deliver(providerB, result);
        deliver(providerC, result);
        verify(txLifecyclePublisher).seen(eq(watch), eq(TX_HASH), eq(3));

        FinalityStatus status = new FinalityStatus(100L, 200L, 150L);
        providerA.scriptFinalityStatus(TX_HASH, status);
        providerB.scriptFinalityStatus(TX_HASH, status);
        providerC.scriptFinalityStatus(TX_HASH, status);
        when(finalityPolicy.isFinal(status)).thenReturn(true);
        when(quorumDecisionService.evaluate(eq("ETHEREUM"), eq(TX_HASH), eq(FactType.FINALITY), anyList()))
                .thenReturn(agreed(FactType.FINALITY));

        watcher.pollFinality();

        verify(txLifecyclePublisher, never()).finalized(any(), any());
        assertThat(cursor.txHash()).isEqualTo("0xdifferent-tx");
        // Phase 11 Finding 12: withholding the event must not still corrupt the cursor.
        assertThat(cursor.lastFinalizedBlock()).isNull();
    }

    // ---------- T17 Phase 9 (self-review Finding 2 / Kimi Finding 4): fail-fast construction ----------

    @Test
    void constructorFailsFastWhenNoFinalityPolicyIsConfiguredForTheWatchsChain() {
        FinalityPolicy tronOnlyPolicy = mock(FinalityPolicy.class);
        when(tronOnlyPolicy.chain()).thenReturn(Chain.TRON);

        assertThatThrownBy(() -> new Watcher(watch, adapters, observationLog, quorumDecisionService,
                providerHealthTracker, chainCursorRepository, 60_000, meterRegistry, clock, objectMapper,
                txLifecyclePublisher, List.of(tronOnlyPolicy), FINALITY_POLL_INTERVAL_MS, reorgDetector))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ETHEREUM");
    }

    // ---------- T18 R11/L6: chain.tx.reorged ----------

    /** Directly populates a placeholder cursor's snapshot, bypassing the natural observation-delivery
     * flow - the tests below are about {@code checkForReorg}'s own logic, not re-proving T17's own
     * already-exhaustively-tested "how a transaction becomes seen" machinery. */
    private ChainCursor seenCursorFor(String txHash, BigDecimal amount) {
        ChainCursor cursor = ChainCursor.placeholder("ETHEREUM", watch.watchId(), NOW);
        cursor.recordSeenTransaction(txHash, amount, "0xfrom", ADDRESS, NOW);
        when(chainCursorRepository.findByWatchId(watch.watchId())).thenReturn(Optional.of(cursor));
        return cursor;
    }

    @Test
    void shouldEmitChainTxReorgedAndWalkCursorBackwardOnReorg() {
        ChainCursor cursor = seenCursorFor(TX_HASH, BigDecimal.TEN);
        Watcher watcher = newWatcher(60_000);
        watcher.start();
        TxResult reorgedOut = tx(false, 0L, null, 0);
        providerA.scriptTx(TX_HASH, reorgedOut);
        providerB.scriptTx(TX_HASH, reorgedOut);
        providerC.scriptTx(TX_HASH, reorgedOut);

        watcher.pollFinality();

        verify(reorgDetector).reorg(watch.watchId(), watch.invoiceUuid(), watch.chain(), TX_HASH,
                watch.tokenContractAddress());
        assertThat(cursor.txHash()).isNull();
        assertThat(cursor.lastBlock()).isEqualTo(-1L);
        assertThat(cursor.lastFinalizedBlock()).isNull();
        assertThat(cursor.amount()).isNull();
    }

    @Test
    void reorgDiscoveredAfterSeenAloneTriggersReorged() {
        // AC3: no CONFIRMATIONS/FINALITY decided yet for this transaction.
        ChainCursor cursor = seenCursorFor(TX_HASH, BigDecimal.TEN);
        Watcher watcher = newWatcher(60_000);
        watcher.start();
        TxResult reorgedOut = tx(false, 0L, null, 0);
        providerA.scriptTx(TX_HASH, reorgedOut);
        providerB.scriptTx(TX_HASH, reorgedOut);
        providerC.scriptTx(TX_HASH, reorgedOut);

        watcher.pollFinality();

        verify(reorgDetector).reorg(watch.watchId(), watch.invoiceUuid(), watch.chain(), TX_HASH,
                watch.tokenContractAddress());
        assertThat(cursor.txHash()).isNull();
    }

    @Test
    void reorgDiscoveredAfterConfirmedTriggersReorged() {
        // AC3: chain.tx.confirmed already emitted before the reorg is discovered.
        seenCursorFor(TX_HASH, BigDecimal.TEN);
        when(quorumDecisionService.evaluate(eq("ETHEREUM"), eq(TX_HASH), eq(FactType.CONFIRMATIONS), anyList()))
                .thenReturn(agreed(FactType.CONFIRMATIONS));
        Watcher watcher = newWatcher(60_000);
        watcher.start();
        TxResult result = tx(true, 100L, BigDecimal.TEN, 3);
        deliver(providerA, result);
        deliver(providerB, result);
        deliver(providerC, result);
        verify(txLifecyclePublisher).confirmed(eq(watch), eq(TX_HASH), eq(3));

        TxResult reorgedOut = tx(false, 0L, null, 0);
        providerA.scriptTx(TX_HASH, reorgedOut);
        providerB.scriptTx(TX_HASH, reorgedOut);
        providerC.scriptTx(TX_HASH, reorgedOut);

        watcher.pollFinality();

        verify(reorgDetector).reorg(watch.watchId(), watch.invoiceUuid(), watch.chain(), TX_HASH,
                watch.tokenContractAddress());
    }

    @Test
    void reorgDiscoveredAfterFinalizedTriggersReorgedEvenThoughFinalityWasAlreadyDecided() throws Exception {
        // AC3: checkForReorg keys off ChainCursor.txHash(), not pendingFinality membership, so it
        // keeps running even after FINALITY already agreed true and the txHash left pendingFinality.
        ChainCursor cursor = seenCursorFor(TX_HASH, BigDecimal.TEN);
        Watcher watcher = newWatcher(60_000);
        watcher.start();
        // seenCursorFor bypasses the natural handleSeenIfAgreed flow (this test is about checkForReorg,
        // not re-proving how a transaction becomes pending-finality, already covered elsewhere) - seed
        // pendingFinality directly via reflection so pollFinalityFor actually runs this tick.
        Field pendingFinalityField = Watcher.class.getDeclaredField("pendingFinality");
        pendingFinalityField.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Set<String> pendingFinality = (java.util.Set<String>) pendingFinalityField.get(watcher);
        pendingFinality.add(TX_HASH);
        TxResult existsResult = tx(true, 100L, BigDecimal.TEN, 3);
        providerA.scriptTx(TX_HASH, existsResult);
        providerB.scriptTx(TX_HASH, existsResult);
        providerC.scriptTx(TX_HASH, existsResult);

        FinalityStatus isFinal = new FinalityStatus(100L, 200L, 150L);
        providerA.scriptFinalityStatus(TX_HASH, isFinal);
        providerB.scriptFinalityStatus(TX_HASH, isFinal);
        providerC.scriptFinalityStatus(TX_HASH, isFinal);
        when(finalityPolicy.isFinal(isFinal)).thenReturn(true);
        when(quorumDecisionService.evaluate(eq("ETHEREUM"), eq(TX_HASH), eq(FactType.FINALITY), anyList()))
                .thenReturn(agreed(FactType.FINALITY));

        watcher.pollFinality(); // reaches finalized; checkForReorg's own getTx still sees exists=true
        verify(txLifecyclePublisher).finalized(eq(watch), eq(cursor));

        TxResult reorgedOut = tx(false, 0L, null, 0);
        providerA.scriptTx(TX_HASH, reorgedOut);
        providerB.scriptTx(TX_HASH, reorgedOut);
        providerC.scriptTx(TX_HASH, reorgedOut);

        watcher.pollFinality();

        verify(reorgDetector).reorg(watch.watchId(), watch.invoiceUuid(), watch.chain(), TX_HASH,
                watch.tokenContractAddress());
        assertThat(cursor.txHash()).isNull();
    }

    @Test
    void aFreshMajorityStillExistsTrueDoesNotTriggerReorgOrAlterTheCursor() {
        ChainCursor cursor = seenCursorFor(TX_HASH, BigDecimal.TEN);
        Watcher watcher = newWatcher(60_000);
        watcher.start();
        TxResult stillExists = tx(true, 100L, BigDecimal.TEN, 3);
        providerA.scriptTx(TX_HASH, stillExists);
        providerB.scriptTx(TX_HASH, stillExists);
        providerC.scriptTx(TX_HASH, stillExists);

        watcher.pollFinality();

        verify(reorgDetector, never()).reorg(any(), any(), any(), any(), any());
        assertThat(cursor.txHash()).isEqualTo(TX_HASH);
    }

    @Test
    void checkForReorgDeclaresNothingWithFewerThanThreeRealAnswers() {
        ChainCursor cursor = seenCursorFor(TX_HASH, BigDecimal.TEN);
        Watcher watcher = newWatcher(60_000);
        watcher.start();
        TxResult reorgedOut = tx(false, 0L, null, 0);
        providerA.scriptTx(TX_HASH, reorgedOut);
        providerB.scriptTx(TX_HASH, reorgedOut);
        // provider-c intentionally left unscripted for TX_HASH - getTx throws, excluded this tick.

        watcher.pollFinality();

        verify(reorgDetector, never()).reorg(any(), any(), any(), any(), any());
        verify(providerHealthTracker).recordUnhealthy("ETHEREUM", "provider-c", DegradationReason.LAGGING);
        assertThat(cursor.txHash()).isEqualTo(TX_HASH);
    }

    @Test
    void checkForReorgFlagsTheDissentingMinorityProviderStillReportingExistsTrue() {
        ChainCursor cursor = seenCursorFor(TX_HASH, BigDecimal.TEN);
        Watcher watcher = newWatcher(60_000);
        watcher.start();
        TxResult reorgedOut = tx(false, 0L, null, 0);
        TxResult stillExists = tx(true, 100L, BigDecimal.TEN, 3);
        providerA.scriptTx(TX_HASH, reorgedOut);
        providerB.scriptTx(TX_HASH, reorgedOut);
        providerC.scriptTx(TX_HASH, stillExists);

        watcher.pollFinality();

        verify(reorgDetector).reorg(watch.watchId(), watch.invoiceUuid(), watch.chain(), TX_HASH,
                watch.tokenContractAddress());
        verify(providerHealthTracker).recordDisagreement("ETHEREUM", "provider-c");
        assertThat(cursor.txHash()).isNull();
    }

    @Test
    void checkForReorgIsANoOpOnTheTickAfterTheCursorIsAlreadyInvalidated() {
        seenCursorFor(TX_HASH, BigDecimal.TEN);
        Watcher watcher = newWatcher(60_000);
        watcher.start();
        TxResult reorgedOut = tx(false, 0L, null, 0);
        providerA.scriptTx(TX_HASH, reorgedOut);
        providerB.scriptTx(TX_HASH, reorgedOut);
        providerC.scriptTx(TX_HASH, reorgedOut);

        watcher.pollFinality();
        verify(providerHealthTracker, times(1)).recordHealthy("ETHEREUM", "provider-a");
        verify(reorgDetector, times(1)).reorg(any(), any(), any(), any(), any());

        watcher.pollFinality(); // cursor.txHash() is now null - checkForReorg returns immediately

        verify(providerHealthTracker, times(1)).recordHealthy("ETHEREUM", "provider-a"); // unchanged
        verify(reorgDetector, times(1)).reorg(any(), any(), any(), any(), any()); // unchanged
    }

    @Test
    void checkForReorgLogsTheRawResponseVerbatimBeforePublishingTheReorgEvent() {
        // T18 Phase 9 (self-review Finding 1 / Kimi Finding 1, L3).
        seenCursorFor(TX_HASH, BigDecimal.TEN);
        Watcher watcher = newWatcher(60_000);
        watcher.start();
        TxResult reorgedOut = tx(false, 0L, null, 0);
        providerA.scriptTx(TX_HASH, reorgedOut);
        providerB.scriptTx(TX_HASH, reorgedOut);
        providerC.scriptTx(TX_HASH, reorgedOut);

        watcher.pollFinality();

        InOrder order = inOrder(observationLog, reorgDetector);
        order.verify(observationLog, times(3)).record(eq("ETHEREUM"), eq(TX_HASH), anyString(),
                eq(FactType.EXISTENCE), anyString());
        order.verify(reorgDetector).reorg(any(), any(), any(), any(), any());
    }

    @Test
    void anExceptionFromReorgDetectorDoesNotPropagateAndTheNextTickSelfHeals() {
        // T18 Phase 9 (self-review Finding 2/3 / Kimi Finding 2/3): publish-before-invalidate means a
        // failed publish leaves the cursor untouched, so the very next tick naturally retries; the
        // per-call exception guard in pollFinality means the failure itself never propagates.
        ChainCursor cursor = seenCursorFor(TX_HASH, BigDecimal.TEN);
        doThrow(new IllegalStateException("transient outbox failure"))
                .doNothing()
                .when(reorgDetector).reorg(any(), any(), any(), any(), any());
        Watcher watcher = newWatcher(60_000);
        watcher.start();
        TxResult reorgedOut = tx(false, 0L, null, 0);
        providerA.scriptTx(TX_HASH, reorgedOut);
        providerB.scriptTx(TX_HASH, reorgedOut);
        providerC.scriptTx(TX_HASH, reorgedOut);

        assertThatCode(watcher::pollFinality).doesNotThrowAnyException();
        assertThat(cursor.txHash()).isEqualTo(TX_HASH); // NOT invalidated - the throw happened first

        assertThatCode(watcher::pollFinality).doesNotThrowAnyException();

        assertThat(cursor.txHash()).isNull(); // the retry succeeded and invalidated the cursor
        verify(reorgDetector, times(2)).reorg(any(), any(), any(), any(), any());
    }

    @Test
    void checkForReorgAbortsIfTheCursorMovedOnToADifferentTransactionBeforeActing() {
        // T18 Phase 9 (self-review Finding 4 / Kimi Finding 4/10): simulates handleSeenIfAgreed (a
        // different thread) having already moved the cursor on to a different transaction between
        // checkForReorg's initial read and its fresh re-check immediately before acting.
        ChainCursor staleCursor = ChainCursor.placeholder("ETHEREUM", watch.watchId(), NOW);
        staleCursor.recordSeenTransaction(TX_HASH, BigDecimal.TEN, "0xfrom", ADDRESS, NOW);
        ChainCursor movedOnCursor = ChainCursor.placeholder("ETHEREUM", watch.watchId(), NOW);
        movedOnCursor.recordSeenTransaction("0xdifferent-tx", BigDecimal.ONE, "0xother", "0xother2", NOW);
        when(chainCursorRepository.findByWatchId(watch.watchId()))
                .thenReturn(Optional.of(staleCursor))
                .thenReturn(Optional.of(movedOnCursor));
        Watcher watcher = newWatcher(60_000);
        watcher.start();
        TxResult reorgedOut = tx(false, 0L, null, 0);
        providerA.scriptTx(TX_HASH, reorgedOut);
        providerB.scriptTx(TX_HASH, reorgedOut);
        providerC.scriptTx(TX_HASH, reorgedOut);

        watcher.pollFinality();

        verify(reorgDetector, never()).reorg(any(), any(), any(), any(), any());
        assertThat(movedOnCursor.txHash()).isEqualTo("0xdifferent-tx");
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
