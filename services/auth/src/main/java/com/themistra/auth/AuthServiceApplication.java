package com.themistra.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Themistra Authentication Service — the platform's OIDC/OAuth2 issuer.
 *
 * <p>Architecture: services/auth/docs/architecture/target-design.md.
 * Decision log: services/auth/docs/architecture/auth-decisions.md.</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
