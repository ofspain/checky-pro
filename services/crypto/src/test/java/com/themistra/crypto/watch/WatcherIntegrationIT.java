package com.themistra.crypto.watch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Watcher against a real Postgres: queries and marks watches.
 *
 * <p>The event publishing is stubbed (logged), but the watch retrieval and status update are
 * against the real schema and migration. This proves the database side of the watcher works.
 */
@Testcontainers
@SpringBootTest
class WatcherIntegrationIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private WatchService watchService;

    @Autowired
    private WatchRepository watchRepository;

    @DynamicPropertySource
    static void chains(DynamicPropertyRegistry registry) {
        registry.add("themistra.crypto.security.require-issuer", () -> false);
        registry.add("themistra.crypto.chains[0].id", () -> "eip155:1");
        registry.add("themistra.crypto.chains[0].providers[0].label", () -> "a");
        registry.add("themistra.crypto.chains[0].providers[0].endpoint", () -> "http://localhost:1/x");
        registry.add("themistra.crypto.chains[0].providers[1].label", () -> "b");
        registry.add("themistra.crypto.chains[0].providers[1].endpoint", () -> "http://localhost:2/x");
        registry.add("themistra.crypto.chains[0].providers[2].label", () -> "c");
        registry.add("themistra.crypto.chains[0].providers[2].endpoint", () -> "http://localhost:3/x");
    }

    @Test
    @DisplayName("watcher retrieves active watches from database")
    void retrievesActiveWatches() {
        Watch w = watchService.register("ref1", "eip155:1", "0x9fd4",
                "0xa0b8", BigInteger.ONE, Instant.now().plus(Duration.ofHours(1)));

        List<Watch> active = watchRepository.findAllActive(Instant.now());
        assertThat(active).anySatisfy(watch -> assertThat(watch.getWatchUuid()).isEqualTo(w.getWatchUuid()));
    }

    @Test
    @DisplayName("watcher marks watch as satisfied")
    void marksSatisfied() {
        Watch w = watchService.register("ref2", "eip155:1", "0x9fd4",
                "0xa0b8", BigInteger.ONE, Instant.now().plus(Duration.ofHours(1)));

        watchRepository.updateStatus(w.getId(), WatchStatus.SATISFIED);

        Watch updated = watchRepository.findByWatchUuid(w.getWatchUuid()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(WatchStatus.SATISFIED);
    }

    @Test
    @DisplayName("expired watches are not returned by findAllActive")
    void expiredExcluded() {
        watchService.register("ref3", "eip155:1", "0x9fd4",
                "0xa0b8", BigInteger.ONE, Instant.now().minusSeconds(1));

        List<Watch> active = watchRepository.findAllActive(Instant.now());
        assertThat(active).isEmpty();
    }
}
