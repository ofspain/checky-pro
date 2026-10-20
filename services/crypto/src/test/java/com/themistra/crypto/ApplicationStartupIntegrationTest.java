package com.themistra.crypto;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kimi sweep review, Finding 4 (2026-10-20): the whole application starts with none of its own
 * beans replaced.
 *
 * <p>Before the sweep, {@code QuorumEvaluator} was not a bean, so crypto-service could not start in
 * any environment (fixed in {@code af3c1132}). Every other full-application test here replaces some
 * of the application's beans: {@code EndToEndIntegrationTest} mocks {@code WatcherRegistry},
 * {@code ObservationSnapshotStore}, {@code KmsSigner}, {@code ScreeningClient} and
 * {@code HeldFactAlerter}. A wiring fault in one of those, or in anything only they depend on,
 * would pass every other test. This one fails on it.</p>
 *
 * <p>Real Postgres (migrated by Flyway, connected as {@code crypto_app}) and real Kafka. The chain
 * provider and KMS settings are the local defaults; nothing contacts them at startup. {@code
 * KmsSigner} deliberately refuses to start without an AWS region (deployments get one from the
 * environment; {@code KmsSignerSpringWiringTest} pins the refusal), so this test sets
 * {@code aws.region} as that test does, and clears it afterwards.</p>
 */
@SpringBootTest
@Testcontainers
class ApplicationStartupIntegrationTest {

    private static final String CRYPTO_APP_PASSWORD = "it-crypto-app-password";

    /** The beans the end-to-end test replaces, plus the one whose absence once stopped startup. */
    private static final List<String> BEANS_THAT_MUST_BE_REAL = List.of(
            "com.themistra.crypto.watch.WatcherRegistry",
            "com.themistra.crypto.observation.ObservationSnapshotStore",
            "com.themistra.crypto.attest.KmsSigner",
            "com.themistra.crypto.screening.ScreeningClient",
            "com.themistra.crypto.quorum.HeldFactAlerter",
            "com.themistra.crypto.quorum.QuorumEvaluator");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container
    private static final KafkaContainer KAFKA =
            new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.0"));

    @BeforeAll
    static void migrateAndProvisionPassword() throws SQLException {
        System.setProperty("aws.region", "us-east-1");
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("chain")
                .load()
                .migrate();
        try (Connection admin = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword()); Statement statement = admin.createStatement()) {
            statement.execute("ALTER ROLE crypto_app PASSWORD '" + CRYPTO_APP_PASSWORD + "'");
        }
    }

    @AfterAll
    static void clearRegion() {
        System.clearProperty("aws.region");
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "crypto_app");
        registry.add("spring.datasource.password", () -> CRYPTO_APP_PASSWORD);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @Autowired
    private ApplicationContext context;

    @Test
    void theApplicationStartsWithEveryBeanReal() throws ClassNotFoundException {
        for (String typeName : BEANS_THAT_MUST_BE_REAL) {
            Class<?> type = Class.forName(typeName);
            assertThat(context.getBeansOfType(type))
                    .as("exactly one real %s bean", type.getSimpleName())
                    .hasSize(1)
                    .allSatisfy((name, bean) -> assertThat(bean.getClass().getName())
                            .as("%s is not a test double", type.getSimpleName())
                            .doesNotContain("Mockito"));
        }
    }
}
