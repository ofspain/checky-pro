package com.themistra.crypto.chain.evm;

import java.math.BigInteger;

/**
 * One decoded ERC-20 {@code Transfer} event.
 *
 * <p>A stablecoin payment is a log entry, not a native value transfer, so this — not the
 * transaction's {@code value} field — is what a merchant actually got paid.
 *
 * @param tokenAddress the contract that emitted the event; the token's only identity (§6.3)
 * @param from         payer, decoded from the first indexed topic
 * @param to           recipient, decoded from the second indexed topic
 * @param value        base units, exactly as the chain reports them
 */
public record Erc20Transfer(String tokenAddress, String from, String to, BigInteger value) {

    public Erc20Transfer {
        if (tokenAddress == null || tokenAddress.isBlank()) {
            throw new IllegalArgumentException("tokenAddress is required");
        }
        if (from == null || to == null) {
            throw new IllegalArgumentException("from and to are required");
        }
        if (value == null || value.signum() < 0) {
            throw new IllegalArgumentException("value must be present and non-negative");
        }
    }
}
