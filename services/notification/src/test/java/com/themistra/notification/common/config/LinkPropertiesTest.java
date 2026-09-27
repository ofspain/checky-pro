package com.themistra.notification.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * AC1 - real property binding via {@link ApplicationContextRunner} (self-review Finding 1).
 * {@code LinkProperties} itself is deliberately unconstrained (no {@code @NotBlank}) - the blank
 * case is expected to succeed here; {@link LinkPropertiesStartupValidationTest} covers the
 * profile-conditional non-blank requirement.
 */
class LinkPropertiesTest {

    @Test
    void constructsWithNullOrBlankBaseUrl() {
        assertThatCode(() -> new LinkProperties(null)).doesNotThrowAnyException();
        assertThatCode(() -> new LinkProperties("")).doesNotThrowAnyException();
    }

    @Configuration
    @EnableConfigurationProperties(LinkProperties.class)
    static class TestConfig {
    }

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner().withUserConfiguration(TestConfig.class);

    @Test
    void bindsFromTheRealPrefixAndKeyName() {
        contextRunner.withPropertyValues("themistra.notification.link.base-url=https://checky.pro")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(LinkProperties.class).baseUrl()).isEqualTo("https://checky.pro");
                });
    }

    @Test
    void bindsSuccessfullyWithBlankValue_matchingTheLocalProfileDefault() {
        contextRunner.withPropertyValues("themistra.notification.link.base-url=")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(LinkProperties.class).baseUrl()).isEmpty();
                });
    }
}
