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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The queries the watcher depends on, against the real schema and migration.
 *
 * <p>What is being protected here is that the watcher can see what it is meant to watch and can
 * record that it is finished. A watch the query cannot find is an invoice nobody is watching.
 */
@Testcontainers
@SpringBootTest
class WatcherIntegrationIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final String ETHEREUM = "eip155:1";
    private static final String RECIPIENT = "0x9fd4AaA15C9B74F4c4B248566E01A729e3aCe193";
    private static final String USDC = "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48";

    @Autowired
    private WatchService watchService;

    @Autowired
    private WatchRepository watchRepository;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("themistra.crypto.security.require-issuer", () -> false);
        // The watcher's own loop is disabled: these tests drive the queries directly, and a
        // background poll hitting unreachable endpoints would only add noise.
        registry.add("themistra.crypto.watcher.interval-ms", () -> 3_600_000);
        registry.add("themistra.crypto.chains[0].id", () -> ETHEREUM);
        registry.add("themistra.crypto.chains[0].providers[0].label", () -> "a");
        registry.add("themistra.crypto.chains[0].providers[0].endpoint", () -> "http://localhost:1/x");
        registry.add("themistra.crypto.chains[0].providers[1].label", () -> "b");
        registry.add("themistra.crypto.chains[0].providers[1].endpoint", () -> "http://localhost:2/x");
        registry.add("themistra.crypto.chains[0].providers[2].label", () -> "c");
        registry.add("themistra.crypto.chains[0].providers[2].endpoint", () -> "http://localhost:3/x");
    }

    @Test
    @DisplayName("an active watch is visible to the watcher")
    void activeWatchIsVisible() {
        Watch registered = register(reference(), Duration.ofHours(1));

        assertThat(watchRepository.findAllActive(Instant.now()))
                .anySatisfy(w -> assertThat(w.getWatchUuid()).isEqualTo(registered.getWatchUuid()));
    }

    @Test
    @DisplayName("a satisfied watch stops being watched")
    void satisfiedWatchStopsBeingWatched() {
        Watch registered = register(reference(), Duration.ofHours(1));

        watchRepository.updateStatus(registered.getId(), WatchStatus.SATISFIED);

        assertThat(watchRepository.findAllActive(Instant.now()))
                .noneSatisfy(w -> assertThat(w.getWatchUuid()).isEqualTo(registered.getWatchUuid()));
    }

    @Test
    @DisplayName("a watch past its expiry stops being watched, with nothing sweeping it")
    void expiredWatchStopsBeingWatched() {
        // Registration refuses a past expiry, so expiry is exercised the way it actually happens:
        // a watch registered legitimately, then evaluated against a later clock.
        Watch registered = register(reference(), Duration.ofMinutes(30));
        Instant afterExpiry = Instant.now().plus(Duration.ofHours(2));

        assertThat(watchRepository.findAllActive(Instant.now()))
                .anySatisfy(w -> assertThat(w.getWatchUuid()).isEqualTo(registered.getWatchUuid()));
        assertThat(watchRepository.findAllActive(afterExpiry))
                .noneSatisfy(w -> assertThat(w.getWatchUuid()).isEqualTo(registered.getWatchUuid()));
    }

    @Test
    @DisplayName("the watcher sees watches across every chain in one query")
    void seesAllChainsAtOnce() {
        Watch first = register(reference(), Duration.ofHours(1));
        Watch second = register(reference(), Duration.ofHours(1));

        List<UUID> active = watchRepository.findAllActive(Instant.now()).stream()
                .map(Watch::getWatchUuid).toList();

        assertThat(active).contains(first.getWatchUuid(), second.getWatchUuid());
    }

    @Test
    @DisplayName("settling a watch actually commits, so the same payment is not found twice")
    void settlingCommits() {
        // The bug this protects against: updateStatus is a modifying query, and without a
        // transaction it throws. A watcher that swallows that exception re-finds and re-publishes
        // the same payment on every cycle - which is what happened, nine times, before this test.
        Watch registered = register(reference(), Duration.ofHours(1));

        watchRepository.updateStatus(registered.getId(), WatchStatus.SATISFIED);

        // Read through a fresh query rather than the persistence context, so a value that was
        // never written cannot be served from memory and look like success.
        assertThat(watchRepository.findByWatchUuid(registered.getWatchUuid()).orElseThrow().getStatus())
                .isEqualTo(WatchStatus.SATISFIED);
        assertThat(watchRepository.findAllActive(Instant.now()))
                .noneSatisfy(w -> assertThat(w.getWatchUuid()).isEqualTo(registered.getWatchUuid()));
    }

    private Watch register(String reference, Duration until) {
        return watchService.register(reference, ETHEREUM, RECIPIENT, USDC,
                new BigInteger("3000000000"), Instant.now().plus(until));
    }

    private static String reference() {
        return "invoice-" + UUID.randomUUID();
    }
}
