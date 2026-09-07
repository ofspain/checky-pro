package com.themistra.crypto.watch;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class WatchTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant EXPIRES_AT = Instant.parse("2026-02-01T00:00:00Z");

    @Test
    void registerAssignsEveryFieldAndDefaultsToRegisteredStatus() {
        UUID watchId = UUID.randomUUID();
        UUID invoiceUuid = UUID.randomUUID();

        Watch watch = Watch.register(watchId, invoiceUuid, "ETHEREUM", "0xABC", "0xDEF",
                BigDecimal.valueOf(1_000_000L), EXPIRES_AT, NOW);

        assertThat(watch.watchId()).isEqualTo(watchId);
        assertThat(watch.invoiceUuid()).isEqualTo(invoiceUuid);
        assertThat(watch.chain()).isEqualTo("ETHEREUM");
        assertThat(watch.address()).isEqualTo("0xABC");
        assertThat(watch.tokenContractAddress()).isEqualTo("0xDEF");
        assertThat(watch.expectedAmount()).isEqualByComparingTo(BigDecimal.valueOf(1_000_000L));
        assertThat(watch.status()).isEqualTo(WatchStatus.REGISTERED);
        assertThat(watch.expiresAt()).isEqualTo(EXPIRES_AT);
        assertThat(watch.createdAt()).isEqualTo(NOW);
        assertThat(watch.unregisteredAt()).isNull();
    }

    @Test
    void registerRejectsANullWatchId() {
        assertThatNullPointerException().isThrownBy(() -> Watch.register(null, UUID.randomUUID(),
                "ETHEREUM", "0xABC", "0xDEF", BigDecimal.ONE, EXPIRES_AT, NOW));
    }

    @Test
    void registerRejectsANullChain() {
        assertThatNullPointerException().isThrownBy(() -> Watch.register(UUID.randomUUID(),
                UUID.randomUUID(), null, "0xABC", "0xDEF", BigDecimal.ONE, EXPIRES_AT, NOW));
    }
}
