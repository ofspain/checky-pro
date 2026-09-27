package com.themistra.notification.common;

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
 * Single resource-server filter chain (L8). Structurally mirrors {@code services/crypto}'s own
 * {@code ResourceServerConfig} (stateless, CSRF disabled, {@code PublicEndpoints} permitted, JWT
 * bearer, RFC 9457 entry/denied handlers) but omits crypto's {@code /internal/v1/**}
 * scope-authority line entirely (Kimi Phase 3 Finding #2) - no such endpoints exist in this
 * service; the in-app stream/read API this chain protects doesn't exist until task 13. Like
 * crypto, this service only validates JWTs minted by {@code auth-service}: {@code JwtDecoder} is
 * Spring Boot's own autoconfigured bean, sourced from
 * {@code spring.security.oauth2.resourceserver.jwt.jwk-set-uri}/{@code issuer-uri} - no custom
 * decoder or authorities converter here.
 */
@Configuration
@EnableWebSecurity
public class ResourceServerConfig {

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
                    auth.anyRequest().authenticated();
                })
                .oauth2ResourceServer(rs -> rs
                        .jwt(Customizer.withDefaults())
                        .authenticationEntryPoint(problemJsonAuthenticationEntryPoint)
                        .accessDeniedHandler(problemJsonAccessDeniedHandler));

        return http.build();
    }

    /**
     * RFC 9457 body for 401s (`agents.md` Security rule) — Spring Security's default is HTML/plain
     * text. {@code WWW-Authenticate: Bearer} is set per RFC 6750 §3, mirroring crypto's own
     * precedent.
     */
    @Bean
    public AuthenticationEntryPoint problemJsonAuthenticationEntryPoint(ObjectMapper objectMapper) {
        return (request, response, authException) -> {
            response.setHeader("WWW-Authenticate", "Bearer");
            writeProblemJson(response, objectMapper, HttpStatus.UNAUTHORIZED,
                    "Unauthorized", "A valid access token is required.");
        };
    }

    /** RFC 9457 body for 403s (`agents.md` Security rule) — Spring Security's default is HTML/plain text. */
    @Bean
    public AccessDeniedHandler problemJsonAccessDeniedHandler(ObjectMapper objectMapper) {
        return (request, response, accessDeniedException) -> writeProblemJson(
                response, objectMapper, HttpStatus.FORBIDDEN,
                "Forbidden", "The token does not carry the required access.");
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
