package com.themistra.notification.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/** AC1 - real property binding via {@link ApplicationContextRunner} (self-review Finding 1). */
class InappPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(InappProperties.class)
    static class TestConfig {
    }

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    @Test
    void bindsFromTheRealPrefixAndKeyName() {
        contextRunner.withPropertyValues("themistra.notification.inapp.transport=sse")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(InappProperties.class).transport()).isEqualTo("sse");
                });
    }

    @Test
    void failsWhenTransportIsBlank() {
        contextRunner.withPropertyValues("themistra.notification.inapp.transport=")
                .run(context -> assertThat(context).hasFailed());
    }
}
