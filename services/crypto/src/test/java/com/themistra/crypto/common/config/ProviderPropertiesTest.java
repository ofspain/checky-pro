package com.themistra.crypto.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/** AC5/AC8 (frozen brief), L1/O1/Q1 — see class Javadoc on {@link ProviderProperties}. */
class ProviderPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(ProviderProperties.class)
    static class TestConfig {
    }

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    private static final String[] VALID_TWO_PROVIDER_ETHEREUM = {
            "themistra.crypto.providers.chains[0].chain=ETHEREUM",
            "themistra.crypto.providers.chains[0].providers[0].name=fake-provider-a",
            "themistra.crypto.providers.chains[0].providers[0].url=http://localhost:9901",
            "themistra.crypto.providers.chains[0].providers[0].timeout-seconds=5",
            "themistra.crypto.providers.chains[0].providers[0].api-key-secret-name=secret-a",
            "themistra.crypto.providers.chains[0].providers[1].name=fake-provider-b",
            "themistra.crypto.providers.chains[0].providers[1].url=http://localhost:9902",
            "themistra.crypto.providers.chains[0].providers[1].timeout-seconds=5",
            "themistra.crypto.providers.chains[0].providers[1].api-key-secret-name=secret-b",
            "themistra.crypto.providers.quorum-threshold=2"
    };

    @Test
    void bindsValidTwoChainConfiguration() {
        contextRunner.withPropertyValues(VALID_TWO_PROVIDER_ETHEREUM).run(context -> {
            assertThat(context).hasNotFailed();
            ProviderProperties props = context.getBean(ProviderProperties.class);
            assertThat(props.quorumThreshold()).isEqualTo(2);
            assertThat(props.chains()).hasSize(1);
            assertThat(props.chains().get(0).chain()).isEqualTo("ETHEREUM");
            assertThat(props.chains().get(0).providers()).hasSize(2);
        });
    }

    @Test
    void failsWhenChainsListMissing() {
        contextRunner.withPropertyValues("themistra.crypto.providers.quorum-threshold=2")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWhenQuorumThresholdMissing() {
        contextRunner.withPropertyValues(
                        "themistra.crypto.providers.chains[0].chain=ETHEREUM",
                        "themistra.crypto.providers.chains[0].providers[0].name=x",
                        "themistra.crypto.providers.chains[0].providers[0].url=http://localhost",
                        "themistra.crypto.providers.chains[0].providers[0].timeout-seconds=5",
                        "themistra.crypto.providers.chains[0].providers[0].api-key-secret-name=k")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWhenProviderTimeoutIsNonPositive() {
        contextRunner.withPropertyValues(
                        "themistra.crypto.providers.chains[0].chain=ETHEREUM",
                        "themistra.crypto.providers.chains[0].providers[0].name=x",
                        "themistra.crypto.providers.chains[0].providers[0].url=http://localhost",
                        "themistra.crypto.providers.chains[0].providers[0].timeout-seconds=0",
                        "themistra.crypto.providers.chains[0].providers[0].api-key-secret-name=k",
                        "themistra.crypto.providers.quorum-threshold=1")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWhenApiKeySecretNameBlank() {
        contextRunner.withPropertyValues(
                        "themistra.crypto.providers.chains[0].chain=ETHEREUM",
                        "themistra.crypto.providers.chains[0].providers[0].name=x",
                        "themistra.crypto.providers.chains[0].providers[0].url=http://localhost",
                        "themistra.crypto.providers.chains[0].providers[0].timeout-seconds=5",
                        "themistra.crypto.providers.chains[0].providers[0].api-key-secret-name=",
                        "themistra.crypto.providers.quorum-threshold=1")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWhenChainNotInLaunchScope() {
        contextRunner.withPropertyValues(
                        "themistra.crypto.providers.chains[0].chain=SOLANA",
                        "themistra.crypto.providers.chains[0].providers[0].name=x",
                        "themistra.crypto.providers.chains[0].providers[0].url=http://localhost",
                        "themistra.crypto.providers.chains[0].providers[0].timeout-seconds=5",
                        "themistra.crypto.providers.chains[0].providers[0].api-key-secret-name=k",
                        "themistra.crypto.providers.quorum-threshold=1")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWhenChainIsLowercase() {
        // Phase 11 Gap 12: the @Pattern is case-sensitive by design (spec consistently uses
        // uppercase chain identifiers) - a lowercase value must not silently bind as "valid".
        contextRunner.withPropertyValues(
                        "themistra.crypto.providers.chains[0].chain=ethereum",
                        "themistra.crypto.providers.chains[0].providers[0].name=x",
                        "themistra.crypto.providers.chains[0].providers[0].url=http://localhost",
                        "themistra.crypto.providers.chains[0].providers[0].timeout-seconds=5",
                        "themistra.crypto.providers.chains[0].providers[0].api-key-secret-name=k",
                        "themistra.crypto.providers.quorum-threshold=1")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWhenQuorumThresholdExceedsConfiguredProviderCount() {
        contextRunner.withPropertyValues(
                        "themistra.crypto.providers.chains[0].chain=ETHEREUM",
                        "themistra.crypto.providers.chains[0].providers[0].name=x",
                        "themistra.crypto.providers.chains[0].providers[0].url=http://localhost",
                        "themistra.crypto.providers.chains[0].providers[0].timeout-seconds=5",
                        "themistra.crypto.providers.chains[0].providers[0].api-key-secret-name=k",
                        "themistra.crypto.providers.quorum-threshold=5")
                .run(context -> assertThat(context.getStartupFailure())
                        .rootCause().isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("quorum-threshold").hasMessageContaining("ETHEREUM"));
    }

    @Test
    void succeedsWhenQuorumThresholdEqualsProviderCount() {
        contextRunner.withPropertyValues(
                        "themistra.crypto.providers.chains[0].chain=TRON",
                        "themistra.crypto.providers.chains[0].providers[0].name=x",
                        "themistra.crypto.providers.chains[0].providers[0].url=http://localhost",
                        "themistra.crypto.providers.chains[0].providers[0].timeout-seconds=5",
                        "themistra.crypto.providers.chains[0].providers[0].api-key-secret-name=k",
                        "themistra.crypto.providers.quorum-threshold=1")
                .run(context -> assertThat(context).hasNotFailed());
    }
}
