package com.themistra.crypto.chain;

/**
 * Token metadata, keyed by contract address.
 *
 * <p>A symbol is carried for display only and is never an identity: a token is identified by its
 * contract address checked against the signed canonical allowlist (ARCHITECTURE §6.3). Anything
 * off that list is {@code UNKNOWN_TOKEN} and surfaced loudly rather than guessed at.
 */
public record TokenInfo(ChainId chainId, String contractAddress, int decimals, String displaySymbol) {

    public TokenInfo {
        if (chainId == null) {
            throw new IllegalArgumentException("chainId is required");
        }
        if (contractAddress == null || contractAddress.isBlank()) {
            throw new IllegalArgumentException("contractAddress is required");
        }
        if (decimals < 0) {
            throw new IllegalArgumentException("decimals must not be negative");
        }
    }
}
