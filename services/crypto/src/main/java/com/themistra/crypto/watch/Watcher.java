package com.themistra.crypto.watch;

import com.themistra.crypto.adapter.ProviderSet;
import com.themistra.crypto.adapter.model.Subscription;
import com.themistra.crypto.adapter.model.TxResult;
import com.themistra.crypto.observation.FactType;
import com.themistra.crypto.observation.ObservationLog;
import com.themistra.crypto.provider.DegradationReason;
import com.themistra.crypto.provider.ProviderHealthTracker;
import com.themistra.crypto.quorum.ProviderAnswer;
import com.themistra.crypto.quorum.QuorumDecisionService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * One instance per actively-watched {@link Watch} (T16). Subscribes to every {@link
 * ProviderSet.NamedAdapter} supplied for the watch's chain, correlates each provider's independently-
 * timed {@code onObservation} callback by {@code txHash}, and once exactly 3 real per-provider answers
 * exist for a fact, evaluates quorum for it exactly once (T16 Phase 3 Finding 1's architecture pivot
 * away from a {@code getTx}-fan-out design - see {@code ObservationSink}'s own Javadoc).
 *
 * <p><b>Exactly 3, never fewer, never fabricated (T16 Phase 4 follow-up correction).</b> {@code
 * QuorumEvaluator} hard-requires exactly 3 non-null answers (verified directly against its source, not
 * assumed) - a fact with fewer than 3 real answers is never evaluated, no matter how long that takes;
 * a provider that hasn't answered within {@code correlationWindowMs} is marked {@code
 * ProviderHealthTracker.recordUnhealthy(..., LAGGING)}, and the fact stays undecided, not forced through
 * with 2 or a fabricated stand-in (unsafe for {@code EXISTENCE}: a {@code Boolean} sentinel would
 * coincidentally match a real answer roughly half the time). {@code ProviderSet} itself now refuses to
 * start at all for a chain with any other configured provider count (Phase 8 Finding 7), so this case
 * is a defence-in-depth backstop, not the primary guard.</p>
 *
 * <p><b>Owns a private, single-thread virtual-thread scheduler (Phase 7/8 Findings 1 and 9, merged).</b>
 * Not a scheduler shared across every {@code Watcher} the registry runs: sharing one meant a single
 * slow/backlogged watcher's sweep could delay every other watch's sweep, and - combined with the
 * original leak (the scheduled sweep future was never captured, so {@link #stop} could never cancel
 * it) - meant a stopped watch's sweep task ran forever on that shared scheduler. Owning the scheduler
 * means {@link #stop} can simply shut the whole thing down.</p>
 */
class Watcher {

    private static final Logger log = LoggerFactory.getLogger(Watcher.class);

    private final Watch watch;
    private final List<ProviderSet.NamedAdapter> adapters;
    private final ObservationLog observationLog;
    private final QuorumDecisionService quorumDecisionService;
    private final ProviderHealthTracker providerHealthTracker;
    private final ChainCursorRepository chainCursorRepository;
    private final long correlationWindowMs;
    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final ScheduledExecutorService sweepScheduler;

    private final Map<String, TxCorrelation> correlations = new ConcurrentHashMap<>();
    private final Set<String> evaluatedFacts = ConcurrentHashMap.newKeySet();
    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();
    private final AtomicReference<Instant> lastObservationAt = new AtomicReference<>();
    private volatile boolean running = false;
    private volatile Gauge lagGauge;
    private volatile ScheduledFuture<?> sweepFuture;

    Watcher(Watch watch, List<ProviderSet.NamedAdapter> adapters, ObservationLog observationLog,
            QuorumDecisionService quorumDecisionService, ProviderHealthTracker providerHealthTracker,
            ChainCursorRepository chainCursorRepository, long correlationWindowMs, MeterRegistry meterRegistry,
            Clock clock) {
        this.watch = watch;
        this.adapters = List.copyOf(adapters);
        this.observationLog = observationLog;
        this.quorumDecisionService = quorumDecisionService;
        this.providerHealthTracker = providerHealthTracker;
        this.chainCursorRepository = chainCursorRepository;
        this.correlationWindowMs = correlationWindowMs;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
        this.sweepScheduler = Executors.newScheduledThreadPool(1, Thread.ofVirtual().factory());
    }

    UUID watchId() {
        return watch.watchId();
    }

    void start() {
        running = true;
        lagGauge = Gauge.builder("crypto.watcher.lag.seconds", lastObservationAt,
                        ref -> ref.get() == null ? 0.0
                                : clock.instant().getEpochSecond() - ref.get().getEpochSecond())
                .tag("chain", watch.chain())
                .tag("address", watch.address())
                .tag("watchId", watch.watchId().toString()) // Phase 8 Finding 5: two watches can
                // legitimately share (chain, address) - T15's own accepted POST non-idempotency risk -
                // without this tag their gauges would collide.
                .register(meterRegistry);

        for (ProviderSet.NamedAdapter namedAdapter : adapters) {
            Subscription subscription = namedAdapter.adapter().subscribeAddress(watch.address(),
                    this::handleObservation);
            subscriptions.add(subscription);
        }
        sweepFuture = sweepScheduler.scheduleWithFixedDelay(this::sweepStaleCorrelations,
                correlationWindowMs, correlationWindowMs, TimeUnit.MILLISECONDS);
    }

    void stop() {
        running = false;
        subscriptions.forEach(Subscription::cancel);
        if (sweepFuture != null) {
            sweepFuture.cancel(false);
        }
        sweepScheduler.shutdownNow();
        if (lagGauge != null) {
            meterRegistry.remove(lagGauge);
        }
    }

    private void handleObservation(String provider, TxResult result, String rawResponseJson) {
        if (!running) {
            // T16 Phase 3 Finding 10: Subscription.cancel() does not guarantee suppression of an
            // observation already in flight - this is the actual enforcement point.
            return;
        }

        try {
            logObservation(provider, result, rawResponseJson);
            lastObservationAt.set(clock.instant());
            recordAnswerAndMaybeEvaluate(provider, result);
            advanceCursorIfNeeded(result.blockNumber());
        } catch (RuntimeException e) {
            // T16 Phase 8 Finding 4: an uncaught exception here would propagate back into the
            // adapter's own scheduleWithFixedDelay task, which silently and permanently cancels all
            // future executions of that subscription (confirmed: EthereumAdapter.pollOnce has no
            // catch-all of its own, unlike TronAdapter's already-guarded pollOnceUnguarded). The
            // watcher's own internal failures must never be able to kill a provider's polling loop.
            log.error("Watcher for watchId={} failed to process an observation from provider={} - "
                    + "continuing, this provider's subscription remains active", watch.watchId(),
                    provider, e);
        }
    }

    /** T16 AC2/L3: called before any quorum evaluation this observation contributes to - one row per
     * applicable fact type, all sharing the same raw JSON. {@code AMOUNT}/{@code TOKEN}/{@code
     * CONFIRMATIONS} are only logged (and later quorum-evaluated) when this provider itself observed
     * the transaction to exist (Phase 3 Finding 4) - {@code TxResult}'s own contract is that those
     * fields carry no meaningful data otherwise. */
    private void logObservation(String provider, TxResult result, String rawResponseJson) {
        observationLog.record(watch.chain(), result.txHash(), provider, FactType.EXISTENCE, rawResponseJson);
        if (result.exists()) {
            observationLog.record(watch.chain(), result.txHash(), provider, FactType.AMOUNT, rawResponseJson);
            observationLog.record(watch.chain(), result.txHash(), provider, FactType.TOKEN, rawResponseJson);
            observationLog.record(watch.chain(), result.txHash(), provider, FactType.CONFIRMATIONS, rawResponseJson);
        }
    }

    private void recordAnswerAndMaybeEvaluate(String provider, TxResult result) {
        TxCorrelation correlation = correlations.computeIfAbsent(result.txHash(),
                key -> new TxCorrelation(clock.instant()));
        correlation.record(provider, result);
        providerHealthTracker.recordHealthy(watch.chain(), provider);

        if (correlation.answers().size() < adapters.size()) {
            return; // still waiting on at least one configured provider
        }

        evaluateFact(result.txHash(), FactType.EXISTENCE, correlation.answers(), TxResult::exists, false);
        evaluateFact(result.txHash(), FactType.AMOUNT, correlation.answers(), TxResult::amount, true);
        evaluateFact(result.txHash(), FactType.TOKEN, correlation.answers(), TxResult::tokenContractAddress, true);
        evaluateFact(result.txHash(), FactType.CONFIRMATIONS, correlation.answers(),
                TxResult::confirmations, true);

        // T16 Phase 8 Finding 2: once every configured provider has answered, no further answer can
        // ever arrive for this transaction - whether or not every fact above actually reached a
        // quorum decision (e.g. a provider reporting exists=false excludes it from AMOUNT/TOKEN/
        // CONFIRMATIONS). The correlation and its evaluatedFacts entries are pruned here, not left to
        // grow the maps without bound for the life of the watch.
        correlations.remove(result.txHash());
        for (FactType factType : FactType.values()) {
            evaluatedFacts.remove(result.txHash() + ":" + factType);
        }
    }

    private <T extends Comparable<T>> void evaluateFact(String txHash, FactType factType,
            Map<String, TxResult> answers, Function<TxResult, T> extractor, boolean requireExists) {
        String evaluationKey = txHash + ":" + factType;
        if (!evaluatedFacts.add(evaluationKey)) {
            return; // already evaluated this fact for this tx (in-memory guard)
        }

        List<ProviderAnswer<T>> providerAnswers = new ArrayList<>();
        for (Map.Entry<String, TxResult> entry : answers.entrySet()) {
            TxResult result = entry.getValue();
            if (requireExists && !result.exists()) {
                continue;
            }
            providerAnswers.add(new ProviderAnswer<>(entry.getKey(), extractor.apply(result)));
        }

        if (providerAnswers.size() != 3) {
            // Fewer than 3 providers actually bear on this fact (e.g. one or more reported
            // exists=false for AMOUNT/TOKEN/CONFIRMATIONS) - QuorumEvaluator hard-requires exactly 3
            // (verified against its source); this fact simply is not decidable. Left marked "evaluated"
            // (not un-marked) because the caller is about to prune this txHash's correlation entirely
            // regardless (every configured provider has already answered - no further answer is ever
            // coming), so there is nothing left to wait for.
            return;
        }

        try {
            quorumDecisionService.evaluate(watch.chain(), txHash, factType, providerAnswers);
            recordDisagreementsIfAny(txHash, providerAnswers);
        } catch (IllegalStateException e) {
            // T16 Phase 3 Finding 5: a duplicate decision already exists (e.g. the in-memory
            // evaluatedFacts guard was lost on restart) - benign, not propagated.
            log.debug("Quorum decision already exists for chain={} txHash={} factType={} - ignoring",
                    watch.chain(), txHash, factType);
        }
    }

    /** Only attributes disagreement when a genuine majority exists (Phase 7/8 Finding 3: {@code
     * QuorumEvaluator}'s own 2-of-3 semantics guarantee {@code agreeingCount >= 2} whenever the
     * outcome is {@code AGREED} - a 3-way {@code HELD} split has no true majority, so none of the three
     * providers is more "correct" than another; {@code HeldFactAlerter}, already invoked inside {@code
     * QuorumDecisionService.evaluate} for every {@code HELD} outcome, is the sole signal for that case).
     * Calls {@code recordDisagreement} at most once per provider per transaction (Phase 8 Finding 8),
     * not once per disagreeing fact - the consecutive-disagreement counter's own meaning is "how many
     * transactions has this provider disagreed on", not "how many fields". */
    private <T extends Comparable<T>> void recordDisagreementsIfAny(String txHash, List<ProviderAnswer<T>> answers) {
        Map<T, Integer> counts = new LinkedHashMap<>();
        for (ProviderAnswer<T> answer : answers) {
            counts.merge(answer.value(), 1, Integer::sum);
        }
        T majorityValue = counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElseThrow();
        int majorityCount = counts.get(majorityValue);
        if (majorityCount == answers.size() || majorityCount < 2) {
            return; // unanimous, or a genuine 3-way split with no true majority - nothing to flag
        }
        TxCorrelation correlation = correlations.get(txHash);
        for (ProviderAnswer<T> answer : answers) {
            if (!answer.value().equals(majorityValue)
                    && (correlation == null || correlation.markDisagreementFlagged(answer.provider()))) {
                providerHealthTracker.recordDisagreement(watch.chain(), answer.provider());
            }
        }
    }

    /** T16 AC6: forward-only, and only after the observation/quorum/health writes for this tick have
     * already completed (Phase 3 Finding 6) - a best-effort progress marker, not a source of truth. */
    private void advanceCursorIfNeeded(long blockNumber) {
        chainCursorRepository.findByWatchId(watch.watchId()).ifPresent(cursor -> {
            cursor.advanceTo(blockNumber, clock.instant());
            chainCursorRepository.save(cursor);
        });
    }

    /** Package-private (not private) so tests can invoke it directly instead of waiting on the real
     * scheduler. A configured provider that has not contributed an answer to a still-open correlation
     * within {@code correlationWindowMs} is marked lagging - the fact itself stays undecided (Phase 4
     * follow-up correction), never forced through with fewer than 3 real answers. Marks a given
     * provider lagging on a given correlation at most once (Phase 8 Finding 10) - a correlation that
     * never completes (one provider permanently unreachable) would otherwise re-flag the same provider,
     * and re-execute the associated repository read, on every sweep window forever. */
    void sweepStaleCorrelations() {
        Instant cutoff = clock.instant().minusMillis(correlationWindowMs);
        for (TxCorrelation correlation : correlations.values()) {
            if (correlation.firstSeenAt().isAfter(cutoff)) {
                continue;
            }
            for (ProviderSet.NamedAdapter namedAdapter : adapters) {
                if (!correlation.answers().containsKey(namedAdapter.providerName())
                        && correlation.markLaggingFlagged(namedAdapter.providerName())) {
                    providerHealthTracker.recordUnhealthy(watch.chain(), namedAdapter.providerName(),
                            DegradationReason.LAGGING);
                }
            }
        }
    }

    private static final class TxCorrelation {
        private final Instant firstSeenAt;
        private final Map<String, TxResult> answers = new ConcurrentHashMap<>();
        private final Set<String> laggingFlagged = ConcurrentHashMap.newKeySet();
        private final Set<String> disagreementFlagged = ConcurrentHashMap.newKeySet();

        TxCorrelation(Instant firstSeenAt) {
            this.firstSeenAt = firstSeenAt;
        }

        void record(String provider, TxResult result) {
            answers.put(provider, result);
        }

        Map<String, TxResult> answers() {
            return answers;
        }

        Instant firstSeenAt() {
            return firstSeenAt;
        }

        /** Returns {@code true} the first time this provider is flagged lagging on this correlation,
         * {@code false} on every subsequent call - a one-shot gate per (correlation, provider). */
        boolean markLaggingFlagged(String provider) {
            return laggingFlagged.add(provider);
        }

        /** Same one-shot-per-(correlation, provider) shape as {@link #markLaggingFlagged}, for the
         * disagreement signal. */
        boolean markDisagreementFlagged(String provider) {
            return disagreementFlagged.add(provider);
        }
    }
}
