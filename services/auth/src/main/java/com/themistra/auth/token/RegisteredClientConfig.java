package com.themistra.auth.token;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/**
 * Clients persist in oauth2_registered_client (Flyway V1) so every replica resolves the same
 * client ids that stored authorizations reference. Seeding runs after the context is ready —
 * i.e. after Flyway.
 */
@Configuration
public class RegisteredClientConfig {

    @Bean
    public RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbcTemplate) {
        return new JdbcRegisteredClientRepository(jdbcTemplate);
    }

    @Bean
    public ApplicationRunner registeredClientSeedRunner(RegisteredClientSeeder seeder) {
        return args -> seeder.seed();
    }
}
