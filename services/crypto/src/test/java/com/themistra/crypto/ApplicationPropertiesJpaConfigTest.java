package com.themistra.crypto;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 11 Gap 8 — mirrors {@code ChainBaselineMigrationIntegrationTest.runtimeFlywayIsDisabledInApplicationProperties}'s
 * fast, non-Spring-context, non-Docker technique: reads the real committed {@code application.properties}
 * directly and asserts the two JPA properties Phase 9 added (Finding 1) are still present. Deliberately
 * NOT inside a {@code @Testcontainers} class - unlike that test, this one has no reason to need Docker.
 */
class ApplicationPropertiesJpaConfigTest {

    @Test
    void ddlAutoIsValidateAndOpenInViewIsDisabled() throws IOException {
        Properties properties = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            assertThat(in).as("application.properties must be on the test classpath").isNotNull();
            properties.load(in);
        }

        assertThat(properties.getProperty("spring.jpa.hibernate.ddl-auto"))
                .as("OutboxEvent is the first JPA entity in this service (Phase 7/9 Finding 1) - a "
                        + "mapping mismatch must fail at boot, not surface as a runtime SQL error")
                .isEqualTo("validate");
        assertThat(properties.getProperty("spring.jpa.open-in-view"))
                .isEqualTo("false");
    }
}
