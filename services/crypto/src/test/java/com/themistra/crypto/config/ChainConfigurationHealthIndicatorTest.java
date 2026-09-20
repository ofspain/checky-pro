package com.themistra.crypto.config;

import com.themistra.crypto.chain.ChainAdapterRegistry;
import com.themistra.crypto.quorum.QuorumPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChainConfigurationHealthIndicatorTest {

    private static final String ENDPOINT = "https://provider-a.example/v2/secret-key-aaa";

    @Test
    @DisplayName("reports each configured chain and its provider count")
    void reportsConfiguredChains() {
        Health health = indicatorFor(3).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("configuredChains", 1);
        assertThat(health.getDetails()).containsEntry("quorumThreshold", 2);
        assertThat(health.getDetails().get("chains")).isEqualTo(Map.of("eip155:1", 3));
    }

    @Test
    @DisplayName("a chain running at exactly the threshold is visible before it drops below it")
    void showsNoMarginChain() {
        assertThat(indicatorFor(2).health().getDetails().get("chains"))
                .isEqualTo(Map.of("eip155:1", 2));
    }

    @Test
    @DisplayName("no chains configured is UP, not unhealthy")
    void noChainsIsUp() {
        ChainAdapterRegistry empty = ChainAdapterFactory.build(
                new ChainProviderProperties(), QuorumPolicy.twoOfThree());

        Health health = new ChainConfigurationHealthIndicator(empty, QuorumPolicy.twoOfThree()).health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("configuredChains", 0);
    }

    @Test
    @DisplayName("health output leaks no endpoint or credential")
    void carriesNoSecrets() {
        String rendered = indicatorFor(3).health().getDetails().toString();

        assertThat(rendered)
                .doesNotContain("secret-key-aaa")
                .doesNotContain("provider-a.example")
                .doesNotContain("https://");
    }

    private static ChainConfigurationHealthIndicator indicatorFor(int providerCount) {
        ChainProviderProperties.Chain chain = new ChainProviderProperties.Chain();
        chain.setId("eip155:1");
        chain.setProviders(java.util.stream.IntStream.range(0, providerCount)
                .mapToObj(i -> {
                    ChainProviderProperties.Provider provider = new ChainProviderProperties.Provider();
                    provider.setLabel("evm-provider-" + (char) ('a' + i));
                    provider.setEndpoint(ENDPOINT);
                    return provider;
                })
                .toList());

        ChainProviderProperties properties = new ChainProviderProperties();
        properties.setChains(List.of(chain));

        QuorumPolicy policy = QuorumPolicy.twoOfThree();
        return new ChainConfigurationHealthIndicator(ChainAdapterFactory.build(properties, policy), policy);
    }
}
