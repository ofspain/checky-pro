package com.themistra.crypto.watch;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class ChainCursorTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void placeholderAssignsTheSentinelLastBlockAndNullLastFinalizedBlock() {
        UUID watchId = UUID.randomUUID();

        ChainCursor cursor = ChainCursor.placeholder("ETHEREUM", watchId, NOW);

        assertThat(cursor.chain()).isEqualTo("ETHEREUM");
        assertThat(cursor.watchId()).isEqualTo(watchId);
        assertThat(cursor.lastBlock()).isEqualTo(-1L);
        assertThat(cursor.lastFinalizedBlock()).isNull();
        assertThat(cursor.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void placeholderRejectsANullChain() {
        assertThatNullPointerException().isThrownBy(() -> ChainCursor.placeholder(null, UUID.randomUUID(), NOW));
    }

    @Test
    void placeholderRejectsANullWatchId() {
        assertThatNullPointerException().isThrownBy(() -> ChainCursor.placeholder("ETHEREUM", null, NOW));
    }

    @Test
    void advanceToMovesLastBlockForwardWhenGivenAHigherBlockNumber() {
        // Phase 11 Finding 7: no test anywhere previously exercised advanceTo directly.
        ChainCursor cursor = ChainCursor.placeholder("ETHEREUM", UUID.randomUUID(), NOW);
        Instant later = NOW.plusSeconds(60);

        cursor.advanceTo(500L, later);

        assertThat(cursor.lastBlock()).isEqualTo(500L);
        assertThat(cursor.updatedAt()).isEqualTo(later);
    }

    @Test
    void advanceToIsANoOpWhenGivenABlockNumberAtOrBelowTheCurrentLastBlock() {
        // AC6: lastBlock only ever increases.
        ChainCursor cursor = ChainCursor.placeholder("ETHEREUM", UUID.randomUUID(), NOW);
        cursor.advanceTo(1000L, NOW.plusSeconds(60));

        cursor.advanceTo(500L, NOW.plusSeconds(120));
        assertThat(cursor.lastBlock()).isEqualTo(1000L);

        cursor.advanceTo(1000L, NOW.plusSeconds(180));
        assertThat(cursor.lastBlock()).isEqualTo(1000L);
    }

    // ---------- T17: recordSeenTransaction ----------

    @Test
    void recordSeenTransactionCapturesTheTxHashAmountAndAddresses() {
        ChainCursor cursor = ChainCursor.placeholder("ETHEREUM", UUID.randomUUID(), NOW);
        Instant seenAt = NOW.plusSeconds(30);

        cursor.recordSeenTransaction("0xabc", BigDecimal.valueOf(500), "0xfrom", "0xto", seenAt);

        assertThat(cursor.txHash()).isEqualTo("0xabc");
        assertThat(cursor.amount()).isEqualByComparingTo(BigDecimal.valueOf(500));
        assertThat(cursor.fromAddress()).isEqualTo("0xfrom");
        assertThat(cursor.toAddress()).isEqualTo("0xto");
        assertThat(cursor.updatedAt()).isEqualTo(seenAt);
    }

    @Test
    void recordSeenTransactionIsWriteOnce() {
        // T17 frozen brief: a watch that legitimately observes a second, distinct transaction after
        // its first keeps only the first snapshot - a disclosed, accepted limitation, not a defect.
        ChainCursor cursor = ChainCursor.placeholder("ETHEREUM", UUID.randomUUID(), NOW);
        cursor.recordSeenTransaction("0xfirst", BigDecimal.TEN, "0xfromA", "0xtoA", NOW.plusSeconds(30));

        cursor.recordSeenTransaction("0xsecond", BigDecimal.valueOf(999), "0xfromB", "0xtoB",
                NOW.plusSeconds(60));

        assertThat(cursor.txHash()).isEqualTo("0xfirst");
        assertThat(cursor.amount()).isEqualByComparingTo(BigDecimal.TEN);
        assertThat(cursor.fromAddress()).isEqualTo("0xfromA");
        assertThat(cursor.toAddress()).isEqualTo("0xtoA");
    }

    @Test
    void recordSeenTransactionRejectsANullTxHash() {
        ChainCursor cursor = ChainCursor.placeholder("ETHEREUM", UUID.randomUUID(), NOW);

        assertThatNullPointerException().isThrownBy(
                () -> cursor.recordSeenTransaction(null, BigDecimal.TEN, "0xfrom", "0xto", NOW));
    }

    // ---------- T17: advanceFinalizedTo ----------

    @Test
    void advanceFinalizedToSetsTheFinalizedBlockFromTheNullSentinel() {
        ChainCursor cursor = ChainCursor.placeholder("ETHEREUM", UUID.randomUUID(), NOW);
        Instant finalizedAt = NOW.plusSeconds(90);

        cursor.advanceFinalizedTo(777L, finalizedAt);

        assertThat(cursor.lastFinalizedBlock()).isEqualTo(777L);
        assertThat(cursor.updatedAt()).isEqualTo(finalizedAt);
    }

    @Test
    void advanceFinalizedToIsANoOpWhenGivenABlockNumberAtOrBelowTheCurrentValue() {
        // AC8: lastFinalizedBlock only ever increases.
        ChainCursor cursor = ChainCursor.placeholder("ETHEREUM", UUID.randomUUID(), NOW);
        cursor.advanceFinalizedTo(1000L, NOW.plusSeconds(60));

        cursor.advanceFinalizedTo(500L, NOW.plusSeconds(120));
        assertThat(cursor.lastFinalizedBlock()).isEqualTo(1000L);

        cursor.advanceFinalizedTo(1000L, NOW.plusSeconds(180));
        assertThat(cursor.lastFinalizedBlock()).isEqualTo(1000L);
    }

    // ---------- T18: invalidate ----------

    @Test
    void invalidateResetsEveryForwardDerivedFieldFromAFullyPopulatedState() {
        // L6: "No forward-derived state survives a reorg that invalidates it."
        ChainCursor cursor = ChainCursor.placeholder("ETHEREUM", UUID.randomUUID(), NOW);
        cursor.advanceTo(500L, NOW.plusSeconds(30));
        cursor.recordSeenTransaction("0xabc", BigDecimal.TEN, "0xfrom", "0xto", NOW.plusSeconds(60));
        cursor.advanceFinalizedTo(450L, NOW.plusSeconds(90));
        Instant invalidatedAt = NOW.plusSeconds(120);

        cursor.invalidate(invalidatedAt);

        assertThat(cursor.lastBlock()).isEqualTo(-1L);
        assertThat(cursor.lastFinalizedBlock()).isNull();
        assertThat(cursor.txHash()).isNull();
        assertThat(cursor.amount()).isNull();
        assertThat(cursor.fromAddress()).isNull();
        assertThat(cursor.toAddress()).isNull();
        assertThat(cursor.updatedAt()).isEqualTo(invalidatedAt);
    }

    @Test
    void invalidateOnAFreshPlaceholderIsANoOpBeyondUpdatingTheTimestamp() {
        ChainCursor cursor = ChainCursor.placeholder("ETHEREUM", UUID.randomUUID(), NOW);
        Instant invalidatedAt = NOW.plusSeconds(30);

        cursor.invalidate(invalidatedAt);

        assertThat(cursor.lastBlock()).isEqualTo(-1L);
        assertThat(cursor.lastFinalizedBlock()).isNull();
        assertThat(cursor.txHash()).isNull();
        assertThat(cursor.updatedAt()).isEqualTo(invalidatedAt);
    }

    @Test
    void aTransactionCanBeRecordedAgainAfterInvalidate() {
        // Write-once (recordSeenTransaction) applies only until the cursor is invalidated - invalidate
        // is the sole way to make room for a genuinely new transaction on this watch.
        ChainCursor cursor = ChainCursor.placeholder("ETHEREUM", UUID.randomUUID(), NOW);
        cursor.recordSeenTransaction("0xfirst", BigDecimal.ONE, "0xfromA", "0xtoA", NOW.plusSeconds(30));
        cursor.invalidate(NOW.plusSeconds(60));

        cursor.recordSeenTransaction("0xsecond", BigDecimal.TEN, "0xfromB", "0xtoB", NOW.plusSeconds(90));

        assertThat(cursor.txHash()).isEqualTo("0xsecond");
        assertThat(cursor.amount()).isEqualByComparingTo(BigDecimal.TEN);
    }
}
