package com.themistra.crypto.dev;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Opens the explorer, in local development only.
 *
 * <p>Profile-gated rather than flag-gated: outside the {@code local} profile this configuration is
 * not registered at all, so there is no property anyone could set to expose it. The main chain in
 * {@code SecurityConfig} is untouched and still refuses everything else.
 */
@Configuration
@Profile("local")
public class DevSecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain devChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/dev/**", "/", "/index.html")
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable());
        return http.build();
    }
}
