package com.themistra.crypto.adapter;

import java.time.Instant;
import java.util.function.LongUnaryOperator;

/** Finds where a {@link HistoricalSubscriber} subscription starts scanning. */
public final class StartBlocks {

    private StartBlocks() {
    }

    /**
     * @param headBlock the provider's current head block
     * @param since the earliest instant whose blocks must be scanned
     * @param timestampMillisOf the block timestamp, in epoch milliseconds, of a given block number
     * @return the first block whose timestamp is at or after {@code since}, or {@code headBlock + 1}
     *     if no such block has been mined yet. Block timestamps are non-decreasing, so this is a
     *     binary search costing about log2(headBlock) block reads, paid once per subscription.
     */
    public static long firstBlockAtOrAfter(long headBlock, Instant since, LongUnaryOperator timestampMillisOf) {
        long sinceMillis = since.toEpochMilli();
        if (timestampMillisOf.applyAsLong(headBlock) < sinceMillis) {
            return headBlock + 1;
        }
        long low = 0;
        long high = headBlock; // invariant: timestamp(high) >= since
        while (low < high) {
            long mid = low + (high - low) / 2;
            if (timestampMillisOf.applyAsLong(mid) >= sinceMillis) {
                high = mid;
            } else {
                low = mid + 1;
            }
        }
        return low;
    }
}
