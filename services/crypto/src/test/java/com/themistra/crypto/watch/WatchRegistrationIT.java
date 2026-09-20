package com.themistra.crypto.watch;

import com.themistra.crypto.chain.ChainId;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Watch registration against a real Postgres running the real migration.
 *
 * <p>Uses Testcontainers rather than an in-memory database on purpose: this exercises the actual
 * {@code chain} schema, its check constraints, and Hibernate validating against it. An in-memory
 * substitute diverges on schemas, numeric precision, and constraint behaviour, so a migration
 * could pass here and fail against RDS — which is the one thing this test exists to prevent.
 */
@Testcontainers
@SpringBootTest
class WatchRegistrationIT {

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

    /** One configured chain, so registration has something to validate against. */
    @DynamicPropertySource
    static void chains(DynamicPropertyRegistry registry) {
        // No issuer here: this test exercises registration behaviour, not who may call it.
        // WatchApiSecurityIT covers authorisation.
        registry.add("themistra.crypto.security.require-issuer", () -> false);
        registry.add("themistra.crypto.chains[0].id", () -> ETHEREUM);
        registry.add("themistra.crypto.chains[0].providers[0].label", () -> "evm-provider-a");
        registry.add("themistra.crypto.chains[0].providers[0].endpoint", () -> "http://localhost:1/unused");
        registry.add("themistra.crypto.chains[0].providers[1].label", () -> "evm-provider-b");
        registry.add("themistra.crypto.chains[0].providers[1].endpoint", () -> "http://localhost:2/unused");
        registry.add("themistra.crypto.chains[0].providers[2].label", () -> "evm-provider-c");
        registry.add("themistra.crypto.chains[0].providers[2].endpoint", () -> "http://localhost:3/unused");
    }

    @Test
    @DisplayName("a watch is registered and survives being read back")
    void registersWatch() {
        Watch watch = register(reference(), new BigInteger("1500000"));

        assertThat(watch.getWatchUuid()).isNotNull();
        assertThat(watch.getStatus()).isEqualTo(WatchStatus.ACTIVE);
        // stored normalised, so an observation matches regardless of the casing the caller sent
        assertThat(watch.getRecipientAddress()).isEqualTo(RECIPIENT.toLowerCase());
        assertThat(watchService.findByUuid(watch.getWatchUuid()).getExpectedAmount())
                .isEqualTo(new BigInteger("1500000"));
    }

    @Test
    @DisplayName("an amount beyond 64 bits round-trips through the database exactly")
    void largeAmountSurvivesPersistence() {
        BigInteger huge = BigInteger.TWO.pow(200);
        Watch watch = register(reference(), huge);

        assertThat(watchService.findByUuid(watch.getWatchUuid()).getExpectedAmount()).isEqualTo(huge);
    }

    @Test
    @DisplayName("retrying the same reference returns the same watch, not a second one")
    void registrationIsIdempotent() {
        String reference = reference();
        Watch first = register(reference, new BigInteger("1500000"));
        Watch second = register(reference, new BigInteger("1500000"));

        assertThat(second.getWatchUuid()).isEqualTo(first.getWatchUuid());
        assertThat(watchRepository.findAll())
                .filteredOn(w -> w.getCallerReference().equals(reference))
                .hasSize(1);
    }

    @Test
    @DisplayName("a reused reference with different terms is a conflict, not an overwrite")
    void contradictoryRetryIsConflict() {
        String reference = reference();
        register(reference, new BigInteger("1500000"));

        assertThatThrownBy(() -> register(reference, new BigInteger("9900000")))
                .isInstanceOf(WatchException.Conflict.class);

        assertThat(watchRepository.findByCallerReference(reference).orElseThrow().getExpectedAmount())
                .isEqualTo(new BigInteger("1500000"));
    }

    @Test
    @DisplayName("two references with identical terms are two watches")
    void differentReferencesCoexist() {
        Watch first = register(reference(), new BigInteger("1500000"));
        Watch second = register(reference(), new BigInteger("1500000"));

        assertThat(second.getWatchUuid()).isNotEqualTo(first.getWatchUuid());
    }

