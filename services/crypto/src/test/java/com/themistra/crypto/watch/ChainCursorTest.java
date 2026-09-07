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
}
