package com.themistra.notification;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AC2 (Kimi Phase 8 Finding #5) - mirrors {@code NotificationBaselineMigrationIntegrationTest}'s
 * fast, non-Spring-context, non-Docker technique: reads the real committed
 * {@code application.properties} directly and asserts the datasource/JPA/Flyway/Kafka keys are
 * present with the expected values. Deliberately not inside a {@code @Testcontainers} class - this
 * one has no reason to need Docker. Mirrors {@code services/crypto}'s own
 * {@code ApplicationPropertiesJpaConfigTest}.
 */
class ApplicationPropertiesJpaConfigTest {

    private Properties loadApplicationProperties() throws IOException {
        Properties properties = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            assertThat(in).as("application.properties must be on the test classpath").isNotNull();
            properties.load(in);
        }
        return properties;
    }

    @Test
    void ddlAutoIsValidateAndOpenInViewIsDisabled() throws IOException {
        Properties properties = loadApplicationProperties();
        assertThat(properties.getProperty("spring.jpa.hibernate.ddl-auto"))
                .as("no @Entity exists yet - validate is a harmless, forward-safe default")
                .isEqualTo("validate");
        assertThat(properties.getProperty("spring.jpa.open-in-view")).isEqualTo("false");
    }

    @Test
    void flywayIsDisabledAtRuntime() throws IOException {
        Properties properties = loadApplicationProperties();
        assertThat(properties.getProperty("spring.flyway.enabled"))
                .as("migrations only ever run via the Maven plugin, never the running application")
                .isEqualTo("false");
    }

    @Test
    void datasourceUsesTheLeastPrivilegeRuntimeRole() throws IOException {
        Properties properties = loadApplicationProperties();
        assertThat(properties.getProperty("spring.datasource.username"))
                .as("must connect as notification_app (T02), never the migration-owning role")
                .isEqualTo("${DB_USERNAME:notification_app}");
        assertThat(properties.getProperty("spring.datasource.hikari.connection-init-sql"))
                .as("Kimi Phase 11 Gap #6: exact order matters - notifications must be first for "
                        + "runtime table resolution, public must be present for citext visibility")
                .isEqualTo("SET search_path TO notifications, public");
        assertThat(properties.getProperty("spring.datasource.password"))
                .as("Kimi Phase 11 Gap #8: must stay an env-var placeholder with a local-only "
                        + "default, never a hardcoded real credential (L10)")
                .startsWith("${DB_PASSWORD:")
                .endsWith("}");
    }

    @Test
    void kafkaBootstrapServersIsDeclared() throws IOException {
        Properties properties = loadApplicationProperties();
        assertThat(properties.getProperty("spring.kafka.bootstrap-servers")).isNotBlank();
    }
}
