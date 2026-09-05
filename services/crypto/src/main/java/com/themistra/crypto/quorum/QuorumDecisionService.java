package com.themistra.crypto.quorum;

import com.themistra.crypto.observation.FactType;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The single operation R1-R3/L1-L2 describe: evaluate 2-of-3 quorum for one fact, alert ops and
 * persist {@code HELD} on disagreement, persist {@code AGREED} otherwise - never auto-resolving
 * either way. Composes {@link QuorumEvaluator} (pure comparison), {@link HeldFactAlerter} (ops alert),
 * and {@link QuorumDecisionRepository} (persistence); none of the three orchestrates another. Not
 * named in design.md §6's own package map, the same "functionally necessary, not spec-named" situation
 * {@code ObservationLog} was in for T08.
 *
 * <p><b>Deliberately not {@code @Transactional}.</b> This method's only database write is the single
 * {@link QuorumDecisionRepository#save} call, and Spring Data's own {@code SimpleJpaRepository.save(...)}
 * is already individually {@code @Transactional} - the same reasoning T08's {@code ObservationLog}
 * relied on. Unlike {@code ObservationLog}, this task has no S3/network call to worry about holding a
 * connection open for, but the same discipline is followed for consistency and because it remains the
 * technically correct, minimal-scope choice.</p>
 *
 * <p><b>Duplicate-provider rejection (Phase 3 Kimi Issue 6).</b> A buggy caller submitting two answers
 * from the same provider could artificially manufacture a false {@code AGREED} - rejected here, before
 * either {@link QuorumEvaluator} or any collaborator is invoked.</p>
 *
 * <p><b>Alert-after-persist ordering, and a pre-flight existing-decision check (Phase 9, Kimi Phase 8
 * Issues 1 and 7).</b> {@link HeldFactAlerter} is invoked only after {@link
 * QuorumDecisionRepository#save} succeeds, not before - an ops alert always corresponds to a durably
 * persisted decision. A pre-flight {@link QuorumDecisionRepository#findByChainAndTxHashAndFactType}
 * check rejects a second evaluation of the same {@code (chain, txHash, factType)} with a clear {@link
 * IllegalStateException} before any alert or save is attempted, rather than letting it fail obscurely
 * against {@code uq_quorum_tx_fact} (frozen brief Amendment #7 - re-evaluation is out of this task's
 * own scope, and this makes that boundary an explicit, named failure rather than a raw constraint
 * violation).</p>
 */
@Component
public class QuorumDecisionService {

    private final QuorumEvaluator evaluator;
    private final QuorumDecisionRepository repository;
    private final HeldFactAlerter alerter;
    private final Clock clock;

    public QuorumDecisionService(QuorumEvaluator evaluator, QuorumDecisionRepository repository,
                                  HeldFactAlerter alerter, Clock clock) {
        this.evaluator = evaluator;
        this.repository = repository;
        this.alerter = alerter;
        this.clock = clock;
    }

    public <T extends Comparable<T>> QuorumDecision evaluate(String chain, String txHash,
                                                               FactType factType,
                                                               List<ProviderAnswer<T>> answers) {
        Objects.requireNonNull(chain, "chain");
        Objects.requireNonNull(txHash, "txHash");
        Objects.requireNonNull(factType, "factType");
        Objects.requireNonNull(answers, "answers");
        rejectExistingDecision(chain, txHash, factType);
        rejectDuplicateProviders(answers);

        QuorumEvaluator.Result result = evaluator.evaluate(extractValues(answers));
        Instant decidedAt = clock.instant();

        QuorumDecision decision = QuorumDecision.create(chain, txHash, factType, result.outcome(),
                result.agreeingCount(), result.providerCount(), decidedAt);
        QuorumDecision saved = repository.save(decision);

        if (result.outcome() == QuorumOutcome.HELD) {
            alerter.alert(chain, txHash, factType, answers);
        }

        return saved;
    }

    private void rejectExistingDecision(String chain, String txHash, FactType factType) {
        if (repository.findByChainAndTxHashAndFactType(chain, txHash, factType).isPresent()) {
            throw new IllegalStateException("a quorum decision already exists for chain=" + chain
                    + " txHash=" + txHash + " factType=" + factType
                    + " - re-evaluation of the same fact is not supported (see frozen brief Amendment #7)");
        }
    }

    private <T> void rejectDuplicateProviders(List<ProviderAnswer<T>> answers) {
        Set<String> seenProviders = new HashSet<>();
        for (ProviderAnswer<T> answer : answers) {
            if (!seenProviders.add(answer.provider())) {
                throw new IllegalArgumentException(
                        "duplicate answer from provider '" + answer.provider() + "' - one answer per provider is required");
            }
        }
    }

    private <T> List<T> extractValues(List<ProviderAnswer<T>> answers) {
        return answers.stream().map(ProviderAnswer::value).toList();
    }
}
