package com.themistra.notification.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC1 - real property binding via {@link ApplicationContextRunner}, not just direct construction
 * (self-review Finding 1: a constructor-only test can't catch a typo'd key name in the real
 * {@code application.properties}).
 */
class EmailPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(EmailProperties.class)
    static class TestConfig {
    }

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    @Test
    void bindsFromTheRealPrefixAndKeyNames() {
        contextRunner.withPropertyValues(
                        "themistra.notification.email.from=no-reply@checky.pro",
                        "themistra.notification.email.transport=ses")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    EmailProperties props = context.getBean(EmailProperties.class);
                    assertThat(props.from()).isEqualTo("no-reply@checky.pro");
                    assertThat(props.transport()).isEqualTo("ses");
                });
    }

    @Test
    void failsWhenFromIsBlank() {
        contextRunner.withPropertyValues(
                        "themistra.notification.email.from=",
                        "themistra.notification.email.transport=ses")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void failsWhenTransportIsBlank() {
        contextRunner.withPropertyValues(
                        "themistra.notification.email.from=no-reply@checky.pro",
                        "themistra.notification.email.transport=")
                .run(context -> assertThat(context).hasFailed());
    }
}
