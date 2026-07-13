package com.themistra.auth.token;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/** Replaces SAS's default in-memory OAuth2AuthorizationService, resolving half of D-015. */
@Configuration
public class AuthorizationServiceConfig {

    @Bean
    public OAuth2AuthorizationService authorizationService(
            JdbcTemplate jdbcTemplate,
            RegisteredClientRepository registeredClientRepository,
            RefreshTokenTracker tracker) {
        JdbcOAuth2AuthorizationService delegate =
                new JdbcOAuth2AuthorizationService(jdbcTemplate, registeredClientRepository);
        return new ReuseDetectingAuthorizationService(delegate, tracker);
    }
}
