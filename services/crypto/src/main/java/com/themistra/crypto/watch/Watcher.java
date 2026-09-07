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
import java.util.concurrent.ScheduledExecutorService;
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
 * coincidentally match a real answer roughly half the time).</p>
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
    private final Clock clock;
    private final ScheduledExecutorService sweepScheduler;

    private final Map<String, TxCorrelation> correlations = new ConcurrentHashMap<>();
    private final Set<String> evaluatedFacts = ConcurrentHashMap.newKeySet();
    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();
    private final AtomicReference<Instant> lastObservationAt = new AtomicReference<>();
    private volatile boolean running = false;

    Watcher(Watch watch, List<ProviderSet.NamedAdapter> adapters, ObservationLog observationLog,
            QuorumDecisionService quorumDecisionService, ProviderHealthTracker providerHealthTracker,
            ChainCursorRepository chainCursorRepository, long correlationWindowMs, MeterRegistry meterRegistry,
            Clock clock, ScheduledExecutorService sweepScheduler) {
        this.watch = watch;
        this.adapters = List.copyOf(adapters);
        this.observationLog = observationLog;
        this.quorumDecisionService = quorumDecisionService;
        this.providerHealthTracker = providerHealthTracker;
        this.chainCursorRepository = chainCursorRepository;
        this.correlationWindowMs = correlationWindowMs;
        this.clock = clock;
        this.sweepScheduler = sweepScheduler;

        Gauge.builder("crypto.watcher.lag.seconds", lastObservationAt,
                        ref -> ref.get() == null ? 0.0
                                : clock.instant().getEpochSecond() - ref.get().getEpochSecond())
                .tag("chain", watch.chain())
                .tag("address", watch.address())
                .register(meterRegistry);
    }

    UUID watchId() {
        return watch.watchId();
    }

    void start() {
        running = true;
        for (ProviderSet.NamedAdapter namedAdapter : adapters) {
            Subscription subscription = namedAdapter.adapter().subscribeAddress(watch.address(),
                    this::handleObservation);
            subscriptions.add(subscription);
        }
        sweepScheduler.scheduleWithFixedDelay(this::sweepStaleCorrelations,
                correlationWindowMs, correlationWindowMs, TimeUnit.MILLISECONDS);
    }

    void stop() {
        running = false;
        subscriptions.forEach(Subscription::cancel);
    }

    private void handleObservation(String provider, TxResult result, String rawResponseJson) {
        if (!running) {
            // T16 Phase 3 Finding 10: Subscription.cancel() does not guarantee suppression of an
            // observation already in flight - this is the actual enforcement point.
            return;
        }

        logObservation(provider, result, rawResponseJson);
        lastObservationAt.set(clock.instant());
        recordAnswerAndMaybeEvaluate(provider, result);
        advanceCursorIfNeeded(result.blockNumber());
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
            // (verified against its source); this fact simply is not decidable yet. Un-mark it so a
            // later, complete set of answers can still trigger evaluation.
            evaluatedFacts.remove(evaluationKey);
            return;
        }

        try {
            quorumDecisionService.evaluate(watch.chain(), txHash, factType, providerAnswers);
            recordDisagreementsIfAny(providerAnswers);
        } catch (IllegalStateException e) {
            // T16 Phase 3 Finding 5: a duplicate decision already exists (e.g. the in-memory
            // evaluatedFacts guard was lost on restart) - benign, not propagated.
            log.debug("Quorum decision already exists for chain={} txHash={} factType={} - ignoring",
                    watch.chain(), txHash, factType);
        }
    }

    /** Best-effort per-provider disagreement signal (R5): find the majority value and flag whoever
     * doesn't match it. A no-op for a unanimous (3-of-3) answer set. */
    private <T extends Comparable<T>> void recordDisagreementsIfAny(List<ProviderAnswer<T>> answers) {
        Map<T, Integer> counts = new LinkedHashMap<>();
        for (ProviderAnswer<T> answer : answers) {
            counts.merge(answer.value(), 1, Integer::sum);
        }
        T majorityValue = counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElseThrow();
        if (counts.get(majorityValue) == answers.size()) {
            return; // unanimous - nothing to flag
        }
        for (ProviderAnswer<T> answer : answers) {
            if (!answer.value().equals(majorityValue)) {
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
     * follow-up correction), never forced through with fewer than 3 real answers. */
    void sweepStaleCorrelations() {
        Instant cutoff = clock.instant().minusMillis(correlationWindowMs);
        for (TxCorrelation correlation : correlations.values()) {
            if (correlation.firstSeenAt().isAfter(cutoff)) {
                continue;
            }
            for (ProviderSet.NamedAdapter namedAdapter : adapters) {
                if (!correlation.answers().containsKey(namedAdapter.providerName())) {
                    providerHealthTracker.recordUnhealthy(watch.chain(), namedAdapter.providerName(),
                            DegradationReason.LAGGING);
                }
            }
        }
    }

    private static final class TxCorrelation {
        private final Instant firstSeenAt;
        private final Map<String, TxResult> answers = new ConcurrentHashMap<>();

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
    }
}
