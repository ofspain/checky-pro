package com.themistra.crypto.quorum;

import java.util.List;
import java.util.Objects;

/**
 * The 2-of-3 arbitration rule itself (L1, L2, R1-R3) - genuinely pure logic (design.md §6's own
 * label), no injected dependencies, no I/O. Given exactly 3 non-null, mutually {@link Comparable}
 * provider answers for one fact, determines the largest {@code compareTo() == 0}-matching group and
 * reports {@link QuorumOutcome#AGREED} when that group's size is at least 2, {@link
 * QuorumOutcome#HELD} otherwise.
 *
 * <p><b>Exactly 3 answers, not a generalized N-of-M rule (Phase 3 Kimi Issues 3 and 10, merged).</b>
 * L1's own wording is "2-of-3 quorum," not "majority of N" - a list of any other size is rejected
 * rather than silently evaluated. This also makes a tied largest group structurally impossible: with
 * exactly 3 elements grouped by {@code compareTo() == 0}, the only possible groupings are all-three-
 * match, exactly-one-matching-pair, or all-three-distinct - never two groups of equal size.</p>
 *
 * <p><b>{@code compareTo() == 0}, not {@code equals()} (Phase 3 Kimi Issue 1).</b> {@code
 * BigDecimal.equals()} is scale-sensitive ({@code "1.0"} and {@code "1.00"} are unequal), which would
 * wrongly report disagreement on an {@code AMOUNT} fact reported at different scales by different
 * providers. Comparing via {@code compareTo() == 0} instead fixes that while remaining behaviorally
 * identical to {@code equals()}-based grouping for {@code Boolean}/{@code String}/integral types,
 * whose natural ordering is consistent with equality.</p>
 *
 * <p>{@code AGREED} denotes that providers converged on the same value - it does not mean the value
 * itself is boolean-true. Two providers agreeing an {@code EXISTENCE} fact is {@code false} (the
 * transaction does not exist) is a correct, expected {@code AGREED} outcome.</p>
 *
 * <p><b>The exactly-3 requirement is deliberate, not a launch-only simplification to relax later
 * casually (Phase 9, Kimi Phase 8 Issue 5, re-raising Phase 3 Issues 3/10).</b> A degraded-provider
 * scenario (only 2 of 3 reachable) is explicitly out of this task's scope - see the frozen brief's own
 * "Out: {@code ProviderHealth}/{@code chain.provider.degraded} (task 10)." Task 10 must define its own
 * resolution for evaluating with fewer than 3 answers (e.g. a separate code path, or waiting for the
 * third provider); it is not this evaluator's job to guess that shape now.</p>
 */
public class QuorumEvaluator {

    public <T extends Comparable<T>> Result evaluate(List<T> answers) {
        validate(answers);
        int agreeingCount = largestMatchingGroupSize(answers.get(0), answers.get(1), answers.get(2));
        QuorumOutcome outcome = agreeingCount >= 2 ? QuorumOutcome.AGREED : QuorumOutcome.HELD;
        return new Result(outcome, agreeingCount, answers.size());
    }

    private <T> void validate(List<T> answers) {
        Objects.requireNonNull(answers, "answers");
        if (answers.size() != 3) {
            throw new IllegalArgumentException("exactly 3 answers are required, got " + answers.size());
        }
        for (int i = 0; i < answers.size(); i++) {
            if (answers.get(i) == null) {
                throw new IllegalArgumentException("answer at index " + i + " is null - a provider "
                        + "with no answer must be omitted from the list, not represented as null");
            }
        }
    }

    private <T extends Comparable<T>> int largestMatchingGroupSize(T first, T second, T third) {
        boolean firstSecondMatch = first.compareTo(second) == 0;
        boolean secondThirdMatch = second.compareTo(third) == 0;
        boolean firstThirdMatch = first.compareTo(third) == 0;

        if (firstSecondMatch && secondThirdMatch) {
            return 3;
        }
        if (firstSecondMatch || secondThirdMatch || firstThirdMatch) {
            return 2;
        }
        return 1;
    }

    /** {@code agreeingCount}/{@code providerCount} only - no agreed value (frozen brief Amendment #5:
     * no consumer of the agreed value is defined in this task's own scope). */
    public record Result(QuorumOutcome outcome, int agreeingCount, int providerCount) {
    }
}
