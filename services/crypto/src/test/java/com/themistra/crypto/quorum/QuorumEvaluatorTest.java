package com.themistra.crypto.quorum;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** AC1 (2-of-3 quorum, exactly-3, compareTo()-based), AC6 (exhaustive agreement matrix), AC9
 * (scale-invariant BigDecimal comparison). Pure logic - no mocks, no Spring context. */
class QuorumEvaluatorTest {

    private final QuorumEvaluator evaluator = new QuorumEvaluator();

    @Test
    void shouldTreatFactAsTrueOnlyWhenTwoOfThreeProvidersAgree() {
        // package.md §8 named test, R1: 2-of-3 providers agree the transaction exists.
        QuorumEvaluator.Result result = evaluator.evaluate(List.of(true, true, false));

        assertThat(result.outcome()).isEqualTo(QuorumOutcome.AGREED);
        assertThat(result.agreeingCount()).isEqualTo(2);
        assertThat(result.providerCount()).isEqualTo(3);
    }

    @Test
    void allThreeMatchIsAgreedWithAgreeingCountThree() {
        QuorumEvaluator.Result result = evaluator.evaluate(List.of(100, 100, 100));

        assertThat(result.outcome()).isEqualTo(QuorumOutcome.AGREED);
        assertThat(result.agreeingCount()).isEqualTo(3);
        assertThat(result.providerCount()).isEqualTo(3);
    }

    @Test
    void firstAndSecondMatchThirdDiffersIsAgreedWithAgreeingCountTwo() {
        QuorumEvaluator.Result result = evaluator.evaluate(List.of("A", "A", "B"));

        assertThat(result.outcome()).isEqualTo(QuorumOutcome.AGREED);
        assertThat(result.agreeingCount()).isEqualTo(2);
    }

    @Test
    void firstAndThirdMatchSecondDiffersIsAgreedWithAgreeingCountTwo() {
        QuorumEvaluator.Result result = evaluator.evaluate(List.of("A", "B", "A"));

        assertThat(result.outcome()).isEqualTo(QuorumOutcome.AGREED);
        assertThat(result.agreeingCount()).isEqualTo(2);
    }

    @Test
    void secondAndThirdMatchFirstDiffersIsAgreedWithAgreeingCountTwo() {
        QuorumEvaluator.Result result = evaluator.evaluate(List.of("A", "B", "B"));

        assertThat(result.outcome()).isEqualTo(QuorumOutcome.AGREED);
        assertThat(result.agreeingCount()).isEqualTo(2);
    }

    @Test
    void allThreeDistinctIsHeldWithAgreeingCountOne() {
        QuorumEvaluator.Result result = evaluator.evaluate(List.of("A", "B", "C"));

        assertThat(result.outcome()).isEqualTo(QuorumOutcome.HELD);
        assertThat(result.agreeingCount()).isEqualTo(1);
        assertThat(result.providerCount()).isEqualTo(3);
    }

    @Test
    void agreedOnAFalseValueIsStillAgreedNotHeld() {
        // AGREED denotes consensus, not boolean truth (Amendment #4): two providers agreeing a
        // transaction does NOT exist is a correct, expected AGREED outcome.
        QuorumEvaluator.Result result = evaluator.evaluate(List.of(false, false, true));

        assertThat(result.outcome()).isEqualTo(QuorumOutcome.AGREED);
    }

    @Test
    void bigDecimalAnswersWithDifferentScaleButEqualValueAreTreatedAsMatching() {
        // Amendment #1 (Phase 3 Kimi Issue 1): compareTo()==0, not equals(), so "1.0" and "1.00"
        // agree despite BigDecimal.equals() itself returning false for that pair.
        QuorumEvaluator.Result result = evaluator.evaluate(
                List.of(new BigDecimal("1.0"), new BigDecimal("1.00"), new BigDecimal("2.0")));

        assertThat(result.outcome()).isEqualTo(QuorumOutcome.AGREED);
        assertThat(result.agreeingCount()).isEqualTo(2);
        assertThat(new BigDecimal("1.0")).isNotEqualTo(new BigDecimal("1.00")); // sanity: equals() itself differs
    }

    @Test
    void rejectsAnEmptyList() {
        assertThatThrownBy(() -> evaluator.evaluate(List.<String>of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly 3");
    }

    @Test
    void rejectsAListOfOne() {
        assertThatThrownBy(() -> evaluator.evaluate(List.of("A")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly 3");
    }

    @Test
    void rejectsAListOfTwo() {
        assertThatThrownBy(() -> evaluator.evaluate(List.of("A", "A")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly 3");
    }

    @Test
    void rejectsAListOfFour() {
        assertThatThrownBy(() -> evaluator.evaluate(List.of("A", "A", "A", "A")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly 3");
    }

    @Test
    void rejectsANullList() {
        assertThatThrownBy(() -> evaluator.<String>evaluate(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("answers");
    }

    @Test
    void rejectsANullElementWithinTheList() {
        // Amendment #2 (Phase 3 Kimi Issue 2): a provider with no answer must be omitted from the
        // list, never represented as null.
        List<String> answersWithNull = Arrays.asList("A", null, "B");

        assertThatThrownBy(() -> evaluator.evaluate(answersWithNull))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("null");
    }
}
