package com.themistra.crypto.adapter;

import com.themistra.crypto.adapter.model.Subscription;
import com.themistra.crypto.adapter.model.TxResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
    void getTxReturnsExistsFalseForAnUnobservedTransactionRatherThanThrowing() {
        // Phase 11 Gap 1: makes the Phase 9 documentation contract executable, not just Javadoc.
        FakeChainAdapter adapter = new FakeChainAdapter(Chain.ETHEREUM).scriptTx(TX_HASH, tx(false, 0, 0L));

        TxResult result = adapter.getTx(TX_HASH);

        assertThat(result.exists()).isFalse();
    }

    @Test
    void chainReturnsTheValuePassedToTheConstructor() {
        assertThat(new FakeChainAdapter(Chain.ETHEREUM).chain()).isEqualTo(Chain.ETHEREUM);
        assertThat(new FakeChainAdapter(Chain.TRON).chain()).isEqualTo(Chain.TRON);
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
    void reorgPushesToASubscriptionOnTheToAddressToo() {
        // Phase 11 Gap 5: the positive case above only exercised fromAddress matching.
        FakeChainAdapter adapter = new FakeChainAdapter(Chain.ETHEREUM);
        adapter.scriptTx(TX_HASH, tx(true, 3, 100L));

        List<TxResult> received = new ArrayList<>();
        adapter.subscribeAddress(TO, received::add);

        TxResult reorged = tx(true, 1, 99L);
        adapter.simulateReorg(TX_HASH, reorged);

        assertThat(received).containsExactly(reorged);
    }

    @Test
    void reorgPushesToEveryMatchingSubscriptionNotJustTheFirst() {
        // Phase 11 Gap 6: guards against an accidental early return/break in the push loop.
        FakeChainAdapter adapter = new FakeChainAdapter(Chain.ETHEREUM);
        adapter.scriptTx(TX_HASH, tx(true, 3, 100L));

        List<TxResult> receivedByFirst = new ArrayList<>();
        List<TxResult> receivedBySecond = new ArrayList<>();
        adapter.subscribeAddress(FROM, receivedByFirst::add);
        adapter.subscribeAddress(FROM, receivedBySecond::add);

        TxResult reorged = tx(true, 1, 99L);
        adapter.simulateReorg(TX_HASH, reorged);

        assertThat(receivedByFirst).containsExactly(reorged);
        assertThat(receivedBySecond).containsExactly(reorged);
    }

    @Test
    void reorgCanPushAnExistsFalseResultRepresentingAnInvalidatedTransaction() {
        // Phase 11 Gap 7: a reorg can remove a previously-observed transaction entirely - the
        // signal task 16's reorg handling will need to react to.
        FakeChainAdapter adapter = new FakeChainAdapter(Chain.ETHEREUM);
        adapter.scriptTx(TX_HASH, tx(true, 3, 100L));

        List<TxResult> received = new ArrayList<>();
        adapter.subscribeAddress(FROM, received::add);

        TxResult invalidated = tx(false, 0, 0L);
        adapter.simulateReorg(TX_HASH, invalidated);

        assertThat(adapter.getTx(TX_HASH).exists()).isFalse();
        assertThat(received).containsExactly(invalidated);
    }

    @Test
    void scriptTxAfterSubscriptionIsActiveDoesNotPush() {
        // Phase 11 Gap 8: only simulateReorg pushes; scriptTx only ever configures the next
        // query's answer, even when called while a subscription is live.
        FakeChainAdapter adapter = new FakeChainAdapter(Chain.ETHEREUM);

        List<TxResult> received = new ArrayList<>();
        adapter.subscribeAddress(FROM, received::add);

        adapter.scriptTx(TX_HASH, tx(true, 3, 100L));

        assertThat(received).isEmpty();
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

    @Test
    void cancelIsIdempotent() {
        // Phase 11 Gap 9: makes the Phase 9 Javadoc contract ("a second or later call is a no-op")
        // executable.
        FakeChainAdapter adapter = new FakeChainAdapter(Chain.ETHEREUM);
        adapter.scriptTx(TX_HASH, tx(true, 3, 100L));

        List<TxResult> received = new ArrayList<>();
        Subscription subscription = adapter.subscribeAddress(FROM, received::add);

        assertThatCode(() -> {
            subscription.cancel();
            subscription.cancel();
        }).doesNotThrowAnyException();

        adapter.simulateReorg(TX_HASH, tx(true, 1, 99L));
        assertThat(received).isEmpty();
    }
}
