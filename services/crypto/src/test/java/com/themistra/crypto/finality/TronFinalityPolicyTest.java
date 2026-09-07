package com.themistra.crypto.finality;

import com.themistra.crypto.adapter.Chain;
import com.themistra.crypto.adapter.model.FinalityStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class TronFinalityPolicyTest {

    private final TronFinalityPolicy policy = new TronFinalityPolicy();

    @Test
    void shouldRequireSolidifiedBlockForTronFinality() {
        // R7: final once the tx's block is at or below the solidified block - "~19 confirmations" is
        // never re-derived as a literal threshold here; the solidified block number is already fetched
        // by TronAdapter.
        FinalityStatus status = new FinalityStatus(50_000_000L, 50_000_030L, 50_000_019L);

        assertThat(policy.isFinal(status)).isTrue();
    }

    @Test
    void isFinalAtTheBoundaryWhenTxBlockEqualsTheSolidifiedBlock() {
        FinalityStatus status = new FinalityStatus(100L, 200L, 100L);

        assertThat(policy.isFinal(status)).isTrue();
    }

    @Test
    void isNotFinalWhenTxBlockIsOneAboveTheSolidifiedBlock() {
        FinalityStatus status = new FinalityStatus(101L, 200L, 100L);

        assertThat(policy.isFinal(status)).isFalse();
    }

    @Test
    void isNotFinalWhenTxBlockIsWellAboveTheSolidifiedBlock() {
        // Phase 11 (Kimi Issue 3): a non-boundary negative case - the boundary test alone doesn't rule
        // out the policy accidentally returning true for any status where the tx block is simply newer
        // than the solidified block.
        FinalityStatus status = new FinalityStatus(1_000L, 1_050L, 100L);

        assertThat(policy.isFinal(status)).isFalse();
    }

    @Test
    void isFinalAtTheGenesisBoundaryWhenBothBlockNumbersAreZero() {
        // Phase 9 (Kimi Issue 3): a transaction mined in the chain's first block is a realistic value,
        // not just a theoretical edge.
        FinalityStatus status = new FinalityStatus(0L, 0L, 0L);

        assertThat(policy.isFinal(status)).isTrue();
    }

    @Test
    void ignoresCurrentBlockNumberFarAheadOfBothOtherFields() {
        // "Scripted chain heads" (task statement) - currentBlockNumber is informative context only
        // (FinalityStatus's own Javadoc); the decision must depend solely on txBlockNumber vs.
        // finalizedBlockNumber.
        FinalityStatus status = new FinalityStatus(100L, 1_000_000L, 100L);

        assertThat(policy.isFinal(status)).isTrue();
    }

    @Test
    void ignoresCurrentBlockNumberFarBehindBothOtherFields() {
        // Phase 9 (Kimi Issue 4): the inverse direction of the above - still ignored.
        FinalityStatus status = new FinalityStatus(100L, 50L, 100L);

        assertThat(policy.isFinal(status)).isTrue();
    }

    @Test
    void chainReturnsTron() {
        assertThat(policy.chain()).isEqualTo(Chain.TRON);
    }

    @Test
    void isFinalThrowsOnNullStatus() {
        assertThatNullPointerException().isThrownBy(() -> policy.isFinal(null));
    }
}