    @Test
    @DisplayName("a watch on an unconfigured chain is rejected")
    void unconfiguredChainRejected() {
        assertThatThrownBy(() -> watchService.register(reference(), "eip155:999", RECIPIENT, USDC,
                BigInteger.ONE, Instant.now().plus(Duration.ofHours(1))))
                .isInstanceOf(WatchException.Invalid.class)
                .hasMessageContaining("not configured");
    }

    @Test
    @DisplayName("a malformed recipient address is rejected")
    void malformedAddressRejected() {
        assertThatThrownBy(() -> watchService.register(reference(), ETHEREUM, "0xnope", USDC,
                BigInteger.ONE, Instant.now().plus(Duration.ofHours(1))))
                .isInstanceOf(WatchException.Invalid.class)
                .hasMessageContaining("recipientAddress");
    }

    @Test
    @DisplayName("a non-positive amount is rejected")
    void nonPositiveAmountRejected() {
        assertThatThrownBy(() -> register(reference(), BigInteger.ZERO))
                .isInstanceOf(WatchException.Invalid.class)
                .hasMessageContaining("positive");
    }

    @Test
    @DisplayName("an expiry in the past is rejected")
    void pastExpiryRejected() {
        assertThatThrownBy(() -> watchService.register(reference(), ETHEREUM, RECIPIENT, USDC,
                BigInteger.ONE, Instant.now().minusSeconds(1)))
                .isInstanceOf(WatchException.Invalid.class)
                .hasMessageContaining("future");
    }

    @Test
    @DisplayName("an expired watch drops out of the active set without anything sweeping it")
    void expiredWatchIsNotActive() {
        String reference = reference();
        watchService.register(reference, ETHEREUM, RECIPIENT, USDC,
                BigInteger.ONE, Instant.now().plusMillis(600));

        List<Watch> before = watchService.activeWatches(ChainId.evm(1));
        assertThat(before).anySatisfy(w -> assertThat(w.getCallerReference()).isEqualTo(reference));

        await(700);

        assertThat(watchService.activeWatches(ChainId.evm(1)))
                .noneSatisfy(w -> assertThat(w.getCallerReference()).isEqualTo(reference));
        // the row is untouched: expiry is evaluated on read, not written by a sweeper
        assertThat(watchRepository.findByCallerReference(reference).orElseThrow().getStatus())
                .isEqualTo(WatchStatus.ACTIVE);
    }

    @Test
    @DisplayName("cancelling twice succeeds and the watch leaves the active set")
    void cancellationIsIdempotent() {
        Watch watch = register(reference(), BigInteger.ONE);

        watchService.cancel(watch.getWatchUuid());
        watchService.cancel(watch.getWatchUuid());

        assertThat(watchService.findByUuid(watch.getWatchUuid()).getStatus())
                .isEqualTo(WatchStatus.CANCELLED);
        assertThat(watchService.activeWatches(ChainId.evm(1)))
                .noneSatisfy(w -> assertThat(w.getWatchUuid()).isEqualTo(watch.getWatchUuid()));
    }

    @Test
    @DisplayName("cancelling an unknown watch is not found, not success")
    void cancellingUnknownWatchFails() {
        assertThatThrownBy(() -> watchService.cancel(UUID.randomUUID()))
                .isInstanceOf(WatchException.NotFound.class);
    }

    @Test
    @DisplayName("a chain with no active watches returns empty, which is not an error")
    void emptyActiveSetIsNotAnError() {
        assertThat(watchService.activeWatches(ChainId.evm(1))).isNotNull();
    }

    private Watch register(String reference, BigInteger amount) {
        return watchService.register(reference, ETHEREUM, RECIPIENT, USDC, amount,
                Instant.now().plus(Duration.ofHours(1)));
    }

    private static String reference() {
        return "invoice-" + UUID.randomUUID();
    }

    private static void await(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
