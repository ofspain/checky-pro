package com.themistra.payment.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single resource-server filter chain (L10, agents.md Security rule). This service validates
 * JWTs minted elsewhere: {@code JwtDecoder} is Spring Boot's own autoconfigured bean, sourced
 * from {@code spring.security.oauth2.resourceserver.jwt.jwk-set-uri}/{@code issuer-uri}
 * (auth-service's {@code /oauth2/jwks}) — no custom decoder bean is declared here. {@code aud} is
 * deliberately not validated — auth's {@code client_credentials} tokens carry the calling
 * client's own id as {@code aud}, not a resource indicator, so an audience check here would
 * reject every legitimately issued token, not attacker traffic (same rationale already
 * established for {@code crypto-service}/{@code notification-service}).
 *
 * <p><b>T06</b> — this task's own `POST /api/v1/invoices` is the first authority-restricted path
 * (Phase 3 findings 1/3, no longer theoretical, per T03's own disclosed gap). {@code
 * @EnableMethodSecurity} and a {@code JwtAuthenticationConverter} wired to {@link
 * JwtRoleAuthoritiesConverter} are added so {@code @PreAuthorize("hasRole('MERCHANT')")} means
 * something — mirrors {@code auth-service}'s own identical {@code SecurityChainsConfig}
 * wiring.</p>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class ResourceServerConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            AuthenticationEntryPoint problemJsonAuthenticationEntryPoint,
            AccessDeniedHandler problemJsonAccessDeniedHandler) throws Exception {
        http
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Stateless bearer-only API, no session-backed page ever exists on this service -
                // CSRF protects session/cookie auth, which this resource server never uses.
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(PublicEndpoints.PATTERNS).permitAll();
                    auth.anyRequest().authenticated();
                })
                .oauth2ResourceServer(rs -> rs
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(problemJsonAuthenticationEntryPoint)
                        .accessDeniedHandler(problemJsonAccessDeniedHandler));

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter(
            JwtRoleAuthoritiesConverter rolesConverter) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(rolesConverter);
        return converter;
    }

    /**
     * RFC 9457 body for 401s (agents.md Security rule) — Spring Security's default is HTML/plain
     * text. {@code WWW-Authenticate: Bearer} is set per RFC 6750 §3, matching both sibling
     * services' own identical precedent.
     */
    @Bean
    public AuthenticationEntryPoint problemJsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
        return (request, response, authException) -> {
            response.setHeader("WWW-Authenticate", "Bearer");
            writeProblemJson(response, objectMapper, HttpStatus.UNAUTHORIZED, ProblemTypes.UNAUTHORIZED,
                    "Unauthorized", "A valid access token is required.");
        };
    }

    /** RFC 9457 body for 403s (agents.md Security rule) — Spring Security's default is HTML/plain text. */
    @Bean
    public AccessDeniedHandler problemJsonAccessDeniedHandler(ObjectMapper objectMapper) {
        return (request, response, accessDeniedException) -> writeProblemJson(
                response, objectMapper, HttpStatus.FORBIDDEN, ProblemTypes.FORBIDDEN,
                "Forbidden", "The token does not carry the required access.");
    }

    /** {@code type} is a {@link URI}, not a {@code String} (T23/R29) - forces every caller
     * through a real {@link ProblemTypes} constant rather than an arbitrary inline literal. */
    private static void writeProblemJson(
            HttpServletResponse response, ObjectMapper objectMapper, HttpStatus status, URI type,
            String title, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/problem+json");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", type.toString());
        body.put("title", title);
        body.put("status", status.value());
        body.put("detail", detail);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
