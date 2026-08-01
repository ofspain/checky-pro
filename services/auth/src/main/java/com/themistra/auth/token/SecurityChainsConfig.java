package com.themistra.auth.token;

import com.themistra.auth.authn.LoginFailureHandler;
import com.themistra.auth.authn.LoginSuccessHandler;
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
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

/**
 * Two filter chains (target-design §5):
 *
 * 1. SAS protocol chain — /oauth2/*, /.well-known/*, /userinfo, OIDC enabled. Browser flows
 *    without a session are sent to /login; the MFA step (D-014) plugs into this chain's
 *    authentication in a later stage.
 * 2. Application chain — the CI-enforced public list (PublicEndpoints), then JWT-authenticated
 *    APIs (this service is a resource server for its own management endpoints) with the
 *    {@code roles} claim mapped to ROLE_* authorities so method security means something.
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
    public SecurityFilterChain applicationChain(
            HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter,
            LoginFailureHandler loginFailureHandler, LoginSuccessHandler loginSuccessHandler) throws Exception {
        http
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers(PublicEndpoints.PATTERNS).permitAll();
                    PublicEndpoints.METHOD_SCOPED.forEach(
                            m -> auth.requestMatchers(m.method(), m.pattern()).permitAll());
                    auth.anyRequest().authenticated();
                })
                // APIs are bearer-authenticated and stateless; CSRF protects only the
                // session-backed login/authorize pages
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .oauth2ResourceServer(rs -> rs.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .formLogin(form -> form
                        .failureHandler(loginFailureHandler)
                        .successHandler(loginSuccessHandler));

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter(
            JwtRoleAuthoritiesConverter rolesConverter) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(rolesConverter);
        return converter;
    }
}
