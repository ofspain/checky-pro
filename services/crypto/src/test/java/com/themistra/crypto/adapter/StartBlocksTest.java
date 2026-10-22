package com.themistra.crypto.adapter;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class StartBlocksTest {

    /** Block n is mined at 1000 + 2n seconds (a 2-second chain, like the local anvil). */
    private static long timestampMillis(long block) {
        return (1_000 + 2 * block) * 1_000;
    }

    @Test
    void returnsTheFirstBlockMinedAtOrAfterTheInstant() {
        assertThat(StartBlocks.firstBlockAtOrAfter(500, Instant.ofEpochSecond(1_200), StartBlocksTest::timestampMillis))
                .isEqualTo(100); // mined exactly at 1200
        assertThat(StartBlocks.firstBlockAtOrAfter(500, Instant.ofEpochSecond(1_201), StartBlocksTest::timestampMillis))
                .isEqualTo(101); // first block after 1201
    }

    @Test
    void returnsGenesisWhenTheInstantIsBeforeTheChainStarted() {
        assertThat(StartBlocks.firstBlockAtOrAfter(500, Instant.ofEpochSecond(1), StartBlocksTest::timestampMillis))
                .isZero();
    }

    @Test
    void returnsTheBlockAfterTheHeadWhenNothingHasBeenMinedSinceTheInstant() {
        AtomicInteger reads = new AtomicInteger();
        long start = StartBlocks.firstBlockAtOrAfter(500, Instant.ofEpochSecond(5_000), block -> {
            reads.incrementAndGet();
            return timestampMillis(block);
        });

        assertThat(start).isEqualTo(501);
        assertThat(reads).hasValue(1); // only the head is read
    }

    @Test
    void handlesSeveralBlocksSharingOneTimestamp() {
        // Blocks 0-9 at t=0, 10-19 at t=10s, and so on: the first block of a tied run is returned.
        assertThat(StartBlocks.firstBlockAtOrAfter(99, Instant.ofEpochSecond(30), block -> (block / 10) * 10_000))
                .isEqualTo(30);
    }

    @Test
    void costsAboutLog2OfTheHeadInBlockReads() {
        AtomicInteger reads = new AtomicInteger();
        StartBlocks.firstBlockAtOrAfter(24_000_000, Instant.ofEpochSecond(1_000 + 2 * 23_999_000L), block -> {
            reads.incrementAndGet();
            return timestampMillis(block);
        });

        assertThat(reads.get()).isLessThanOrEqualTo(27);
    }
}
