package com.themistra.crypto.adapter;

import com.themistra.crypto.adapter.model.Subscription;
import com.themistra.crypto.adapter.model.TxResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** AC3, AC6, AC7 (frozen brief). */
class FakeChainAdapterTest {

    private static final String TX_HASH = "0xabc";
    private static final String FROM = "0xfrom";
    private static final String TO = "0xto";
    private static final String TOKEN = "0xtoken";

    private static TxResult tx(boolean exists, int confirmations, long blockNumber) {
        return new TxResult(exists, TX_HASH, FROM, TO, TOKEN, BigDecimal.TEN, confirmations, blockNumber);
    }

    @Test
    void agreeIsTwoInstancesScriptedWithAnEqualResultForTheSameTxHash() {
        TxResult agreed = tx(true, 3, 100L);
        FakeChainAdapter providerA = new FakeChainAdapter(Chain.ETHEREUM).scriptTx(TX_HASH, agreed);
        FakeChainAdapter providerB = new FakeChainAdapter(Chain.ETHEREUM).scriptTx(TX_HASH, agreed);

        assertThat(providerA.getTx(TX_HASH)).isEqualTo(providerB.getTx(TX_HASH));
    }

    @Test
    void disagreeIsTwoInstancesScriptedWithDifferingResultsForTheSameTxHash() {
        FakeChainAdapter providerA = new FakeChainAdapter(Chain.ETHEREUM).scriptTx(TX_HASH, tx(true, 3, 100L));
        FakeChainAdapter providerB = new FakeChainAdapter(Chain.ETHEREUM).scriptTx(TX_HASH, tx(true, 3, 101L));

        assertThat(providerA.getTx(TX_HASH)).isNotEqualTo(providerB.getTx(TX_HASH));
    }

    @Test
    void lagIsARelativelyBehindOrUnobservedResult() {
        FakeChainAdapter caughtUp = new FakeChainAdapter(Chain.ETHEREUM).scriptTx(TX_HASH, tx(true, 12, 100L));
        FakeChainAdapter laggingByConfirmations = new FakeChainAdapter(Chain.ETHEREUM).scriptTx(TX_HASH, tx(true, 1, 100L));
        FakeChainAdapter notYetObserved = new FakeChainAdapter(Chain.ETHEREUM).scriptTx(TX_HASH, tx(false, 0, 0L));

        assertThat(laggingByConfirmations.getTx(TX_HASH).confirmations())
                .isLessThan(caughtUp.getTx(TX_HASH).confirmations());
        assertThat(notYetObserved.getTx(TX_HASH).exists()).isFalse();
    }

    @Test
    void reorgReScriptsTheTxAndPushesTheNewResultToAMatchingSubscription() {
        FakeChainAdapter adapter = new FakeChainAdapter(Chain.ETHEREUM);
        TxResult original = tx(true, 3, 100L);
        adapter.scriptTx(TX_HASH, original);

        List<TxResult> received = new ArrayList<>();
        adapter.subscribeAddress(FROM, received::add);

        TxResult reorged = tx(true, 1, 99L);
        adapter.simulateReorg(TX_HASH, reorged);

        assertThat(adapter.getTx(TX_HASH)).isEqualTo(reorged);
        assertThat(received).containsExactly(reorged);
    }

    @Test
    void reorgDoesNotPushToASubscriptionForAnUnrelatedAddress() {
        FakeChainAdapter adapter = new FakeChainAdapter(Chain.ETHEREUM);
        adapter.scriptTx(TX_HASH, tx(true, 3, 100L));

        List<TxResult> received = new ArrayList<>();
        adapter.subscribeAddress("0xsome-other-address", received::add);

        adapter.simulateReorg(TX_HASH, tx(true, 1, 99L));

        assertThat(received).isEmpty();
    }

    @Test
    void unscriptedGetTxThrows() {
        FakeChainAdapter adapter = new FakeChainAdapter(Chain.ETHEREUM);

        assertThatThrownBy(() -> adapter.getTx(TX_HASH))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(TX_HASH);
    }

    @Test
    void unscriptedGetTokenInfoThrows() {
        FakeChainAdapter adapter = new FakeChainAdapter(Chain.ETHEREUM);

        assertThatThrownBy(() -> adapter.getTokenInfo(TOKEN))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(TOKEN);
    }

    @Test
    void unscriptedGetFinalityStatusThrows() {
        FakeChainAdapter adapter = new FakeChainAdapter(Chain.ETHEREUM);

        assertThatThrownBy(() -> adapter.getFinalityStatus(TX_HASH))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(TX_HASH);
    }

    @Test
    void subscribeDoesNotReplayAnAlreadyScriptedTransaction() {
        FakeChainAdapter adapter = new FakeChainAdapter(Chain.ETHEREUM);
        adapter.scriptTx(TX_HASH, tx(true, 3, 100L));

        List<TxResult> received = new ArrayList<>();
        adapter.subscribeAddress(FROM, received::add);

        assertThat(received).isEmpty();
    }

    @Test
    void cancelledSubscriptionReceivesNoFurtherObservations() {
        FakeChainAdapter adapter = new FakeChainAdapter(Chain.ETHEREUM);
        adapter.scriptTx(TX_HASH, tx(true, 3, 100L));

        List<TxResult> received = new ArrayList<>();
        Subscription subscription = adapter.subscribeAddress(FROM, received::add);
        subscription.cancel();

        adapter.simulateReorg(TX_HASH, tx(true, 1, 99L));

        assertThat(received).isEmpty();
    }
}
