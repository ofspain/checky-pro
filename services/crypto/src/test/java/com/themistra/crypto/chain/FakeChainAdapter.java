package com.themistra.crypto.chain;

import java.math.BigInteger;
import java.util.Optional;

/**
 * A provider you can make say anything, including nothing.
 *
 * <p>This exists because the two behaviours that most need testing — provider disagreement and
 * provider failure — cannot be produced on demand against a real chain. Substituting at the
 * adapter boundary is the reason {@link ChainAdapter} carries no provider-specific concepts.
 */
public final class FakeChainAdapter implements ChainAdapter {

    private static final ChainId ETHEREUM = ChainId.evm(1);

    private final String label;
    private final Optional<TxObservation> answer;
    private final RuntimeException failure;

    private FakeChainAdapter(String label, Optional<TxObservation> answer, RuntimeException failure) {
        this.label = label;
        this.answer = answer;
        this.failure = failure;
    }

    public static FakeChainAdapter seeing(String label, TxObservation observation) {
        return new FakeChainAdapter(label, Optional.of(observation), null);
    }

    /** Answers honestly that it does not see the transaction. A vote, not a failure. */
    public static FakeChainAdapter blind(String label) {
        return new FakeChainAdapter(label, Optional.empty(), null);
    }

    /** Cannot answer at all. Not a vote in either direction. */
    public static FakeChainAdapter broken(String label, String reason) {
        return new FakeChainAdapter(label, Optional.empty(), new IllegalStateException(reason));
    }

    /** A representative USDC-shaped transfer on Ethereum: 1.5 units at 6 decimals. */
    public static TxObservation transfer() {
        return transferOf(new BigInteger("1500000"));
    }

    public static TxObservation transferOf(BigInteger amount) {
        return new TxObservation(
                ETHEREUM,
                "0xabc123",
                18_000_000L,
                "0xblockA",
                "0xpayer",
                "0xmerchant",
                "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
                amount,
                6);
    }

    /** The same transfer as seen in a different block — what a reorg produces. */
    public static TxObservation transferInBlock(long blockNumber, String blockHash) {
        TxObservation base = transfer();
        return new TxObservation(
                base.chainId(), base.txHash(), blockNumber, blockHash,
                base.fromAddress(), base.toAddress(), base.tokenAddress(),
                base.amount(), base.decimals());
    }

    @Override
    public String providerLabel() {
        return label;
    }

    @Override
    public ChainId chainId() {
        return ETHEREUM;
    }

    @Override
    public Optional<TxObservation> getTx(String txHash) {
        if (failure != null) {
            throw failure;
        }
        return answer;
    }

    @Override
    public Optional<TokenInfo> getTokenInfo(String tokenAddress) {
        if (failure != null) {
            throw failure;
        }
        return Optional.of(new TokenInfo(ETHEREUM, tokenAddress, 6, "USDC"));
    }

    @Override
    public Optional<FinalityStatus> getFinalityStatus(String txHash) {
        if (failure != null) {
            throw failure;
        }
        return answer.map(observation -> FinalityStatus.pending(3));
    }
}
