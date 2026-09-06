package com.themistra.crypto.config;

import com.themistra.crypto.chain.ChainAdapterRegistry;
import com.themistra.crypto.chain.ChainId;
import com.themistra.crypto.quorum.QuorumPolicy;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What an operator can see about chain configuration.
 *
 * <p>Reports which chains are configured and how many providers each has, so a chain running with
 * no margin above the quorum threshold is visible before it drops below it. Carries no endpoint or
 * credential — health output is widely readable, and a partially-redacted endpoint is still a leak.
 *
 * <p>Reports UP for a service with no chains: not yet given work is not unhealthy.
 */
@Component
public class ChainConfigurationHealthIndicator implements HealthIndicator {

    private final ChainAdapterRegistry registry;
    private final QuorumPolicy policy;

    public ChainConfigurationHealthIndicator(ChainAdapterRegistry registry, QuorumPolicy policy) {
        this.registry = registry;
        this.policy = policy;
    }

    @Override
    public Health health() {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("quorumThreshold", policy.threshold());

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Map.Entry<ChainId, Integer> entry : registry.providerCounts().entrySet()) {
            counts.put(entry.getKey().value(), entry.getValue());
        }
        details.put("chains", counts);
        details.put("configuredChains", counts.size());

        return Health.up().withDetails(details).build();
    }
}
