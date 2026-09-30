package com.themistra.notification.common.config;

import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Enforces that {@link EmailProperties#transport()} is exactly {@code "ses"} or {@code "fake"} -
 * mirrors {@link LinkPropertiesStartupValidation}'s own established pattern (Kimi Phase 8
 * Finding #8, self-review Finding #5). Unlike that validation, this one is unconditional (no
 * {@code @Profile} guard): an invalid value is wrong in every profile, not only outside
 * {@code local}, since there is no environment where a third value is ever legitimate.
 *
 * <p>Without this component, an invalid value already fails startup - Spring cannot satisfy
 * {@code EmailChannel}'s own {@code EmailTransport} constructor dependency, since neither
 * {@code SesEmailTransport} nor {@code FakeEmailTransport}'s own {@code @ConditionalOnProperty}
 * matches - but with a generic, unhelpful {@code NoSuchBeanDefinitionException} that does not name
 * the actual misconfigured property or its allowed values. This component runs eagerly (in the
 * constructor, at context-wiring time) and fails first, with a clear message.</p>
 */
@Component
class EmailTransportStartupValidation {

    private static final Set<String> ALLOWED_VALUES = Set.of("ses", "fake");

    EmailTransportStartupValidation(EmailProperties emailProperties) {
        if (!ALLOWED_VALUES.contains(emailProperties.transport())) {
            throw new IllegalStateException(
                    "themistra.notification.email.transport must be one of " + ALLOWED_VALUES
                            + ", got: " + emailProperties.transport());
        }
    }
}
