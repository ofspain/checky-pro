package com.themistra.crypto.chain;

import java.math.BigInteger;

/**
 * What one provider says about a watched transfer at a point in time.
 *
 * <p>Amounts are the chain's base units, never a converted decimal: a {@code uint256} does not
 * survive a double, and moving the {@code decimals} lookup in here would let a wrong answer
 * silently corrupt a money value rather than fail loudly. Consumers build their own
 * {@link java.math.BigDecimal}.
 *
 * <p>Tokens are identified by contract address only — never by symbol (ARCHITECTURE §6.3).
 */
public record TxObservation(
        ChainId chainId,
        String txHash,
        long blockNumber,
        String blockHash,
        String fromAddress,
        String toAddress,
        String tokenAddress,
        BigInteger amount,
        int decimals
) {

    public TxObservation {
        if (chainId == null) {
            throw new IllegalArgumentException("chainId is required");
        }
        if (txHash == null || txHash.isBlank()) {
            throw new IllegalArgumentException("txHash is required");
        }
        if (amount == null) {
            throw new IllegalArgumentException("amount is required");
        }
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
        if (decimals < 0) {
            throw new IllegalArgumentException("decimals must not be negative");
        }
    }

    /**
     * The subset of this observation that providers must agree on for quorum (ARCHITECTURE §6.1:
     * tx existence, amount, token contract, block placement).
     *
     * <p>Confirmation count is deliberately excluded — it advances between two providers' replies
     * for entirely benign reasons, and treating that as disagreement would hold every honest
     * transaction. Confirmations are compared by the finality policy instead (§6.2).
     */
    public Fact fact() {
        return new Fact(chainId, txHash, blockNumber, blockHash,
                fromAddress, toAddress, tokenAddress, amount, decimals);
    }

    /** Value-equal projection used to group provider answers. */
    public record Fact(
            ChainId chainId,
            String txHash,
            long blockNumber,
            String blockHash,
            String fromAddress,
            String toAddress,
            String tokenAddress,
            BigInteger amount,
            int decimals
    ) {
    }
}
