package com.themistra.crypto.watch.dto;

import com.themistra.crypto.watch.Watch;
import com.themistra.crypto.watch.WatchStatus;

import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

/**
 * Outbound view of a watch: external UUID only, never the internal id.
 *
 * <p>Addresses are returned in the normalised form they are matched by, so a caller comparing what
 * it sent against what we hold sees the same value we will compare an observation against.
 */
public record WatchResponse(
        UUID watchUuid,
        String callerReference,
        String chainId,
        String recipientAddress,
        String tokenAddress,
        BigInteger expectedAmount,
        Instant expiresAt,
        WatchStatus status,
        Instant createdAt
) {

    public static WatchResponse from(Watch watch) {
        return new WatchResponse(
                watch.getWatchUuid(),
                watch.getCallerReference(),
                watch.getChainId(),
                watch.getRecipientAddress(),
                watch.getTokenAddress(),
                watch.getExpectedAmount(),
                watch.getExpiresAt(),
                watch.getStatus(),
                watch.getCreatedAt());
    }
}
