package com.themistra.crypto.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/** AC7 (frozen brief), L4 — see class Javadoc on {@link FinalityProperties}. */
class FinalityPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(FinalityProperties.class)
    static class TestConfig {
    }

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    @Test
    void bindsValidEnabledChains() {
        contextRunner.withPropertyValues(
                        "themistra.crypto.finality.enabled-chains[0]=ETHEREUM",
                        "themistra.crypto.finality.enabled-chains[1]=TRON")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    FinalityProperties props = context.getBean(FinalityProperties.class);
                    assertThat(props.enabledChains()).containsExactly("ETHEREUM", "TRON");
                });
    }

    @Test
    void failsWhenEnabledChainsMissing() {
        contextRunner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWhenEnabledChainsEmpty() {
        // An empty list can't be expressed as a bare property key; simulate via a single blank
        // entry, which @NotBlank on the element rejects - proving the list can't silently contain
        // a no-op entry either.
        contextRunner.withPropertyValues("themistra.crypto.finality.enabled-chains[0]=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWhenChainNotInLaunchScope() {
        contextRunner.withPropertyValues("themistra.crypto.finality.enabled-chains[0]=SOLANA")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWhenChainIsLowercase() {
        // Phase 11 Gap 12: case-sensitive by design, mirrors ProviderPropertiesTest's equivalent.
        contextRunner.withPropertyValues("themistra.crypto.finality.enabled-chains[0]=ethereum")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void hasNoConfirmationOrThresholdShapedField() {
        RecordComponent[] components = FinalityProperties.class.getRecordComponents();
        assertThat(components).hasSize(1);
        assertThat(components[0].getName()).isEqualTo("enabledChains");
        assertThat(Arrays.stream(components).map(RecordComponent::getName))
                .as("L4: finality policy is hardcoded per chain - no confirmation-count/threshold field belongs here")
                .noneMatch(name -> {
                    String lower = name.toLowerCase(Locale.ROOT);
                    return lower.contains("confirmation") || lower.contains("threshold");
                });
    }
}
