package com.themistra.crypto.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Who may call this service (ARCHITECTURE §8: JWT validation lives in each service, zero trust).
 *
 * <p>A valid token is not an authorisation. Every service in the estate validates against the same
 * issuer, so a token minted for the notification service would otherwise open watch registration.
 * The internal API additionally requires a scope naming this capability.
 *
 * <p>Liveness and readiness stay open so orchestration works without credentials. Everything else
 * under management is closed, because the chain-configuration detail names chains and provider
 * counts.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    /**
     * Refuses to start a deployment that cannot authenticate anyone.
     *
     * <p>The guarded failure is a service that looks healthy while silently accepting everyone —
     * the same failure D-011 guards in auth. Local development may run without an issuer by
     * setting the flag off explicitly.
     */
    public SecurityConfig(SecurityProperties properties) {
        if (properties.isRequireIssuer() && !properties.hasIssuer()) {
            throw new IllegalStateException(
                    "themistra.crypto.security.issuer-uri is not configured and require-issuer is true — "
                            + "refusing to start a deployment that cannot validate a caller (ARCHITECTURE 8). "
                            + "Set the issuer, or set require-issuer=false for local development only.");
        }
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, SecurityProperties properties) throws Exception {
        String scope = "SCOPE_" + properties.getWatchScope();

        http
                .authorizeHttpRequests(auth -> auth
                        // Orchestration must be able to probe without credentials.
                        .requestMatchers("/actuator/health/liveness", "/actuator/health/readiness").permitAll()
                        // Everything else under management can disclose configuration.
                        .requestMatchers("/actuator/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/internal/**").hasAuthority(scope)
                        .requestMatchers("/internal/**").hasAuthority(scope)
                        .anyRequest().authenticated())
                // Bearer-authenticated and stateless: there is no session for CSRF to protect.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        // The decoder is built here rather than as a conditional @Bean: @ConditionalOnProperty
        // matches on a property being *present*, and an environment default of "" is present but
        // empty, so the bean was constructed with an empty issuer and startup failed. Building it
        // inside this branch ties its existence to the only condition that actually matters.
        if (properties.hasIssuer()) {
            http.oauth2ResourceServer(rs -> rs.jwt(jwt -> jwt
                    .decoder(JwtDecoders.fromIssuerLocation(properties.getIssuerUri()))
                    .jwtAuthenticationConverter(scopeAuthorities())));
        }
        return http.build();
    }

    /**
     * Maps the {@code scope} claim to {@code SCOPE_*} authorities.
     *
     * <p>No role mapping: this service authorises machine callers by capability, not people by
     * role. Roles arrive if and when a human-facing endpoint does.
     */
    private static JwtAuthenticationConverter scopeAuthorities() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthorityPrefix("SCOPE_");
        authorities.setAuthoritiesClaimName("scope");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
