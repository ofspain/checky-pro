package com.themistra.crypto.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.themistra.crypto.adapter.eth.EthereumAdapter;
import com.themistra.crypto.adapter.tron.TronAdapter;
import org.junit.jupiter.api.Test;
import org.tron.trident.core.ApiWrapper;
import org.web3j.protocol.Web3j;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/** T16 Phase 3 Finding 2 (location) / Phase 8 Finding 7 (exactly-3 validation). Real {@code
 * EthereumAdapter}/{@code TronAdapter} instances are constructed with mocked transport clients
 * ({@link Web3j}/{@link ApiWrapper}) purely so their own {@code providerName()} accessor is exercised
 * for real - no network call is ever made. */
class ProviderSetTest {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private EthereumAdapter ethereumAdapter(String providerName) {
        return new EthereumAdapter(mock(Web3j.class), providerName, scheduler, Duration.ofSeconds(5),
                new ObjectMapper());
    }

    private TronAdapter tronAdapter(String providerName) {
        return new TronAdapter(mock(ApiWrapper.class), providerName, scheduler, Duration.ofSeconds(5),
                new ObjectMapper());
    }

    @Test
    void groupsThreeConfiguredEthereumAdaptersByChainWithTheirProviderNames() {
        ProviderSet providerSet = new ProviderSet(
                List.of(ethereumAdapter("a"), ethereumAdapter("b"), ethereumAdapter("c")),
                List.of(tronAdapter("x"), tronAdapter("y"), tronAdapter("z")));

        List<ProviderSet.NamedAdapter> ethereumAdapters = providerSet.adaptersFor(Chain.ETHEREUM);

        assertThat(ethereumAdapters).hasSize(3);
        assertThat(ethereumAdapters).extracting(ProviderSet.NamedAdapter::providerName)
                .containsExactly("a", "b", "c");
        assertThat(ethereumAdapters).allSatisfy(named ->
                assertThat(named.adapter().chain()).isEqualTo(Chain.ETHEREUM));
    }

    @Test
    void groupsThreeConfiguredTronAdaptersByChainWithTheirProviderNames() {
        ProviderSet providerSet = new ProviderSet(
                List.of(ethereumAdapter("a"), ethereumAdapter("b"), ethereumAdapter("c")),
                List.of(tronAdapter("x"), tronAdapter("y"), tronAdapter("z")));

        List<ProviderSet.NamedAdapter> tronAdapters = providerSet.adaptersFor(Chain.TRON);

        assertThat(tronAdapters).hasSize(3);
        assertThat(tronAdapters).extracting(ProviderSet.NamedAdapter::providerName)
                .containsExactly("x", "y", "z");
    }

    @Test
    void rejectsFewerThanThreeConfiguredProvidersForAChain() {
        assertThatThrownBy(() -> new ProviderSet(
                List.of(ethereumAdapter("a"), ethereumAdapter("b")),
                List.of(tronAdapter("x"), tronAdapter("y"), tronAdapter("z"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exactly 3")
                .hasMessageContaining("ETHEREUM");
    }

    @Test
    void rejectsMoreThanThreeConfiguredProvidersForAChain() {
        assertThatThrownBy(() -> new ProviderSet(
                List.of(ethereumAdapter("a"), ethereumAdapter("b"), ethereumAdapter("c")),
                List.of(tronAdapter("x"), tronAdapter("y"), tronAdapter("z"), tronAdapter("w"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exactly 3")
                .hasMessageContaining("TRON");
    }
}
