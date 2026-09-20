package com.themistra.crypto.chain;

import java.util.List;
import java.util.Optional;

/**
 * One provider's view of one chain (ARCHITECTURE §3.4).
 *
 * <p>An adapter is deliberately dumb: it answers questions and never decides what is true. No
 * single adapter's answer leaves this service as fact — the quorum layer compares several
 * (§6.1). That is also why the provider boundary is the substitution seam for tests: agreement,
 * disagreement, and provider failure are all reachable without a network.
 *
 * <p>Address subscription belongs to the watcher layer and is not part of this interface yet. A
 * subscription is only ever a <em>trigger</em> to go and establish a fact by quorum, never an
 * observation in its own right.
 */
public interface ChainAdapter {

    /**
     * Stable, non-sensitive label for this provider, e.g. {@code evm-provider-a}. Never an
     * endpoint URL or a credential — this value reaches the observation log and published events.
     */
    String providerLabel();

    /** The chain this adapter speaks to. */
    ChainId chainId();

    /** The transfer at this hash, or empty if this provider does not see it. */
    Optional<TxObservation> getTx(String txHash);

    /** Decimals and contract metadata for a token, by contract address (§6.3). */
    Optional<TokenInfo> getTokenInfo(String tokenAddress);

    /**
     * Transaction hashes of incoming transfers of {@code tokenAddress} to {@code recipient}
     * between two blocks, per this provider.
     *
     * <p>The watcher's problem is the inverse of {@link #getTx(String)}: it must find a payment
     * whose hash nobody knows yet. This returns candidates only — a hash here is one provider's
     * claim, not a fact. The caller still puts each candidate through quorum before believing it.
     *
     * <p>The block range is bounded by the caller because providers cap how wide a log query may
     * be, and the caps differ between them.
     */
    List<String> findIncomingTransfers(String recipient, String tokenAddress, long fromBlock, long toBlock);

    /** The current head block height, per this provider. */
    long currentBlockNumber();

    /** How deep the transaction is, per this provider. */
    Optional<FinalityStatus> getFinalityStatus(String txHash);
}
