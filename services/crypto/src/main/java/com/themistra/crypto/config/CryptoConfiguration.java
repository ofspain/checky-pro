package com.themistra.crypto.config;

import com.themistra.crypto.quorum.QuorumPolicy;
import com.themistra.crypto.quorum.QuorumReader;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Wires the quorum read layer.
 *
 * <p>The policy is constructed at startup so an invalid threshold — anything below 2, or above the
 * configured provider count — refuses to boot. A service that cannot satisfy §6.1 should fail
 * loudly at deploy time, not quietly attest to a single provider's word later.
 */
@Configuration
@EnableConfigurationProperties(QuorumProperties.class)
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
}
