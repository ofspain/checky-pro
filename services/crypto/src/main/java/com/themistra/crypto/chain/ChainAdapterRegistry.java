package com.themistra.crypto.chain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The adapters serving each configured chain.
 *
 * <p>A lookup, not a facade: it hands out adapters and knows nothing about establishing facts, so
 * nothing acquires a second route to a provider that bypasses the quorum layer.
 *
 * <p>Asking for an unconfigured chain fails rather than returning an empty list. An empty list
 * would reach the quorum layer looking like total provider failure — a transient, survivable
 * condition — when the truth is a permanent misconfiguration that needs a human.
 */
public class ChainAdapterRegistry {

    private final Map<ChainId, List<ChainAdapter>> adaptersByChain;

    public ChainAdapterRegistry(Map<ChainId, List<ChainAdapter>> adaptersByChain) {
        Map<ChainId, List<ChainAdapter>> copy = new LinkedHashMap<>();
        adaptersByChain.forEach((chain, adapters) -> copy.put(chain, List.copyOf(adapters)));
        this.adaptersByChain = Map.copyOf(copy);
    }

    /**
     * Every provider declared for a chain.
     *
     * @throws UnknownChainException when the chain was never configured
     */
    public List<ChainAdapter> adaptersFor(ChainId chainId) {
        List<ChainAdapter> adapters = adaptersByChain.get(chainId);
        if (adapters == null) {
            throw new UnknownChainException(chainId);
        }
        return adapters;
    }

    public boolean supports(ChainId chainId) {
        return adaptersByChain.containsKey(chainId);
    }

    public Set<ChainId> configuredChains() {
        return adaptersByChain.keySet();
    }

    /** Provider count per chain, for health reporting. Carries no endpoints. */
    public Map<ChainId, Integer> providerCounts() {
        Map<ChainId, Integer> counts = new LinkedHashMap<>();
        adaptersByChain.forEach((chain, adapters) -> counts.put(chain, adapters.size()));
        return Map.copyOf(counts);
    }

    /** A chain nobody configured. Distinct from every provider for a configured chain failing. */
    public static class UnknownChainException extends RuntimeException {

        public UnknownChainException(ChainId chainId) {
            super("no providers are configured for chain " + chainId);
        }
    }
}
