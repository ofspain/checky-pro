package com.themistra.crypto.config;

import com.themistra.crypto.chain.ChainAdapterRegistry;
import com.themistra.crypto.quorum.QuorumPolicy;
import com.themistra.crypto.quorum.QuorumReader;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * Wires the quorum read layer and the configured chain providers.
 *
 * <p>Both the quorum policy and the provider registry are constructed at startup, so a deployment
 * that could not satisfy ARCHITECTURE §6.1 — a threshold below two, or a chain with fewer
 * providers than that threshold — refuses to boot. A service that starts anyway would, under load
 * and out of anyone's attention, attest on a single provider's word.
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties({QuorumProperties.class, ChainProviderProperties.class})
public class CryptoConfiguration {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    QuorumPolicy quorumPolicy(QuorumProperties properties) {
        return new QuorumPolicy(properties.getProviderCount(), properties.getThreshold());
    }

    @Bean
    QuorumReader quorumReader(QuorumPolicy policy, Clock clock) {
        return new QuorumReader(policy, clock);
    }

    @Bean
    ChainAdapterRegistry chainAdapterRegistry(ChainProviderProperties properties, QuorumPolicy policy) {
        return ChainAdapterFactory.build(properties, policy);
    }
}
