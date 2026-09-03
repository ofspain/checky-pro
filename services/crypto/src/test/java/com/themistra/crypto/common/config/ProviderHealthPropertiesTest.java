package com.themistra.crypto.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/** T10 — see class Javadoc on {@link ProviderHealthProperties}. */
class ProviderHealthPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(ProviderHealthProperties.class)
    static class TestConfig {
    }

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    @Test
    void bindsAPositiveThreshold() {
        contextRunner.withPropertyValues("themistra.crypto.provider-health.disagreement-threshold=3")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ProviderHealthProperties props = context.getBean(ProviderHealthProperties.class);
                    assertThat(props.disagreementThreshold()).isEqualTo(3);
                });
    }

    @Test
    void failsWhenThresholdMissing() {
        contextRunner.run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWhenThresholdIsZero() {
        contextRunner.withPropertyValues("themistra.crypto.provider-health.disagreement-threshold=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWhenThresholdIsNegative() {
        contextRunner.withPropertyValues("themistra.crypto.provider-health.disagreement-threshold=-1")
                .run(context -> assertThat(context).hasFailed());
    }
}
