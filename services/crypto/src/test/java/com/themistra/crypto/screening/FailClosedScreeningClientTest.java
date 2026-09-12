package com.themistra.crypto.screening;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/** AC2 (always fail-closed), AC3 (persists exactly one row per call), AC7 (no network I/O - structural:
 * this class has no HTTP/RPC client field to call), AC8 (observability) - frozen brief Phase 4. */
@ExtendWith(MockitoExtension.class)
class FailClosedScreeningClientTest {

    private static final Instant NOW = Instant.parse("2026-09-12T00:00:00Z");

    @Mock
    private ScreeningResultRepository screeningResultRepository;

    private FailClosedScreeningClient client;

    @BeforeEach
    void setUp() {
        client = new FailClosedScreeningClient(screeningResultRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void alwaysReturnsErrorForANormalInput() {
        ScreeningOutcome outcome = client.screen("ETHEREUM", "0xaddr", "0xtx");

        assertThat(outcome).isEqualTo(ScreeningOutcome.ERROR);
    }

    @Test
    void alwaysReturnsErrorWhenTxHashIsNull() {
        ScreeningOutcome outcome = client.screen("ETHEREUM", "0xaddr", null);

        assertThat(outcome).isEqualTo(ScreeningOutcome.ERROR);
    }

    @Test
    void alwaysReturnsErrorForAMalformedAddressSinceThisClassPerformsNoFormatValidation() {
        // Phase 3 Finding #5: token.AddressValidator owns format validation, not ScreeningClient.
        ScreeningOutcome outcome = client.screen("ETHEREUM", "not-an-address", "0xtx");

        assertThat(outcome).isEqualTo(ScreeningOutcome.ERROR);
    }

    @Test
    void neverReturnsClearedOrBlocked() {
        ScreeningOutcome outcome = client.screen("TRON", "Taddr", "0xtx");

        assertThat(outcome).isNotEqualTo(ScreeningOutcome.CLEARED);
        assertThat(outcome).isNotEqualTo(ScreeningOutcome.BLOCKED);
    }

    @Test
    void persistsExactlyOneScreeningResultPerCallWithTheExpectedFields() {
        client.screen("ETHEREUM", "0xaddr", "0xtx");

        ArgumentCaptor<ScreeningResult> captor = ArgumentCaptor.forClass(ScreeningResult.class);
        verify(screeningResultRepository).save(captor.capture());
        ScreeningResult saved = captor.getValue();

        assertThat(saved.chain()).isEqualTo("ETHEREUM");
        assertThat(saved.address()).isEqualTo("0xaddr");
        assertThat(saved.txHash()).isEqualTo("0xtx");
        assertThat(saved.outcome()).isEqualTo(ScreeningOutcome.ERROR);
        assertThat(saved.provider()).isEqualTo(FailClosedScreeningClient.PROVIDER_NAME);
        assertThat(saved.rawResponse()).isNull();
        assertThat(saved.screenedAt()).isEqualTo(NOW);
    }
}
