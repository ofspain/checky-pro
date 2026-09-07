package com.themistra.crypto.watch;

import org.junit.jupiter.api.Test;

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
}
