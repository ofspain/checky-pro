package com.themistra.crypto.lookup;

import com.themistra.crypto.adapter.Chain;
import com.themistra.crypto.adapter.ProviderSet;
import com.themistra.crypto.adapter.model.TxResult;
import com.themistra.crypto.quorum.QuorumEvaluator;
import com.themistra.crypto.quorum.QuorumOutcome;
import com.themistra.crypto.token.TokenValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Crypto task 30 (R29, L16): looks up one transaction hash on demand. It asks all 3 providers for
 * the chain through {@code ChainAdapter.getTx} and runs the existing {@link QuorumEvaluator} over
 * each fact. There is no other verification logic and no way round the 2-of-3 rule (L1).
 *
 * <ul>
 *   <li>Existence is decided first. Unanimous absence is a 404. A disputed existence, or a 2-of-3
 *       absence (one provider says it exists), is {@code HELD}: the contract keeps 404 for
 *       unanimous absence, and an absence one provider disputes is never resolved in the caller's
 *       favour (L2).</li>
 *   <li>The other facts are decided as the watcher decides them: only from providers that report
 *       the transaction exists, and only with all 3 answers. If any fact cannot be decided, or the
 *       providers disagree on it, the answer is {@code HELD} with no facts.</li>
 *   <li>Agreed facts for a token not on the allowlist are {@code UNKNOWN_TOKEN} (R14).</li>
 * </ul>
 *
 * <p>Read-only. It writes no {@code quorum_decisions} row: that table holds one decision per fact
 * for the watch path, which a lookup must not pre-empt or collide with. It writes no
 * {@code observations} row either, because {@code getTx} returns no verbatim provider payload to
 * record (L3 covers the watch path's persisted decisions). Nothing in the response is used to sign;
 * attestation still requires the watch path's persisted decisions (L10).</p>
 */
@Service
public class TransactionLookupService {

    private static final Logger log = LoggerFactory.getLogger(TransactionLookupService.class);

    private final ProviderSet providerSet;
    private final QuorumEvaluator evaluator;
    private final TokenValidator tokenValidator;

    public TransactionLookupService(ProviderSet providerSet, QuorumEvaluator evaluator, TokenValidator tokenValidator) {
        this.providerSet = providerSet;
        this.evaluator = evaluator;
        this.tokenValidator = tokenValidator;
    }

    public TransactionLookupResponse lookup(Chain chain, String txHash) {
        List<TxResult> answers = askEveryProvider(chain, txHash);
        String chainName = chain.name();

        Optional<Boolean> exists = decide(answers, TxResult::exists);
        if (exists.isEmpty()) {
            return TransactionLookupResponse.held(chainName, txHash, QuorumOutcome.HELD.name());
        }
        if (!exists.get()) {
            if (answers.stream().noneMatch(TxResult::exists)) {
                throw new TransactionNotFoundException();
            }
            return TransactionLookupResponse.held(chainName, txHash, QuorumOutcome.HELD.name());
        }
        if (!answers.stream().allMatch(TxResult::exists)) {
            // One provider has not seen it: the remaining facts have only 2 answers (as in the watcher).
            return TransactionLookupResponse.held(chainName, txHash, QuorumOutcome.AGREED.name());
        }

        Optional<Integer> confirmations = decide(answers, TxResult::confirmations);
        Optional<String> token = decide(answers, TxResult::tokenContractAddress);
        Optional<String> from = decide(answers, TxResult::fromAddress);
        Optional<String> to = decide(answers, TxResult::toAddress);
        Optional<BigDecimal> amount = decide(answers, TxResult::amount);
        if (confirmations.isEmpty() || token.isEmpty() || from.isEmpty() || to.isEmpty() || amount.isEmpty()) {
            return TransactionLookupResponse.held(chainName, txHash, QuorumOutcome.AGREED.name());
        }

        String outcome = tokenValidator.validate(chainName, token.get()).isPresent()
                ? QuorumOutcome.AGREED.name() : QuorumOutcome.UNKNOWN_TOKEN.name();
        return new TransactionLookupResponse(chainName, txHash, QuorumOutcome.AGREED.name(), confirmations.get(),
                token.get(), from.get(), to.get(), amount.get().toPlainString(), outcome);
    }

    private List<TxResult> askEveryProvider(Chain chain, String txHash) {
        List<ProviderSet.NamedAdapter> adapters = providerSet.adaptersFor(chain);
        List<TxResult> answers = new ArrayList<>(adapters.size());
        for (ProviderSet.NamedAdapter named : adapters) {
            try {
                answers.add(named.adapter().getTx(txHash));
            } catch (RuntimeException e) {
                log.warn("Transaction lookup: provider={} chain={} did not answer ({})",
                        named.providerName(), chain, e.getClass().getSimpleName());
                throw new ProvidersUnavailableException("provider " + named.providerName() + " did not answer");
            }
        }
        return answers;
    }

    /** The agreed value of one fact, or empty if the 2-of-3 rule does not agree on one. */
    private <T extends Comparable<T>> Optional<T> decide(List<TxResult> answers, Function<TxResult, T> fact) {
        List<T> values = answers.stream().map(fact).toList();
        if (values.size() != 3 || values.stream().anyMatch(java.util.Objects::isNull)) {
            return Optional.empty();
        }
        if (evaluator.evaluate(values).outcome() != QuorumOutcome.AGREED) {
            return Optional.empty();
        }
        T first = values.get(0);
        boolean firstIsInTheMajority = first.compareTo(values.get(1)) == 0 || first.compareTo(values.get(2)) == 0;
        return Optional.of(firstIsInTheMajority ? first : values.get(1));
    }
}
