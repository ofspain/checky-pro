package com.themistra.notification.common.config;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Enforces L10's "startup fails on missing config in non-local profiles" for
 * {@link LinkProperties#baseUrl()} specifically (Kimi Phase 3 Finding #1). {@code LinkProperties}
 * itself carries no {@code @NotBlank}, because the spec's own VERBATIM default
 * ({@code themistra.notification.link.base-url=${AUTH_EMAIL_LINK_BASE_URL:}}) binds blank in the
 * {@code local} profile - an unconditional constraint there would break local startup. This
 * component is registered only when the active profile is not {@code local}, so Spring only ever
 * runs this check outside {@code local}, and it runs it eagerly (in the constructor, at
 * context-wiring time), not lazily on first use.
 */
@Component
@Profile("!local")
class LinkPropertiesStartupValidation {

    LinkPropertiesStartupValidation(LinkProperties linkProperties) {
        if (linkProperties.baseUrl() == null || linkProperties.baseUrl().isBlank()) {
            throw new IllegalStateException(
                    "themistra.notification.link.base-url is required outside the local profile");
        }
    }
}
