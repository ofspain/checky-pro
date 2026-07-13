package com.themistra.auth.token;

import com.themistra.auth.common.PublicEndpoints;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

/**
 * Two filter chains (target-design §5):
 *
 * 1. SAS protocol chain — /oauth2/*, /.well-known/*, /userinfo, OIDC enabled. Browser flows
 *    without a session are sent to /login; the MFA step (D-014) plugs into this chain's
 *    authentication in a later stage.
 * 2. Application chain — everything else: the CI-enforced public list, then JWT-authenticated
 *    APIs (this service is a resource server for its own management endpoints), plus form
 *    login for the interactive authorize flow.
 *
 * Interim (replaced in the JWT stage, D-015): token signing uses Boot's autoconfigured
 * in-memory dev JWKS, and authorizations live in SAS's default in-memory store.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityChainsConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServer =
                OAuth2AuthorizationServerConfigurer.authorizationServer();

        http
                .securityMatcher(authorizationServer.getEndpointsMatcher())
                .with(authorizationServer, server -> server.oidc(Customizer.withDefaults()))
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .exceptionHandling(ex -> ex.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain applicationChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PublicEndpoints.PATTERNS).permitAll()
                        .anyRequest().authenticated())
                // APIs are bearer-authenticated and stateless; CSRF protects only the
                // session-backed login/authorize pages
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .oauth2ResourceServer(rs -> rs.jwt(Customizer.withDefaults()))
                .formLogin(Customizer.withDefaults());

        return http.build();
    }
}
