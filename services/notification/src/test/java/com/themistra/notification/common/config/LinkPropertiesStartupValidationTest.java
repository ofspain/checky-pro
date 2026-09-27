package com.themistra.notification.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC5 (Kimi Phase 3 Finding #1, Phase 8 Finding #4) - automated proof of the profile-conditional
 * fail-fast Phase 6 only verified by manually booting the real app. {@code @Import} (not
 * {@code ApplicationContextRunner.withBean}) is required here specifically because {@code @Import}
 * goes through Spring's standard condition-evaluation pipeline, so
 * {@link LinkPropertiesStartupValidation}'s own {@code @Profile("!local")} is genuinely evaluated,
 * not bypassed.
 */
class LinkPropertiesStartupValidationTest {

    @Configuration
    @EnableConfigurationProperties(LinkProperties.class)
    @Import(LinkPropertiesStartupValidation.class)
    static class TestConfig {
    }

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    @Test
    void localProfileBootsCleanWithNoBaseUrl() {
        contextRunner
                .withInitializer(context -> context.getEnvironment().addActiveProfile("local"))
                .withPropertyValues("themistra.notification.link.base-url=")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void nonLocalProfileFailsWithNoBaseUrl() {
        contextRunner
                .withInitializer(context -> context.getEnvironment().addActiveProfile("dev"))
                .withPropertyValues("themistra.notification.link.base-url=")
                .run(context -> assertThat(context.getStartupFailure())
                        .rootCause().isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("themistra.notification.link.base-url")
                        .hasMessageContaining("outside the local profile"));
    }

    @Test
    void nonLocalProfileBootsCleanWhenBaseUrlIsSet() {
        contextRunner
                .withInitializer(context -> context.getEnvironment().addActiveProfile("dev"))
                .withPropertyValues("themistra.notification.link.base-url=https://checky.pro")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void stagingAndProdProfilesAlsoFailWithNoBaseUrl() {
        for (String profile : new String[] {"staging", "prod"}) {
            contextRunner
                    .withInitializer(context -> context.getEnvironment().addActiveProfile(profile))
                    .withPropertyValues("themistra.notification.link.base-url=")
                    .run(context -> assertThat(context).hasFailed());
        }
    }
}
