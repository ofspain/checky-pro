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
 *
 * <p><b>Exactly 3 providers per chain (T16 Phase 8 Finding 7).</b> {@code QuorumEvaluator} hard-requires
 * exactly 3 non-null answers per fact (verified directly against its source) - a chain configured with
 * any other provider count could never reach a quorum decision at all, and would fail completely
 * silently (the correlation in {@code Watcher} simply never completes, with no error anywhere).
 * Validated here, not in the general-purpose {@code ProviderProperties} - that class is consumed by
 * {@code EthereumAdapterConfig}/{@code TronAdapterConfig} too, for concerns (credential resolution,
 * timeout wiring, adapter construction) that have nothing to do with quorum arithmetic and legitimately
 * use other provider counts in their own tests; this constraint belongs to {@code ProviderSet}, the
 * actual, sole consumer that needs it, not the shared config type every other adapter concern also
 * binds through.</p>
 */
@Component
public class ProviderSet {

    private static final int REQUIRED_PROVIDER_COUNT = 3;

    private final Map<Chain, List<NamedAdapter>> adaptersByChain;

    public ProviderSet(List<EthereumAdapter> ethereumAdapters, List<TronAdapter> tronAdapters) {
        Map<Chain, List<NamedAdapter>> byChain = new EnumMap<>(Chain.class);
        byChain.put(Chain.ETHEREUM, toNamedAdapters(Chain.ETHEREUM, ethereumAdapters, EthereumAdapter::providerName));
        byChain.put(Chain.TRON, toNamedAdapters(Chain.TRON, tronAdapters, TronAdapter::providerName));
        this.adaptersByChain = Map.copyOf(byChain);
    }

    private static <T extends ChainAdapter> List<NamedAdapter> toNamedAdapters(
            Chain chain, List<T> adapters, java.util.function.Function<T, String> providerNameOf) {
        if (adapters.size() != REQUIRED_PROVIDER_COUNT) {
            throw new IllegalStateException(
                    "themistra.crypto.providers must configure exactly " + REQUIRED_PROVIDER_COUNT
                            + " providers for chain " + chain + " (QuorumEvaluator's own 2-of-3 design"
                            + " hard-requires exactly 3 answers, T16) - found " + adapters.size());
        }
        return adapters.stream()
                .map(adapter -> new NamedAdapter(providerNameOf.apply(adapter), adapter))
                .toList();
    }

    /** Never {@code null} - an unconfigured chain returns an empty list, not an absent key. */
    public List<NamedAdapter> adaptersFor(Chain chain) {
        return adaptersByChain.getOrDefault(chain, List.of());
    }

    public record NamedAdapter(String providerName, ChainAdapter adapter) {
    }
}
