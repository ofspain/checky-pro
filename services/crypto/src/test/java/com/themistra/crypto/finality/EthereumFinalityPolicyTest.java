package com.themistra.crypto.finality;

import com.themistra.crypto.adapter.Chain;
import com.themistra.crypto.adapter.model.FinalityStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class EthereumFinalityPolicyTest {

    private final EthereumFinalityPolicy policy = new EthereumFinalityPolicy();

    @Test
    void shouldRequireBeaconFinalizedCheckpointForEthereumFinality() {
        // R6: final once the tx's block is at or below the beacon finalized checkpoint - no
        // confirmation count is involved anywhere in this comparison.
        FinalityStatus status = new FinalityStatus(18_000_000L, 18_000_050L, 18_000_010L);

        assertThat(policy.isFinal(status)).isTrue();
    }

    @Test
    void isFinalAtTheBoundaryWhenTxBlockEqualsTheFinalizedCheckpoint() {
        FinalityStatus status = new FinalityStatus(100L, 200L, 100L);

        assertThat(policy.isFinal(status)).isTrue();
    }

    @Test
    void isNotFinalWhenTxBlockIsOneAboveTheFinalizedCheckpoint() {
        FinalityStatus status = new FinalityStatus(101L, 200L, 100L);

        assertThat(policy.isFinal(status)).isFalse();
    }

    @Test
    void isNotFinalWhenTxBlockIsWellAboveTheFinalizedCheckpoint() {
        // Phase 11 (Kimi Issue 3): a non-boundary negative case - the boundary test alone doesn't rule
        // out the policy accidentally returning true for any status where the tx block is simply newer
        // than the checkpoint.
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
    void chainReturnsEthereum() {
        assertThat(policy.chain()).isEqualTo(Chain.ETHEREUM);
    }

    @Test
    void isFinalThrowsOnNullStatus() {
        assertThatNullPointerException().isThrownBy(() -> policy.isFinal(null));
    }

    @Test
    void appliesTheSameRawComparisonAsTronFinalityPolicyForTheSameStatus() {
        // Phase 9 (Kimi Issue 2): FinalityStatus carries no chain discriminator (Phase 3 Finding 1,
        // accepted as documentation-only) - neither policy performs a chain self-check, so the same
        // status fed to either policy produces the identical raw comparison result. This locks the
        // documented "no self-validation" contract in executable form.
        //
        // Phase 11 (Kimi Issue 4): the original version of this test only exercised the `true` path -
        // a `finalStatus`/`notFinalStatus` pair now covers both branches, so a subtle divergence in
        // the `false` case would not slip past unnoticed.
        FinalityStatus finalStatus = new FinalityStatus(500L, 900L, 500L);
        FinalityStatus notFinalStatus = new FinalityStatus(600L, 900L, 500L);
        TronFinalityPolicy tronPolicy = new TronFinalityPolicy();

        assertThat(policy.isFinal(finalStatus)).isEqualTo(tronPolicy.isFinal(finalStatus));
        assertThat(policy.isFinal(notFinalStatus)).isEqualTo(tronPolicy.isFinal(notFinalStatus));
    }
}
