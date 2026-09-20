package com.themistra.crypto.config;

import com.themistra.crypto.chain.ChainAdapter;
import com.themistra.crypto.chain.ChainAdapterRegistry;
import com.themistra.crypto.chain.ChainId;
import com.themistra.crypto.chain.evm.EvmChainAdapter;
import com.themistra.crypto.quorum.QuorumPolicy;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns declared configuration into a validated {@link ChainAdapterRegistry}.
 *
 * <p>Validates shape only — provider count, label uniqueness, a known namespace, a present
 * endpoint — and deliberately opens no connection. A provider being momentarily unreachable is
 * precisely the condition §6.1 is designed to survive; refusing to boot on it would turn a
 * survivable incident into an outage, and would make a restart during a provider outage impossible.
 * Reachability belongs to health checking.
 *
 * <p>What it does refuse is a deployment that could never satisfy the quorum it claims to enforce.
 */
public final class ChainAdapterFactory {

    private ChainAdapterFactory() {
    }

    public static ChainAdapterRegistry build(ChainProviderProperties properties, QuorumPolicy policy) {
        Map<ChainId, List<ChainAdapter>> byChain = new LinkedHashMap<>();

        for (ChainProviderProperties.Chain chain : properties.getChains()) {
            ChainId chainId = parseChainId(chain.getId());
            if (byChain.containsKey(chainId)) {
                throw new ChainConfigurationException("chain " + chainId + " is declared more than once");
            }
            byChain.put(chainId, adaptersFor(chainId, chain, policy));
        }
        return new ChainAdapterRegistry(byChain);
    }

    private static List<ChainAdapter> adaptersFor(
            ChainId chainId, ChainProviderProperties.Chain chain, QuorumPolicy policy) {

        List<ChainProviderProperties.Provider> providers = chain.getProviders();
        if (providers.size() < policy.threshold()) {
            throw new ChainConfigurationException(
                    "chain %s declares %d provider(s) but the quorum threshold is %d — a deployment that "
                            .formatted(chainId, providers.size(), policy.threshold())
                            + "cannot reach quorum must not serve traffic (ARCHITECTURE 6.1)");
        }

        Set<String> labels = new HashSet<>();
        List<ChainAdapter> adapters = new ArrayList<>(providers.size());

        for (ChainProviderProperties.Provider provider : providers) {
            String label = provider.getLabel();
            if (label == null || label.isBlank()) {
                throw new ChainConfigurationException(
                        "chain " + chainId + " declares a provider with no label");
            }
            if (!labels.add(label)) {
                throw new ChainConfigurationException(
                        "chain %s declares provider label '%s' more than once — an observation could not "
                                .formatted(chainId, label)
                                + "be attributed to a provider");
            }
            // Never include the endpoint value: a startup failure ends up in tickets and logs.
            if (provider.getEndpoint() == null || provider.getEndpoint().isBlank()) {
                throw new ChainConfigurationException(
                        "chain %s provider '%s' has no endpoint configured".formatted(chainId, label));
            }
            adapters.add(adapter(chainId, label, provider.getEndpoint()));
        }
        return adapters;
    }

    private static ChainAdapter adapter(ChainId chainId, String label, String endpoint) {
        if (chainId.isEvm()) {
            return new EvmChainAdapter(label, chainId, Web3j.build(new HttpService(endpoint)));
        }
        throw new ChainConfigurationException(
                "chain %s is in namespace '%s', which this service has no adapter for — a declared chain "
                        .formatted(chainId, chainId.namespace())
                        + "nothing can observe would look like payments never arriving");
    }

    private static ChainId parseChainId(String value) {
        if (value == null || value.isBlank()) {
            throw new ChainConfigurationException("a declared chain has no id");
        }
        try {
            return ChainId.parse(value);
        } catch (IllegalArgumentException e) {
            throw new ChainConfigurationException("invalid chain id '" + value + "': " + e.getMessage());
        }
    }
}
