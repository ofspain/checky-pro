package com.themistra.crypto.watch;

import com.themistra.crypto.token.AddressValidator;
import com.themistra.crypto.watch.dto.RegisterWatchRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@link AddressValidator} is used as its real, pure implementation (not mocked) - it has no external
 * dependency, and exercising the real EIP-55/Base58Check logic is more meaningful than a stub. Valid
 * test vectors are reused from T12's own known-good ones. */
class WatchServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant FUTURE = NOW.plus(1, ChronoUnit.DAYS);
    private static final String VALID_EVM_ADDRESS = "0x5AEDA56215b167893e80B4fE645BA6d5Bab767DE";
    private static final String VALID_TRON_ADDRESS = "TA4Y62o6YC2Zsck9rZVGTvqW1AQ7X9zTnj";

    private WatchRepository watchRepository;
    private ChainCursorRepository chainCursorRepository;
    private WatchService service;

    @BeforeEach
    void setUp() {
        watchRepository = mock(WatchRepository.class);
        chainCursorRepository = mock(ChainCursorRepository.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new WatchService(watchRepository, chainCursorRepository, new AddressValidator(), clock);
    }

    private RegisterWatchRequest validRequest() {
        return new RegisterWatchRequest(UUID.randomUUID(), "ETHEREUM", VALID_EVM_ADDRESS,
                VALID_EVM_ADDRESS, "1000000", FUTURE);
    }

    @Test
    void shouldRegisterWatchAndReturnWatchId() {
        Watch watch = service.register(validRequest());

        assertThat(watch.watchId()).isNotNull();
        assertThat(watch.status()).isEqualTo(WatchStatus.REGISTERED);
        verify(watchRepository).save(watch);
    }

    @Test
    void registerPersistsAChainCursorWithTheMatchingWatchIdAndChain() {
        Watch watch = service.register(validRequest());

        ArgumentCaptor<ChainCursor> captor = ArgumentCaptor.forClass(ChainCursor.class);
        verify(chainCursorRepository).save(captor.capture());
        ChainCursor cursor = captor.getValue();
        assertThat(cursor.watchId()).isEqualTo(watch.watchId());
        assertThat(cursor.chain()).isEqualTo("ETHEREUM");
        assertThat(cursor.lastBlock()).isEqualTo(-1L);
        assertThat(cursor.lastFinalizedBlock()).isNull();
    }

    @Test
    void registerCallsChainCursorRepositorySaveExactlyOnce() {
        // T15 Phase 8 Finding 4: the schema enforces no 1:1 Watch<->ChainCursor relationship - this is
        // the application-level guarantee, locked in as an executable test.
        service.register(validRequest());

        verify(chainCursorRepository, times(1)).save(any());
    }

    @Test
    void twoSequentialRegisterCallsProduceTwoDistinctWatchIds() {
        // T15 Phase 3 Finding 3: documents the accepted POST non-idempotency risk in executable form.
        RegisterWatchRequest request = validRequest();

        Watch first = service.register(request);
        Watch second = service.register(request);

        assertThat(first.watchId()).isNotEqualTo(second.watchId());
        verify(watchRepository, times(2)).save(any());
        verify(chainCursorRepository, times(2)).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.5", "1e18", "-0", "0", "-5", "abc", "01", ""})
    void registerRejectsAMalformedExpectedAmount(String badAmount) {
        RegisterWatchRequest request = new RegisterWatchRequest(UUID.randomUUID(), "ETHEREUM",
                VALID_EVM_ADDRESS, VALID_EVM_ADDRESS, badAmount, FUTURE);

        assertThatExceptionOfType(InvalidWatchRequestException.class)
                .isThrownBy(() -> service.register(request));
    }

    @Test
    void registerRejectsAnExpectedAmountOverSeventyEightDigits() {
        // T15 Phase 8/9 Finding 2: matches expected_amount NUMERIC(78, 0)'s own precision cap.
        String seventyNineDigits = "1" + "0".repeat(78);
        RegisterWatchRequest request = new RegisterWatchRequest(UUID.randomUUID(), "ETHEREUM",
                VALID_EVM_ADDRESS, VALID_EVM_ADDRESS, seventyNineDigits, FUTURE);

        assertThatExceptionOfType(InvalidWatchRequestException.class)
                .isThrownBy(() -> service.register(request));
    }

    @Test
    void registerAcceptsAnExpectedAmountOfExactlySeventyEightDigits() {
        String seventyEightDigits = "1" + "0".repeat(77);
        RegisterWatchRequest request = new RegisterWatchRequest(UUID.randomUUID(), "ETHEREUM",
                VALID_EVM_ADDRESS, VALID_EVM_ADDRESS, seventyEightDigits, FUTURE);

        Watch watch = service.register(request);

        assertThat(watch.expectedAmount()).isEqualByComparingTo(new BigDecimal(seventyEightDigits));
    }

    @Test
    void registerRejectsAnExpiresAtOfExactlyNow() {
        RegisterWatchRequest request = new RegisterWatchRequest(UUID.randomUUID(), "ETHEREUM",
                VALID_EVM_ADDRESS, VALID_EVM_ADDRESS, "1000000", NOW);

        assertThatExceptionOfType(InvalidWatchRequestException.class)
                .isThrownBy(() -> service.register(request));
    }

    @Test
    void registerRejectsAPastExpiresAt() {
        RegisterWatchRequest request = new RegisterWatchRequest(UUID.randomUUID(), "ETHEREUM",
                VALID_EVM_ADDRESS, VALID_EVM_ADDRESS, "1000000", NOW.minusSeconds(1));

        assertThatExceptionOfType(InvalidWatchRequestException.class)
                .isThrownBy(() -> service.register(request));
    }

    @Test
    void registerAcceptsAnExpiresAtOneNanosecondInTheFuture() {
        RegisterWatchRequest request = new RegisterWatchRequest(UUID.randomUUID(), "ETHEREUM",
                VALID_EVM_ADDRESS, VALID_EVM_ADDRESS, "1000000", NOW.plusNanos(1));

        assertThat(service.register(request).status()).isEqualTo(WatchStatus.REGISTERED);
    }

    @Test
    void registerRejectsAStructurallyInvalidEvmAddress() {
        RegisterWatchRequest request = new RegisterWatchRequest(UUID.randomUUID(), "ETHEREUM",
                "0xnotanaddress", VALID_EVM_ADDRESS, "1000000", FUTURE);

        assertThatExceptionOfType(InvalidWatchRequestException.class)
                .isThrownBy(() -> service.register(request));
    }

    @Test
    void registerRejectsAStructurallyInvalidTokenContractAddress() {
        // Confirms tokenContractAddress is validated independently of address, not skipped.
        RegisterWatchRequest request = new RegisterWatchRequest(UUID.randomUUID(), "ETHEREUM",
                VALID_EVM_ADDRESS, "0xnotanaddress", "1000000", FUTURE);

        assertThatExceptionOfType(InvalidWatchRequestException.class)
                .isThrownBy(() -> service.register(request));
    }

    @Test
    void registerRejectsAnUncheckedLowercaseEvmAddress() {
        // L8's strict EIP-55 policy (T12) applies here too - an unchecksummed address is rejected, not
        // silently accepted.
        RegisterWatchRequest request = new RegisterWatchRequest(UUID.randomUUID(), "ETHEREUM",
                VALID_EVM_ADDRESS.toLowerCase(), VALID_EVM_ADDRESS, "1000000", FUTURE);

        assertThatExceptionOfType(InvalidWatchRequestException.class)
                .isThrownBy(() -> service.register(request));
    }

    @Test
    void registerAcceptsAValidTronAddress() {
        RegisterWatchRequest request = new RegisterWatchRequest(UUID.randomUUID(), "TRON",
                VALID_TRON_ADDRESS, VALID_TRON_ADDRESS, "1000000", FUTURE);

        assertThat(service.register(request).chain()).isEqualTo("TRON");
    }

    @Test
    void registerRejectsAStructurallyInvalidTronAddress() {
        RegisterWatchRequest request = new RegisterWatchRequest(UUID.randomUUID(), "TRON",
                "not-a-tron-address", VALID_TRON_ADDRESS, "1000000", FUTURE);

        assertThatExceptionOfType(InvalidWatchRequestException.class)
                .isThrownBy(() -> service.register(request));
    }

    @Test
    void shouldUnregisterWatchOnDelete() {
        UUID watchId = UUID.randomUUID();
        when(watchRepository.existsByWatchId(watchId)).thenReturn(true);

        service.unregister(watchId);

        verify(watchRepository).markUnregisteredIfRegistered(watchId, NOW);
    }

    @Test
    void unregisterThrowsWatchNotFoundExceptionForAnUnknownWatchId() {
        UUID watchId = UUID.randomUUID();
        when(watchRepository.existsByWatchId(watchId)).thenReturn(false);

        assertThatThrownBy(() -> service.unregister(watchId))
                .isInstanceOf(WatchNotFoundException.class);
    }

    @Test
    void unregisterDoesNotThrowWhenTheConditionalUpdateAffectsZeroRows() {
        // The already-non-REGISTERED case (idempotent no-op) - the service layer treats 0 or 1 rows
        // updated identically, since existsByWatchId already confirmed the row exists at all.
        UUID watchId = UUID.randomUUID();
        when(watchRepository.existsByWatchId(watchId)).thenReturn(true);
        when(watchRepository.markUnregisteredIfRegistered(any(), any())).thenReturn(0);

        assertThatCode(() -> service.unregister(watchId)).doesNotThrowAnyException();
    }
}
