package com.themistra.payment.archtestfixtures;

import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;

/**
 * T25 (L10). This class calls {@code AuthorizedUrl.permitAll()} on purpose, from outside
 * {@code ResourceServerConfig}. That lets {@code ArchitectureTest}'s public-endpoint allowlist
 * rule be shown to fail against a real violation, not just pass on clean code. It serves the
 * same purpose as {@link RogueInlineProblemTypeUser} does for T23.
 *
 * <p>It is not a Spring bean. It has no annotations, and {@code archtestfixtures} is never
 * component-scanned. So it can never contribute a real security rule.
 */
public class RoguePermitAllUser {

    void openUp(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth.requestMatchers("/rogue/**").permitAll();
    }
}
