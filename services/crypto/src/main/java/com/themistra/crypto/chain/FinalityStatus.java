package com.themistra.crypto.chain;

/**
 * Where a transaction sits against its chain's finality rule (ARCHITECTURE §6.2).
 *
 * <p>Finality is a per-chain policy, not a global confirmation count: Ethereum uses the
 * beacon-chain finalized checkpoint, Tron a solidified block. The adapter reports the raw
 * position; the policy decides what it means.
 *
 * @param confirmations how deep the transaction is, as this provider sees it
 * @param finalized     whether the chain considers it irreversible
 */
public record FinalityStatus(long confirmations, boolean finalized) {

    public FinalityStatus {
        if (confirmations < 0) {
            throw new IllegalArgumentException("confirmations must not be negative");
        }
    }

    public static FinalityStatus pending(long confirmations) {
        return new FinalityStatus(confirmations, false);
    }

    public static FinalityStatus finalizedAt(long confirmations) {
        return new FinalityStatus(confirmations, true);
    }
}
