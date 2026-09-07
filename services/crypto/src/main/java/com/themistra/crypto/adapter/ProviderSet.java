package com.themistra.crypto.adapter;

import com.themistra.crypto.adapter.eth.EthereumAdapter;
import com.themistra.crypto.adapter.tron.TronAdapter;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * {@code Chain -> List<NamedAdapter>} (design.md §6, "N adapters per chain — O1") - named in the spec
 * but never built by any prior task, since none needed a per-chain grouping until now (T16, the
 * watcher layer). Combines the separately-injected {@code List<EthereumAdapter>}/
 * {@code List<TronAdapter>} collection beans (T06/T07's {@code *AdapterConfig} classes) into the shape
 * a chain-agnostic caller actually needs.
 *
 * <p>Lives in {@code adapter/}, not {@code provider/} (T16 Phase 3 Finding 2) -
 * {@code ProviderModuleBoundaryTest} unconditionally forbids any {@code adapter} import inside
 * {@code provider/}, and this class is fundamentally an {@code adapter}-layer concern (grouping {@link
 * ChainAdapter} instances), not a health-tracking one.</p>
 *
 * <p><b>{@link NamedAdapter} pairs each {@link ChainAdapter} with its provider name.</b> Neither the
 * VERBATIM {@code ChainAdapter} interface nor {@code Watcher} (L14/AC7 - no code path may distinguish
 * one {@code ChainAdapter} implementation from another) may expose or depend on a concrete adapter's
 * own {@code providerName()} accessor directly. This class is the one place that legitimately holds
 * both the concrete types (to call their {@code providerName()}) and the abstract {@link ChainAdapter}
 * view every other caller uses - captured once, here, at construction time.</p>
 */
@Component
public class ProviderSet {

    private final Map<Chain, List<NamedAdapter>> adaptersByChain;

    public ProviderSet(List<EthereumAdapter> ethereumAdapters, List<TronAdapter> tronAdapters) {
        Map<Chain, List<NamedAdapter>> byChain = new EnumMap<>(Chain.class);
        byChain.put(Chain.ETHEREUM, ethereumAdapters.stream()
                .map(adapter -> new NamedAdapter(adapter.providerName(), adapter))
                .toList());
        byChain.put(Chain.TRON, tronAdapters.stream()
                .map(adapter -> new NamedAdapter(adapter.providerName(), adapter))
                .toList());
        this.adaptersByChain = Map.copyOf(byChain);
    }

    /** Never {@code null} - an unconfigured chain returns an empty list, not an absent key. */
    public List<NamedAdapter> adaptersFor(Chain chain) {
        return adaptersByChain.getOrDefault(chain, List.of());
    }

    public record NamedAdapter(String providerName, ChainAdapter adapter) {
    }
}
