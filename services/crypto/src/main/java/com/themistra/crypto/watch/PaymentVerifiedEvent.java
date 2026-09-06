package com.themistra.crypto.watch;

import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

/**
 * A payment was verified by quorum agreement.
 *
 * <p>Published to subscribers when a watch finds its transaction on chain, confirmed by 2-of-3
 * providers. Immutable and timestamped for audit.
 */
public record PaymentVerifiedEvent(
        UUID watchId,
        String txHash,
        long blockNumber,
        String blockHash,
        BigInteger observedAmount,
        String chainId,
        Instant verifiedAt
) {
    public PaymentVerifiedEvent {
        if (watchId == null) throw new IllegalArgumentException("watchId required");
        if (txHash == null || txHash.isBlank()) throw new IllegalArgumentException("txHash required");
        if (blockHash == null || blockHash.isBlank()) throw new IllegalArgumentException("blockHash required");
        if (observedAmount == null || observedAmount.signum() <= 0)
            throw new IllegalArgumentException("observedAmount must be positive");
        if (chainId == null || chainId.isBlank()) throw new IllegalArgumentException("chainId required");
        if (verifiedAt == null) throw new IllegalArgumentException("verifiedAt required");
    }
}
