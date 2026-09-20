package com.themistra.crypto.watch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.themistra.crypto.adapter.Chain;
import com.themistra.crypto.adapter.ProviderSet;
import com.themistra.crypto.adapter.model.FinalityStatus;
import com.themistra.crypto.adapter.model.Subscription;
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
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import java.util.stream.Collectors;

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
    private final ObjectMapper objectMapper;
    private final TxLifecyclePublisher txLifecyclePublisher;
    private final FinalityPolicy finalityPolicy;
    private final long finalityPollIntervalMs;
    private final ReorgDetector reorgDetector;

    private final Map<String, TxCorrelation> correlations = new ConcurrentHashMap<>();
    private final Set<String> evaluatedFacts = ConcurrentHashMap.newKeySet();
    private final Set<String> pendingFinality = ConcurrentHashMap.newKeySet();
    private final List<Subscription> subscriptions = new CopyOnWriteArrayList<>();
    private final AtomicReference<Instant> lastObservationAt = new AtomicReference<>();
    private volatile boolean running = false;
    private volatile Gauge lagGauge;
    private volatile ScheduledFuture<?> sweepFuture;
    private volatile ScheduledFuture<?> finalityPollFuture;

    Watcher(Watch watch, List<ProviderSet.NamedAdapter> adapters, ObservationLog observationLog,
            QuorumDecisionService quorumDecisionService, ProviderHealthTracker providerHealthTracker,
            ChainCursorRepository chainCursorRepository, long correlationWindowMs, MeterRegistry meterRegistry,
            Clock clock, ObjectMapper objectMapper, TxLifecyclePublisher txLifecyclePublisher,
            List<FinalityPolicy> finalityPolicies, long finalityPollIntervalMs, ReorgDetector reorgDetector) {
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
        this.objectMapper = objectMapper;
        this.txLifecyclePublisher = txLifecyclePublisher;
        Chain chain = Chain.valueOf(watch.chain());
        this.finalityPolicy = finalityPolicies.stream()
                .collect(Collectors.toMap(FinalityPolicy::chain, Function.identity()))
                .get(chain);
        // T17 Phase 9 (self-review Finding 2 / Kimi Finding 4): a missing policy must fail loudly at
        // construction, not surface as an endless, misleading "every provider is lagging" signal (the
        // NullPointerException it would otherwise throw inside pollFinalityFor's per-provider
        // try/catch is a RuntimeException, indistinguishable from a real transport failure). Mirrors
        // ProviderSet's own eager validation (T16 Phase 8 Finding 7).
        if (this.finalityPolicy == null) {
            throw new IllegalStateException(
                    "No FinalityPolicy configured for chain " + chain + " (watchId=" + watch.watchId() + ")");
        }
        this.finalityPollIntervalMs = finalityPollIntervalMs;
        this.reorgDetector = reorgDetector;
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
        // T17: shares this Watcher's own scheduler rather than a second thread pool (frozen brief).
        finalityPollFuture = sweepScheduler.scheduleWithFixedDelay(this::pollFinality,
                finalityPollIntervalMs, finalityPollIntervalMs, TimeUnit.MILLISECONDS);
    }

    void stop() {
        running = false;
        subscriptions.forEach(Subscription::cancel);
        if (sweepFuture != null) {
            sweepFuture.cancel(false);
        }
        if (finalityPollFuture != null) {
            finalityPollFuture.cancel(false);
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

        QuorumDecision existenceDecision = evaluateFact(result.txHash(), FactType.EXISTENCE,
                correlation.answers(), TxResult::exists, false);
        evaluateFact(result.txHash(), FactType.AMOUNT, correlation.answers(), TxResult::amount, true);
        evaluateFact(result.txHash(), FactType.TOKEN, correlation.answers(), TxResult::tokenContractAddress, true);
        QuorumDecision confirmationsDecision = evaluateFact(result.txHash(), FactType.CONFIRMATIONS,
                correlation.answers(), TxResult::confirmations, true);

        // T17 R8/R9: must run before the correlation is pruned below - handleSeenIfAgreed/
        // handleConfirmedIfAgreed read the still-populated correlation.answers() to source the
        // durable ChainCursor snapshot and the agreed confirmation count.
        handleSeenIfAgreed(result.txHash(), existenceDecision, correlation.answers());
        handleConfirmedIfAgreed(result.txHash(), confirmationsDecision, correlation.answers());

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

    /** @return the persisted {@link QuorumDecision}, or {@code null} if this call did not decide
     *     anything - either this fact was already evaluated for this tx (in-memory guard, T16), fewer
     *     than 3 providers actually bear on it, or a duplicate-decision {@link IllegalStateException}
     *     was caught (T16 Phase 3 Finding 5). T17 adds the return value so callers can react to a
     *     freshly-made {@code AGREED} decision (R8/R9) - {@code QuorumDecision} itself carries no
     *     agreed *value*, only the outcome and counts, so {@code answers} must still be consulted by
     *     the caller for the actual value. */
    private <T extends Comparable<T>> QuorumDecision evaluateFact(String txHash, FactType factType,
            Map<String, TxResult> answers, Function<TxResult, T> extractor, boolean requireExists) {
        String evaluationKey = txHash + ":" + factType;
        if (!evaluatedFacts.add(evaluationKey)) {
            return null; // already evaluated this fact for this tx (in-memory guard)
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
            return null;
        }

        try {
            QuorumDecision decision = quorumDecisionService.evaluate(watch.chain(), txHash, factType,
                    providerAnswers);
            recordDisagreementsIfAny(txHash, providerAnswers);
            return decision;
        } catch (IllegalStateException e) {
            // T16 Phase 3 Finding 5: a duplicate decision already exists (e.g. the in-memory
            // evaluatedFacts guard was lost on restart) - benign, not propagated.
            log.debug("Quorum decision already exists for chain={} txHash={} factType={} - ignoring",
                    watch.chain(), txHash, factType);
            return null;
        }
    }

    /** R8: no event unless {@code EXISTENCE} was just, freshly decided {@code AGREED} in this call.
     * An {@code AGREED false} outcome (T17 Finding #10) is permanent for this {@code txHash} - the
     * pre-existing, unmodified T16 exactly-once-per-fact design means {@code EXISTENCE} can never be
     * re-decided, so no lifecycle event is ever emitted for it either. */
    private void handleSeenIfAgreed(String txHash, QuorumDecision existenceDecision,
                                     Map<String, TxResult> answers) {
        if (existenceDecision == null || existenceDecision.outcome() != QuorumOutcome.AGREED) {
            return;
        }
        boolean agreedExists = majorityValue(answers.values().stream().map(TxResult::exists).toList());
        if (!agreedExists) {
            return;
        }
        List<TxResult> existingAnswers = answers.values().stream().filter(TxResult::exists).toList();
        // T17 Phase 9 (self-review Finding 1 / Kimi Finding 5): amount must be the quorum-majority
        // value, not an arbitrary agreeing provider's own answer - a Map's iteration order could
        // otherwise pick a minority, disagreeing provider's amount. fromAddress/toAddress have no
        // independent quorum fact of their own, so the provider that supplied the majority amount is
        // used as the representative for them too, rather than an unrelated arbitrary pick.
        BigDecimal agreedAmount = majorityValue(existingAnswers.stream().map(TxResult::amount).toList());
        TxResult representative = existingAnswers.stream()
                .filter(result -> agreedAmount.equals(result.amount()))
                .findFirst()
                .orElseThrow();
        int agreedConfirmations = majorityValue(existingAnswers.stream().map(TxResult::confirmations).toList());

        Optional<ChainCursor> cursor = chainCursorRepository.findByWatchId(watch.watchId());
        if (cursor.isEmpty()) {
            // T17 Phase 9 (self-review Finding 3 / Kimi Finding 7): this should never happen in
            // practice (T15 creates the placeholder row at watch registration) - surfaced loudly
            // rather than silently emitting "seen" with no durable snapshot and no way to ever emit
            // "finalized" later.
            log.warn("No ChainCursor found for watchId={} chain={} - chain.tx.seen will be emitted but "
                    + "the durable snapshot could not be recorded and finality polling cannot start "
                    + "for txHash={}", watch.watchId(), watch.chain(), txHash);
        } else {
            cursor.get().recordSeenTransaction(txHash, agreedAmount, representative.fromAddress(),
                    representative.toAddress(), clock.instant());
            chainCursorRepository.save(cursor.get());
        }

        // T17 Phase 9 (self-review Finding 4 / Kimi Finding 8): published before pendingFinality.add
        // so the "seen" outbox row's created_at is guaranteed to precede any possible "finalized" row
        // for the same transaction - pollFinality runs on a separate thread and could otherwise race
        // ahead if the add happened first.
        txLifecyclePublisher.seen(watch, txHash, agreedConfirmations);
        if (cursor.isPresent()) {
            pendingFinality.add(txHash);
        }
    }

    /** R9 (one-shot, T17 frozen brief): no event unless {@code CONFIRMATIONS} was just, freshly
     * decided {@code AGREED} in this call. */
    private void handleConfirmedIfAgreed(String txHash, QuorumDecision confirmationsDecision,
                                          Map<String, TxResult> answers) {
        if (confirmationsDecision == null || confirmationsDecision.outcome() != QuorumOutcome.AGREED) {
            return;
        }
        int agreedConfirmations = majorityValue(answers.values().stream()
                .filter(TxResult::exists)
                .map(TxResult::confirmations)
                .toList());
        txLifecyclePublisher.confirmed(watch, txHash, agreedConfirmations);
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
        T majorityValue = majorityValue(answers.stream().map(ProviderAnswer::value).toList());
        Map<T, Integer> counts = new LinkedHashMap<>();
        for (ProviderAnswer<T> answer : answers) {
            counts.merge(answer.value(), 1, Integer::sum);
        }
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

    /** The plurality value among {@code values} - ties broken by insertion order (T16's original
     * inline logic, extracted in T17 so {@link #handleSeenIfAgreed}/{@link #handleConfirmedIfAgreed}
     * can compute the same "what did the group actually agree on" answer {@link QuorumDecision} itself
     * does not carry (it stores only the outcome and counts, never the value). */
    private static <T> T majorityValue(Collection<T> values) {
        Map<T, Integer> counts = new LinkedHashMap<>();
        for (T value : values) {
            counts.merge(value, 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElseThrow();
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

    /** R10: package-private for direct test invocation, mirrors {@link #sweepStaleCorrelations}'s own
     * testability convention. Polls every {@code txHash} currently pending finality (added by {@link
     * #handleSeenIfAgreed} once {@code EXISTENCE} agrees {@code true}; removed here once {@code
     * FINALITY} reaches any decided outcome). */
    void pollFinality() {
        // T18 Phase 9 (self-review Finding 3 / Kimi Finding 3): each half guarded independently - an
        // uncaught RuntimeException from either must never permanently cancel this entire scheduled
        // task (the same class of bug T16 fixed for the adapters' own polling loops), and a failure in
        // one half must not silence the other.
        try {
            checkForReorg();
        } catch (RuntimeException e) {
            log.error("checkForReorg failed for watchId={} chain={} - will retry next tick",
                    watch.watchId(), watch.chain(), e);
        }
        for (String txHash : List.copyOf(pendingFinality)) {
            try {
                pollFinalityFor(txHash);
            } catch (RuntimeException e) {
                log.error("pollFinalityFor failed for watchId={} chain={} txHash={} - will retry next "
                        + "tick", watch.watchId(), watch.chain(), txHash, e);
            }
        }
    }

    /** R11/L6: runs on every tick, independent of {@link #pendingFinality} membership - keyed off
     * {@link ChainCursor#txHash()} directly, so a reorg discovered even after {@code FINALITY} already
     * agreed {@code true} (and the {@code txHash} was removed from {@code pendingFinality}) is still
     * caught (T18 Phase 3 Finding #9). Uses {@code ChainAdapter.getTx} - a synchronous pull, unaffected
     * by whatever forward-only scan position each adapter's own polling loop has reached (T18 Phase 3
     * Finding #3: {@code EthereumAdapter}/{@code TronAdapter} never re-deliver an already-scanned
     * transaction via {@code subscribeAddress}, verified by reading both directly - the original,
     * push-based detection design this method replaces could never have fired in production).
     *
     * <p><b>Trust boundary (T18 Phase 9, Kimi Finding #7).</b> {@code getTx(txHash)}'s returned {@code
     * TxResult.txHash()} is trusted to match the queried {@code txHash} without being cross-checked -
     * the same trust {@code pollFinalityFor} already places in {@code getFinalityStatus}'s response
     * (T17), consistent rather than asymmetric between this method and its sibling.</p>
     *
     * <p><b>Known, accepted limitations (T18 Phase 9, disclosed, not fixed).</b> (1) A cross-thread
     * race exists between this method (the scheduler thread) and {@link #handleSeenIfAgreed} (an
     * adapter observation-callback thread) both reading-mutating-saving the same {@link ChainCursor}
     * row with no locking - the re-check immediately before acting (below) narrows the window but does
     * not close it; a {@code @Version} optimistic-locking column would, but that is a schema change
     * outside this task's proportionate scope (Kimi Findings #4/#8). (2) {@link
     * #recordDisagreementsIfAny} has no live {@code TxCorrelation} to one-shot-guard against here (the
     * original correlation was pruned long ago), so a provider could be flagged disagreeing more than
     * once if a reorg is detected repeatedly before it is successfully persisted (Kimi Finding #5) -
     * low real-world frequency, affects only a health-tracking counter. (3) The three {@code getTx}
     * calls below run sequentially on the shared {@code sweepScheduler} thread, same as {@code
     * pollFinalityFor}'s own {@code getFinalityStatus} calls (T17 Phase 9 Finding #9's identical,
     * already-accepted disposition) - a slow provider delays both checks for this watch (Kimi Finding
     * #6).</p> */
    private void checkForReorg() {
        Optional<ChainCursor> cursorOpt = chainCursorRepository.findByWatchId(watch.watchId());
        if (cursorOpt.isEmpty() || cursorOpt.get().txHash() == null) {
            return; // nothing seen yet, or already invalidated by a prior reorg
        }
        ChainCursor cursor = cursorOpt.get();
        String txHash = cursor.txHash();

        Map<String, Boolean> existsAnswers = new LinkedHashMap<>();
        for (ProviderSet.NamedAdapter namedAdapter : adapters) {
            try {
                TxResult result = namedAdapter.adapter().getTx(txHash);
                // T18 Phase 9 (self-review Finding 1 / Kimi Finding 1, L3): logged verbatim before
                // this answer is used for anything, identical discipline to every other fact type.
                observationLog.record(watch.chain(), txHash, namedAdapter.providerName(),
                        FactType.EXISTENCE, toRawJson(result));
                existsAnswers.put(namedAdapter.providerName(), result.exists());
                providerHealthTracker.recordHealthy(watch.chain(), namedAdapter.providerName());
            } catch (RuntimeException e) {
                log.debug("Reorg re-check failed for provider={} chain={} txHash={} - marking lagging "
                        + "for this tick", namedAdapter.providerName(), watch.chain(), txHash, e);
                providerHealthTracker.recordUnhealthy(watch.chain(), namedAdapter.providerName(),
                        DegradationReason.LAGGING);
            }
        }

        if (existsAnswers.size() != 3) {
            // T18 Finding #11: never declare a reorg (or its absence) with fewer than 3 real answers -
            // mirrors evaluateFact's/pollFinalityFor's own exactly-3 discipline (T16/T17).
            return;
        }

        boolean stillExists = majorityValue(existsAnswers.values());
        if (stillExists) {
            return; // no reorg - a late, harmless re-confirmation
        }

        // T18 Phase 9 (self-review Finding 4 / Kimi Finding 4/10): re-verify the cursor still refers
        // to this same transaction before acting - handleSeenIfAgreed mutates the same cursor from a
        // different thread (the adapter's own observation-callback thread), and the getTx loop above
        // just spent real time on synchronous network calls during which that could have changed.
        Optional<ChainCursor> freshCursorOpt = chainCursorRepository.findByWatchId(watch.watchId());
        if (freshCursorOpt.isEmpty() || !txHash.equals(freshCursorOpt.get().txHash())) {
            return; // stale - something else already moved this watch on; let the next tick catch up
        }
        ChainCursor freshCursor = freshCursorOpt.get();

        // REORG DETECTED: the fresh 2-of-3 (or 3-of-3) majority now says this transaction does not
        // exist. This is L1's own quorum rule computed locally rather than persisted via
        // QuorumDecisionService (which could only ever throw here - EXISTENCE was already decided
        // AGREED true when this transaction was first seen, T18 Phase 3 Finding #1: not a bypass of
        // quorum discipline, the same 2-of-3 computation, just not re-persisted).
        List<ProviderAnswer<Boolean>> providerAnswers = existsAnswers.entrySet().stream()
                .map(entry -> new ProviderAnswer<>(entry.getKey(), entry.getValue()))
                .toList();
        recordDisagreementsIfAny(txHash, providerAnswers);

        // T18 Phase 9 (self-review Finding 2 / Kimi Finding 2): published BEFORE the cursor is
        // invalidated. ReorgDetector.reorg only catches the expected duplicate-key case; any other
        // failure here leaves the cursor untouched, so the very next tick naturally retries this exact
        // check - and if the publish actually already succeeded, the retry's second attempt is a
        // benign, swallowed duplicate (the deterministic idempotency key), never a lost event.
        reorgDetector.reorg(watch.watchId(), watch.invoiceUuid(), watch.chain(), txHash,
                watch.tokenContractAddress());
        freshCursor.invalidate(clock.instant());
        chainCursorRepository.save(freshCursor);
        pendingFinality.remove(txHash);
    }

    private String toRawJson(TxResult result) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("txHash", result.txHash());
        fields.put("exists", result.exists());
        fields.put("fromAddress", result.fromAddress());
        fields.put("toAddress", result.toAddress());
        fields.put("tokenContractAddress", result.tokenContractAddress());
        fields.put("amount", result.amount() == null ? null : result.amount().toPlainString());
        fields.put("confirmations", result.confirmations());
        fields.put("blockNumber", result.blockNumber());
        try {
            return objectMapper.writeValueAsString(fields);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("TxResult could not be serialized to JSON", e);
        }
    }

    private void pollFinalityFor(String txHash) {
        Map<String, Boolean> answers = new LinkedHashMap<>();
        Map<String, FinalityStatus> statuses = new LinkedHashMap<>();
        for (ProviderSet.NamedAdapter namedAdapter : adapters) {
            try {
                FinalityStatus status = namedAdapter.adapter().getFinalityStatus(txHash);
                // T17 Finding #3 (L3): logged verbatim before this fact is ever quorum-evaluated,
                // identical ordering to every other fact type.
                observationLog.record(watch.chain(), txHash, namedAdapter.providerName(),
                        FactType.FINALITY, toRawJson(txHash, status));
                answers.put(namedAdapter.providerName(), finalityPolicy.isFinal(status));
                statuses.put(namedAdapter.providerName(), status);
                providerHealthTracker.recordHealthy(watch.chain(), namedAdapter.providerName());
            } catch (RuntimeException e) {
                log.debug("Finality poll failed for provider={} chain={} txHash={} - marking lagging "
                        + "for this tick", namedAdapter.providerName(), watch.chain(), txHash, e);
                providerHealthTracker.recordUnhealthy(watch.chain(), namedAdapter.providerName(),
                        DegradationReason.LAGGING);
            }
        }

        if (answers.size() != 3) {
            // T17 Finding #6: skip this tick entirely rather than evaluate with fewer than 3 real
            // answers - mirrors evaluateFact's own exactly-3 discipline (T16).
            return;
        }

        // T17 Phase 10 (test-driven correctness fix - not caught by self-review or independent
        // review): QuorumDecisionService.evaluate can only ever succeed ONCE per (chain, txHash,
        // FINALITY), the same one-shot guarantee that is correct for the other four fact types but
        // wrong here if applied naively - FINALITY starts false and only becomes true after enough
        // blocks accumulate, so persisting an early, unanimous "not yet final" reading (the
        // overwhelmingly likely outcome of the very first qualifying poll tick) would permanently
        // burn the one-and-only evaluation opportunity and make chain.tx.finalized impossible to
        // ever emit for that transaction. The majority is therefore checked locally first, at zero
        // persistence cost, and evaluate() is only ever called once that local majority already
        // indicates true - the one persisted decision this produces is then always AGREED true (a
        // Boolean fact can never produce HELD: three booleans always have a 2-of-3 majority for one
        // value, by the pigeonhole principle), never a premature AGREED false.
        boolean majorityIndicatesFinal = majorityValue(answers.values());
        if (!majorityIndicatesFinal) {
            return; // not yet final by local majority - try again next tick, nothing persisted
        }

        List<ProviderAnswer<Boolean>> providerAnswers = answers.entrySet().stream()
                .map(entry -> new ProviderAnswer<>(entry.getKey(), entry.getValue()))
                .toList();

        QuorumDecision decision;
        try {
            decision = quorumDecisionService.evaluate(watch.chain(), txHash, FactType.FINALITY, providerAnswers);
            recordDisagreementsIfAny(txHash, providerAnswers);
        } catch (IllegalStateException e) {
            log.debug("Quorum decision already exists for chain={} txHash={} factType=FINALITY - "
                    + "ignoring and no longer polling", watch.chain(), txHash, e);
            pendingFinality.remove(txHash);
            return;
        }

        if (decision.outcome() != QuorumOutcome.AGREED) {
            return; // unreachable for a Boolean fact in practice; kept as defense-in-depth
        }

        pendingFinality.remove(txHash);

        long finalizedBlockNumber = majorityValue(statuses.values().stream()
                .map(FinalityStatus::finalizedBlockNumber)
                .toList());

        Optional<ChainCursor> cursor = chainCursorRepository.findByWatchId(watch.watchId());
        if (cursor.isEmpty()) {
            // T17 Phase 9 (self-review Finding 3 / Kimi Finding 7): mirrors handleSeenIfAgreed's own
            // logging - should never happen in practice, surfaced rather than silently dropped.
            log.warn("No ChainCursor found for watchId={} chain={} - FINALITY agreed true for "
                    + "txHash={} but chain.tx.finalized could not be emitted", watch.watchId(),
                    watch.chain(), txHash);
            return;
        }
        if (!txHash.equals(cursor.get().txHash())) {
            // T17 Phase 9 (Kimi Finding 1): this watch's cursor snapshot belongs to a *different*
            // transaction than the one just reaching finality - recordSeenTransaction is write-once
            // (a disclosed, accepted limitation for a watch that observes more than one distinct
            // transaction, T17 frozen brief), so publishing here would cite the wrong txHash/amount.
            // Skipped rather than emitting a misleading event; the mismatched transaction's own
            // finality was still correctly decided and persisted above, only the event is withheld.
            log.warn("ChainCursor snapshot for watchId={} belongs to txHash={} but FINALITY just agreed "
                    + "true for a different txHash={} - chain.tx.finalized withheld for the latter "
                    + "(this watch has observed more than one transaction, a known, disclosed "
                    + "limitation of this task's scope)", watch.watchId(), cursor.get().txHash(), txHash);
            return;
        }
        cursor.get().advanceFinalizedTo(finalizedBlockNumber, clock.instant());
        chainCursorRepository.save(cursor.get());
        txLifecyclePublisher.finalized(watch, cursor.get());
    }

    private String toRawJson(String txHash, FinalityStatus status) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("txHash", txHash);
        fields.put("txBlockNumber", status.txBlockNumber());
        fields.put("currentBlockNumber", status.currentBlockNumber());
        fields.put("finalizedBlockNumber", status.finalizedBlockNumber());
        try {
            return objectMapper.writeValueAsString(fields);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("FinalityStatus could not be serialized to JSON", e);
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
