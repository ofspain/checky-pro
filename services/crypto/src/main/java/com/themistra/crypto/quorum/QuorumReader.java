package com.themistra.crypto.quorum;

import com.themistra.crypto.chain.ChainAdapter;
import com.themistra.crypto.chain.TxObservation;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Establishes chain facts by agreement across independent providers (ARCHITECTURE §6.1).
 *
 * <p>No single provider's answer leaves this service as fact. Providers are queried concurrently
 * on virtual threads, their answers recorded verbatim for the observation log, and a fact is
 * returned only when enough of them agree.
 *
 * <p>A provider that fails is not a vote in either direction: with one provider unreachable and
 * the other two in agreement, quorum is satisfied. A provider that answers "not on chain" <em>is</em>
 * a vote, and enough of those produce {@link QuorumResult.Absent}.
 */
public class QuorumReader {

    private final QuorumPolicy policy;
    private final Clock clock;

    public QuorumReader(QuorumPolicy policy, Clock clock) {
        this.policy = policy;
        this.clock = clock;
    }

    public QuorumReader(QuorumPolicy policy) {
        this(policy, Clock.systemUTC());
    }

    /**
     * Asks every adapter about one transaction and decides whether their answers constitute a fact.
     *
     * @param adapters the independent providers for a single chain
     * @param txHash   the transaction to establish
     */
    public QuorumResult establish(List<ChainAdapter> adapters, String txHash) {
        if (adapters == null || adapters.isEmpty()) {
            throw new IllegalArgumentException("at least one adapter is required");
        }
        if (adapters.size() < policy.threshold()) {
            throw new IllegalArgumentException(
                    "cannot reach a threshold of " + policy.threshold()
                            + " with only " + adapters.size() + " providers");
        }
        return decide(query(adapters, txHash));
    }

    private List<ProviderAnswer> query(List<ChainAdapter> adapters, String txHash) {
        List<Callable<ProviderAnswer>> calls = adapters.stream()
                .map(adapter -> (Callable<ProviderAnswer>) () -> ask(adapter, txHash))
                .toList();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<ProviderAnswer> answers = new ArrayList<>(adapters.size());
            for (Future<ProviderAnswer> future : executor.invokeAll(calls)) {
                try {
                    answers.add(future.get());
                } catch (Exception e) {
                    // invokeAll has already completed every task; a throw here is the task's own
                    // failure, which ask() normally converts. Recorded rather than propagated so
                    // one broken provider cannot deny the whole read.
                    answers.add(ProviderAnswer.failed("unknown", String.valueOf(e.getMessage()), now()));
                }
            }
            return List.copyOf(answers);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while querying providers", e);
        }
    }

    private ProviderAnswer ask(ChainAdapter adapter, String txHash) {
        try {
            Optional<TxObservation> seen = adapter.getTx(txHash);
            return seen.map(observation -> ProviderAnswer.saw(adapter.providerLabel(), observation, now()))
                    .orElseGet(() -> ProviderAnswer.absent(adapter.providerLabel(), now()));
        } catch (RuntimeException e) {
            return ProviderAnswer.failed(adapter.providerLabel(), e.getMessage(), now());
        }
    }

    private QuorumResult decide(List<ProviderAnswer> answers) {
        Map<TxObservation.Fact, List<TxObservation>> byFact = new HashMap<>();
        int absences = 0;
        int failures = 0;

        for (ProviderAnswer answer : answers) {
            if (answer.isFailure()) {
                failures++;
            } else if (answer.isAbsence()) {
                absences++;
            } else {
                TxObservation observation = answer.observation().orElseThrow();
                byFact.computeIfAbsent(observation.fact(), key -> new ArrayList<>()).add(observation);
            }
        }

        Optional<Map.Entry<TxObservation.Fact, List<TxObservation>>> strongest = byFact.entrySet().stream()
                .max(Comparator.comparingInt(entry -> entry.getValue().size()));

        if (strongest.isPresent() && strongest.get().getValue().size() >= policy.threshold()) {
            return new QuorumResult.Agreed(strongest.get().getValue().getFirst(), answers);
        }
        if (absences >= policy.threshold()) {
            return new QuorumResult.Absent(answers);
        }

        int agreeing = strongest.map(entry -> entry.getValue().size()).orElse(0);
        String reason = byFact.size() > 1
                ? "providers reported %d conflicting facts; strongest had %d of %d required"
                        .formatted(byFact.size(), agreeing, policy.threshold())
                : "no fact reached quorum: %d agreeing, %d absent, %d failed, %d required"
                        .formatted(agreeing, absences, failures, policy.threshold());
        return new QuorumResult.Disagreed(reason, answers);
    }

    private Instant now() {
        return clock.instant();
    }
}
