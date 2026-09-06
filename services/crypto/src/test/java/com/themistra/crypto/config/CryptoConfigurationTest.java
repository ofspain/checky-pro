package com.themistra.crypto.config;

import com.themistra.crypto.quorum.QuorumPolicy;
import com.themistra.crypto.quorum.QuorumReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A deployment that cannot satisfy ARCHITECTURE §6.1 must fail at startup, not on the first
 * disagreement in production.
 */
class CryptoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of())
            .withUserConfiguration(CryptoConfiguration.class);

    @Test
    @DisplayName("launch posture wires a 2-of-3 reader")
    void defaultsToTwoOfThree() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(QuorumReader.class);
            assertThat(context.getBean(QuorumPolicy.class)).isEqualTo(QuorumPolicy.twoOfThree());
        });
    }

    @Test
    @DisplayName("a threshold of 1 refuses to boot")
    void singleProviderThresholdFailsStartup() {
        runner.withPropertyValues(
                        "themistra.crypto.quorum.provider-count=3",
                        "themistra.crypto.quorum.threshold=1")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasMessageContaining("single provider is never authoritative"));
    }

    @Test
    @DisplayName("a threshold larger than the provider count refuses to boot")
    void unreachableThresholdFailsStartup() {
        runner.withPropertyValues(
                        "themistra.crypto.quorum.provider-count=2",
                        "themistra.crypto.quorum.threshold=3")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasMessageContaining("cannot exceed providerCount"));
    }
}
