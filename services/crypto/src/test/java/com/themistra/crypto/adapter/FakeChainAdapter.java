package com.themistra.crypto.adapter;

import com.themistra.crypto.adapter.model.FinalityStatus;
import com.themistra.crypto.adapter.model.Subscription;
import com.themistra.crypto.adapter.model.TokenInfo;
import com.themistra.crypto.adapter.model.TxResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A scripted, in-memory {@code ChainAdapter} for unit tests (agents.md: "real RPC providers are
 * never called in tests or CI"). Multiple instances scripted with matching/mismatching data for the
 * same {@code txHash} are how "agree"/"disagree" are exercised in a quorum test — there is no special
 * mode for either on a single instance. "Lag" is likewise not a special mode: script a
 * {@code TxResult} with {@code exists=false}, or a lower {@code confirmations}/{@code blockNumber}
 * than another instance, to represent a provider behind head.
 *
 * <p>{@code scriptTx}/{@code scriptTokenInfo}/{@code scriptFinalityStatus} only set what the next
 * query returns — they never push to an active subscription. {@link #simulateReorg} is the only
 * method that both re-scripts a transaction's answer AND pushes the new state to every subscription
 * whose watched address matches the new result's {@code fromAddress}/{@code toAddress}.</p>
 */
public class FakeChainAdapter implements ChainAdapter {

    private final Chain chain;
    private final Map<String, TxResult> scriptedTx = new HashMap<>();
    private final Map<String, TokenInfo> scriptedTokenInfo = new HashMap<>();
    private final Map<String, FinalityStatus> scriptedFinalityStatus = new HashMap<>();
    private final List<ActiveSubscription> activeSubscriptions = new ArrayList<>();

    public FakeChainAdapter(Chain chain) {
        this.chain = chain;
    }

    public FakeChainAdapter scriptTx(String txHash, TxResult result) {
        scriptedTx.put(txHash, result);
        return this;
    }

    public FakeChainAdapter scriptTokenInfo(String contractAddress, TokenInfo info) {
        scriptedTokenInfo.put(contractAddress, info);
        return this;
    }

    public FakeChainAdapter scriptFinalityStatus(String txHash, FinalityStatus status) {
        scriptedFinalityStatus.put(txHash, status);
        return this;
    }

    /** Re-scripts {@code txHash} and pushes {@code newResult} to every subscription whose address
     * matches its {@code fromAddress}/{@code toAddress} — the reorg simulation. */
    public void simulateReorg(String txHash, TxResult newResult) {
        scriptedTx.put(txHash, newResult);
        pushToMatchingSubscriptions(newResult);
    }

    @Override
    public Chain chain() {
        return chain;
    }

    @Override
    public TxResult getTx(String txHash) {
        TxResult result = scriptedTx.get(txHash);
        if (result == null) {
            throw new IllegalStateException("No scripted TxResult for txHash: " + txHash);
        }
        return result;
    }

    @Override
    public TokenInfo getTokenInfo(String contractAddress) {
        TokenInfo info = scriptedTokenInfo.get(contractAddress);
        if (info == null) {
            throw new IllegalStateException("No scripted TokenInfo for contractAddress: " + contractAddress);
        }
        return info;
    }

    @Override
    public Subscription subscribeAddress(String address, ObservationSink sink) {
        ActiveSubscription subscription = new ActiveSubscription(address, sink);
        activeSubscriptions.add(subscription);
        return () -> activeSubscriptions.remove(subscription);
    }

    @Override
    public FinalityStatus getFinalityStatus(String txHash) {
        FinalityStatus status = scriptedFinalityStatus.get(txHash);
        if (status == null) {
            throw new IllegalStateException("No scripted FinalityStatus for txHash: " + txHash);
        }
        return status;
    }

    private void pushToMatchingSubscriptions(TxResult result) {
        for (ActiveSubscription subscription : activeSubscriptions) {
            boolean matches = subscription.address().equals(result.fromAddress())
                    || subscription.address().equals(result.toAddress());
            if (matches) {
                subscription.sink().onObservation(result);
            }
        }
    }

    private record ActiveSubscription(String address, ObservationSink sink) {
    }
}
