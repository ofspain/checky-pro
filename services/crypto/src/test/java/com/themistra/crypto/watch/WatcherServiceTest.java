package com.themistra.crypto.watch;

import com.themistra.crypto.chain.ChainAdapter;
import com.themistra.crypto.chain.ChainAdapterRegistry;
import com.themistra.crypto.chain.ChainId;
import com.themistra.crypto.chain.TxObservation;
import com.themistra.crypto.quorum.QuorumReader;
import com.themistra.crypto.quorum.QuorumResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigInteger;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The watcher finds a candidate, puts it through quorum, and only then believes it.
 *
 * <p>The distinction these tests protect is that discovery and belief are separate: a hash a
 * provider offers is not a payment until several providers agree on what it contains.
 */
class WatcherServiceTest {

    private static final String CHAIN = "eip155:1";
    private static final String RECIPIENT = "0x9fd4aaa15c9b74f4c4b248566e01a729e3ace193";
    private static final String USDC = "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48";
    private static final String TX = "0x4154b051894293b1c1e686015ab18ecae949d144c2ad7b25149786eb54a95333";
    private static final BigInteger EXPECTED = new BigInteger("3000000000");

    private final WatchRepository watchRepository = mock(WatchRepository.class);
    private final QuorumReader quorumReader = mock(QuorumReader.class);
    private final ChainAdapterRegistry registry = mock(ChainAdapterRegistry.class);
    private final WatcherEventPublisher publisher = mock(WatcherEventPublisher.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-06T12:00:00Z"), ZoneOffset.UTC);

    private final WatcherService watcher =
            new WatcherService(watchRepository, quorumReader, registry, publisher, clock);

    private ChainAdapter adapter;
    private Watch watch;

    @BeforeEach
    void setUp() {
        adapter = mock(ChainAdapter.class);
        when(adapter.providerLabel()).thenReturn("provider-a");
        when(adapter.currentBlockNumber()).thenReturn(1000L);
        when(registry.adaptersFor(any(ChainId.class))).thenReturn(List.of(adapter));

        watch = mock(Watch.class);
        when(watch.getId()).thenReturn(1L);
        when(watch.getWatchUuid()).thenReturn(UUID.randomUUID());
        when(watch.getChainId()).thenReturn(CHAIN);
        when(watch.getRecipientAddress()).thenReturn(RECIPIENT);
        when(watch.getTokenAddress()).thenReturn(USDC);
        when(watch.getExpectedAmount()).thenReturn(EXPECTED);
        when(watchRepository.findAllActive(any())).thenReturn(List.of(watch));
    }

    @Test
    @DisplayName("a discovered transfer that quorum agrees on satisfies the watch")
    void agreedCandidateSatisfiesWatch() {
        discovers(TX);
        when(quorumReader.establish(any(), eq(TX)))
                .thenReturn(new QuorumResult.Agreed(observation(EXPECTED), List.of()));

        watcher.checkActiveWatches();

        ArgumentCaptor<PaymentVerifiedEvent> published = ArgumentCaptor.forClass(PaymentVerifiedEvent.class);
        verify(publisher).publish(published.capture());
        assertThat(published.getValue().txHash()).isEqualTo(TX);
        assertThat(published.getValue().observedAmount()).isEqualTo(EXPECTED);
        verify(watchRepository).updateStatus(1L, WatchStatus.SATISFIED);
    }

    @Test
    @DisplayName("a discovered hash is not believed until quorum agrees")
    void disagreementPublishesNothing() {
        discovers(TX);
        when(quorumReader.establish(any(), eq(TX)))
                .thenReturn(new QuorumResult.Disagreed("providers conflict", List.of()));

        watcher.checkActiveWatches();

        verifyNoInteractions(publisher);
        verify(watchRepository, never()).updateStatus(anyLong(), any());
    }

    @Test
    @DisplayName("a real transfer for the wrong amount leaves the watch open")
    void wrongAmountLeavesWatchOpen() {
        discovers(TX);
        when(quorumReader.establish(any(), eq(TX)))
                .thenReturn(new QuorumResult.Agreed(observation(new BigInteger("999")), List.of()));

        watcher.checkActiveWatches();

        verifyNoInteractions(publisher);
        verify(watchRepository, never()).updateStatus(anyLong(), any());
    }

    @Test
    @DisplayName("a provider that fails discovery does not stop the others")
    void discoveryFailureIsSurvivable() {
        ChainAdapter broken = mock(ChainAdapter.class);
        when(broken.providerLabel()).thenReturn("provider-broken");
        when(broken.currentBlockNumber()).thenThrow(new RuntimeException("429 rate limited"));
        when(registry.adaptersFor(any(ChainId.class))).thenReturn(List.of(broken, adapter));

        discovers(TX);
        when(quorumReader.establish(any(), eq(TX)))
                .thenReturn(new QuorumResult.Agreed(observation(EXPECTED), List.of()));

        watcher.checkActiveWatches();

        verify(publisher).publish(any());
    }

    @Test
    @DisplayName("nothing on chain yet means nothing is published")
    void noCandidatesPublishesNothing() {
        when(adapter.findIncomingTransfers(anyString(), anyString(), anyLong(), anyLong()))
                .thenReturn(List.of());

        watcher.checkActiveWatches();

        verifyNoInteractions(quorumReader, publisher);
    }

    @Test
    @DisplayName("no active watches means no provider is called at all")
    void noActiveWatchesCostsNothing() {
        when(watchRepository.findAllActive(any())).thenReturn(List.of());

        watcher.checkActiveWatches();

        verifyNoInteractions(registry, quorumReader, publisher);
    }

    @Test
    @DisplayName("one watch throwing does not abandon the rest of the cycle")
    void oneBadWatchDoesNotStopTheCycle() {
        Watch broken = mock(Watch.class);
        when(broken.getWatchUuid()).thenReturn(UUID.randomUUID());
        when(broken.getChainId()).thenThrow(new RuntimeException("corrupt row"));
        when(watchRepository.findAllActive(any())).thenReturn(List.of(broken, watch));

        discovers(TX);
        when(quorumReader.establish(any(), eq(TX)))
                .thenReturn(new QuorumResult.Agreed(observation(EXPECTED), List.of()));

        watcher.checkActiveWatches();

        verify(publisher).publish(any());
    }

    private void discovers(String txHash) {
        when(adapter.findIncomingTransfers(eq(RECIPIENT), eq(USDC), anyLong(), anyLong()))
                .thenReturn(List.of(txHash));
    }

    private static TxObservation observation(BigInteger amount) {
        return new TxObservation(
                ChainId.parse(CHAIN), TX, 990L, "0xblock",
                "0xsender", RECIPIENT, USDC, amount, 6);
    }
}
