package com.themistra.crypto.config;

import com.themistra.crypto.chain.ChainAdapter;
import com.themistra.crypto.chain.ChainAdapterRegistry;
import com.themistra.crypto.chain.ChainId;
import com.themistra.crypto.quorum.QuorumPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A deployment that could not satisfy ARCHITECTURE §6.1 must fail at startup rather than run and
 * later attest on evidence thinner than the architecture promises.
 */
class ChainAdapterFactoryTest {

    private static final String ENDPOINT_A = "https://provider-a.example/v2/secret-key-aaa";
    private static final String ENDPOINT_B = "https://provider-b.example/v2/secret-key-bbb";
    private static final String ENDPOINT_C = "https://provider-c.example/v2/secret-key-ccc";

    @Test
    @DisplayName("three declared providers resolve to three adapters")
    void resolvesConfiguredProviders() {
        ChainAdapterRegistry registry = build(chain("eip155:1",
                provider("evm-provider-a", ENDPOINT_A),
                provider("evm-provider-b", ENDPOINT_B),
                provider("evm-provider-c", ENDPOINT_C)));

        List<ChainAdapter> adapters = registry.adaptersFor(ChainId.evm(1));

        assertThat(adapters).hasSize(3)
                .extracting(ChainAdapter::providerLabel)
                .containsExactly("evm-provider-a", "evm-provider-b", "evm-provider-c");
        assertThat(adapters).allSatisfy(a -> assertThat(a.chainId()).isEqualTo(ChainId.evm(1)));
    }

    @Test
    @DisplayName("no chains configured is a valid state")
    void noChainsIsValid() {
        ChainAdapterRegistry registry = ChainAdapterFactory.build(
                new ChainProviderProperties(), QuorumPolicy.twoOfThree());

        assertThat(registry.configuredChains()).isEmpty();
    }

    @Test
    @DisplayName("a chain below the quorum threshold refuses to start")
    void tooFewProvidersFailsStartup() {
        assertThatThrownBy(() -> build(chain("eip155:1", provider("evm-provider-a", ENDPOINT_A))))
                .isInstanceOf(ChainConfigurationException.class)
                .hasMessageContaining("eip155:1")
                .hasMessageContaining("threshold is 2");
    }

    @Test
    @DisplayName("exactly the threshold starts, with no tolerance to spare")
    void exactlyThresholdStarts() {
        ChainAdapterRegistry registry = build(chain("eip155:1",
                provider("evm-provider-a", ENDPOINT_A),
                provider("evm-provider-b", ENDPOINT_B)));

        assertThat(registry.adaptersFor(ChainId.evm(1))).hasSize(2);
    }

    @Test
    @DisplayName("duplicate labels on one chain refuse to start")
    void duplicateLabelsFailStartup() {
        assertThatThrownBy(() -> build(chain("eip155:1",
                provider("evm-provider-a", ENDPOINT_A),
                provider("evm-provider-a", ENDPOINT_B),
                provider("evm-provider-c", ENDPOINT_C))))
                .isInstanceOf(ChainConfigurationException.class)
                .hasMessageContaining("evm-provider-a")
                .hasMessageContaining("attributed");
    }

    @Test
    @DisplayName("the same label on different chains is fine")
    void sameLabelDifferentChainsIsFine() {
        ChainAdapterRegistry registry = build(
                chain("eip155:1", provider("evm-provider-a", ENDPOINT_A), provider("evm-provider-b", ENDPOINT_B)),
                chain("eip155:8453", provider("evm-provider-a", ENDPOINT_A), provider("evm-provider-b", ENDPOINT_B)));

        assertThat(registry.configuredChains())
                .containsExactlyInAnyOrder(ChainId.evm(1), ChainId.evm(8453));
    }

    @Test
    @DisplayName("a namespace with no adapter refuses to start")
    void unknownNamespaceFailsStartup() {
        assertThatThrownBy(() -> build(chain("tron:0x2b6653dc",
                provider("tron-provider-a", ENDPOINT_A),
                provider("tron-provider-b", ENDPOINT_B))))
                .isInstanceOf(ChainConfigurationException.class)
                .hasMessageContaining("tron");
    }

    @Test
    @DisplayName("a missing endpoint refuses to start")
    void missingEndpointFailsStartup() {
        assertThatThrownBy(() -> build(chain("eip155:1",
                provider("evm-provider-a", ENDPOINT_A),
                provider("evm-provider-b", "  "))))
                .isInstanceOf(ChainConfigurationException.class)
                .hasMessageContaining("evm-provider-b");
    }

    @Test
    @DisplayName("no startup failure message leaks an endpoint")
    void failureMessagesCarryNoEndpoint() {
        assertThatThrownBy(() -> build(chain("eip155:1", provider("evm-provider-a", ENDPOINT_A))))
                .isInstanceOf(ChainConfigurationException.class)
                .satisfies(e -> assertThat(e.getMessage())
                        .doesNotContain("secret-key-aaa")
                        .doesNotContain("provider-a.example"));
    }

    @Test
    @DisplayName("an unconfigured chain fails rather than looking like total provider failure")
    void unknownChainFails() {
        ChainAdapterRegistry registry = build(chain("eip155:1",
                provider("evm-provider-a", ENDPOINT_A),
                provider("evm-provider-b", ENDPOINT_B)));

        assertThatThrownBy(() -> registry.adaptersFor(ChainId.evm(999)))
                .isInstanceOf(ChainAdapterRegistry.UnknownChainException.class)
                .hasMessageContaining("eip155:999");
    }

    @Test
    @DisplayName("a chain declared twice refuses to start")
    void duplicateChainFailsStartup() {
        assertThatThrownBy(() -> build(
                chain("eip155:1", provider("a", ENDPOINT_A), provider("b", ENDPOINT_B)),
                chain("eip155:1", provider("c", ENDPOINT_C), provider("d", ENDPOINT_A))))
                .isInstanceOf(ChainConfigurationException.class)
                .hasMessageContaining("more than once");
    }

    @Test
    @DisplayName("a malformed chain id refuses to start")
    void malformedChainIdFailsStartup() {
        assertThatThrownBy(() -> build(chain("1", provider("a", ENDPOINT_A), provider("b", ENDPOINT_B))))
                .isInstanceOf(ChainConfigurationException.class)
                .hasMessageContaining("invalid chain id");
    }

    private static ChainAdapterRegistry build(ChainProviderProperties.Chain... chains) {
        ChainProviderProperties properties = new ChainProviderProperties();
        properties.setChains(List.of(chains));
        return ChainAdapterFactory.build(properties, QuorumPolicy.twoOfThree());
    }

    private static ChainProviderProperties.Chain chain(String id, ChainProviderProperties.Provider... providers) {
        ChainProviderProperties.Chain chain = new ChainProviderProperties.Chain();
        chain.setId(id);
        chain.setProviders(List.of(providers));
        return chain;
    }

    private static ChainProviderProperties.Provider provider(String label, String endpoint) {
        ChainProviderProperties.Provider provider = new ChainProviderProperties.Provider();
        provider.setLabel(label);
        provider.setEndpoint(endpoint);
        return provider;
    }
}
