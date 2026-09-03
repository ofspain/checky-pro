package com.themistra.crypto.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single resource-server filter chain (R27, agents.md Security rule). Unlike {@code services/auth}
 * (an OAuth2 <em>issuer</em> with local key material and a two-chain SAS+application split), this
 * service only validates JWTs minted elsewhere: {@code JwtDecoder} is Spring Boot's own
 * autoconfigured bean, sourced from {@code spring.security.oauth2.resourceserver.jwt.jwk-set-uri}
 * (auth-service's {@code /oauth2/jwks}) — no custom decoder bean is declared here. The default
 * {@code JwtGrantedAuthoritiesConverter} reading the {@code scope} claim into {@code SCOPE_*}
 * authorities is sufficient (no custom converter, unlike auth's role-claim mapping); {@code aud} is
 * deliberately not validated — auth's {@code client_credentials} tokens carry the calling client's
 * own id as {@code aud}, not a resource indicator (contracts/api/token-claims.md Path 2), so an
 * audience check here would reject every legitimately issued token, not attacker traffic.
 *
 * <p>{@code /internal/v1/**} (not {@code /internal/v1/*}) is deliberate: {@code DELETE
 * /internal/v1/watches/{watchId}} is two path segments below {@code /internal/v1}, which a
 * single-segment matcher would not cover.</p>
 */
@Configuration
@EnableWebSecurity
public class ResourceServerConfig {

    private static final String INTERNAL_SCOPE_AUTHORITY = "SCOPE_internal.crypto:write";

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationEntryPoint problemJsonAuthenticationEntryPoint,
            AccessDeniedHandler problemJsonAccessDeniedHandler) throws Exception {
        http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Stateless bearer-only API, no session-backed page ever exists on this service —
                // CSRF protects session/cookie auth, which this resource server never uses.
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(PublicEndpoints.PATTERNS).permitAll();
                    auth.requestMatchers("/internal/v1/**").hasAuthority(INTERNAL_SCOPE_AUTHORITY);
                    auth.anyRequest().authenticated();
                })
                .oauth2ResourceServer(rs -> rs
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(problemJsonAuthenticationEntryPoint)
                        .accessDeniedHandler(problemJsonAccessDeniedHandler));

        return http.build();
    }

    /**
     * RFC 9457 body for 401s (agents.md Security rule) — Spring Security's default is HTML/plain
     * text. {@code WWW-Authenticate: Bearer} is set per RFC 6750 §3 (Phase 9 Finding) — some HTTP
     * clients/edge proxies expect it on a Bearer-scheme 401 regardless of the body format.
     */
    @Bean
    public AuthenticationEntryPoint problemJsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
        return (request, response, authException) -> {
            response.setHeader("WWW-Authenticate", "Bearer");
            writeProblemJson(response, objectMapper, HttpStatus.UNAUTHORIZED,
                    "Unauthorized", "A valid service-to-service token is required.");
        };
    }

    /** RFC 9457 body for 403s (agents.md Security rule) — Spring Security's default is HTML/plain text. */
    @Bean
    public AccessDeniedHandler problemJsonAccessDeniedHandler(ObjectMapper objectMapper) {
        return (request, response, accessDeniedException) -> writeProblemJson(
                response, objectMapper, HttpStatus.FORBIDDEN,
                "Forbidden", "The token does not carry the required scope.");
    }

    private static void writeProblemJson(
            HttpServletResponse response, ObjectMapper objectMapper, HttpStatus status,
            String title, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/problem+json");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", title);
        body.put("status", status.value());
        body.put("detail", detail);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
