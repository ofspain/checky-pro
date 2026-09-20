package com.themistra.crypto.quorum;

import com.themistra.crypto.chain.TxObservation;

import java.util.List;

/**
 * The outcome of comparing independent providers (ARCHITECTURE §6.1).
 *
 * <p>Sealed so that every caller must handle disagreement explicitly. Disagreement is never
 * resolved automatically and never in the payer's favour: it becomes a {@code held} lifecycle
 * event and an ops alert, not silence.
 *
 * <p>Every variant carries the full set of provider answers for the observation log.
 */
public sealed interface QuorumResult {

    List<ProviderAnswer> answers();

    /** Enough providers agreed on the same fact. */
    record Agreed(TxObservation fact, List<ProviderAnswer> answers) implements QuorumResult {
        public Agreed {
            answers = List.copyOf(answers);
        }
    }

    /** Enough providers agreed the transaction is not on chain. */
    record Absent(List<ProviderAnswer> answers) implements QuorumResult {
        public Absent {
            answers = List.copyOf(answers);
        }
    }

    /**
     * No fact reached the threshold. Either providers contradicted each other, or too many failed
     * to leave a quorum. Both mean the same thing to the caller: assert nothing.
     */
    record Disagreed(String reason, List<ProviderAnswer> answers) implements QuorumResult {
        public Disagreed {
            answers = List.copyOf(answers);
        }
    }
}
