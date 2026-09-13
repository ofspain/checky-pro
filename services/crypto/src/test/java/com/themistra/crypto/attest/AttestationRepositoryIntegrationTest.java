package com.themistra.crypto.attest;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.flywaydb.core.Flyway;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** AC5 (real, DB-enforced {@code INSERT, SELECT}-but-not-{@code UPDATE}/{@code DELETE} grant, added by
 * {@code V2__crypto_app_role_and_grants.sql}), proven end-to-end against a real Postgres - mirrors
 * {@code ScreeningResultRepositoryIntegrationTest}'s own role-connection pattern exactly. */
@Testcontainers
@SpringBootTest(classes = AttestationRepositoryIntegrationTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AttestationRepositoryIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    private static final String CRYPTO_APP_PASSWORD = "it-crypto-app-password";
    private static final Instant CREATED_AT = Instant.parse("2026-09-14T00:00:00Z");
    private static final Instant SIGNED_AT = Instant.parse("2026-09-14T00:00:01Z");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "crypto_app");
        registry.add("spring.datasource.password", () -> CRYPTO_APP_PASSWORD);
    }

    @BeforeAll
    static void migrateAndProvisionLocalPassword() throws SQLException {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("chain")
                .load()
                .migrate();

        try (Connection admin = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
             Statement statement = admin.createStatement()) {
            statement.execute("ALTER ROLE crypto_app PASSWORD '" + CRYPTO_APP_PASSWORD + "'");
        }
    }

    @Autowired
    private AttestationRepository repository;

    @Test
    void savesAndReadsBackASignedRow() {
        Attestation saved = repository.save(Attestation.create("ETHEREUM", "0xtx", "d".repeat(64),
                AttestOutcome.SIGNED, "arn:aws:kms:us-east-1:111122223333:key/abc", SIGNED_AT, CREATED_AT));

        Optional<Attestation> reloaded = repository.findById(saved.id());

        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().outcome()).isEqualTo(AttestOutcome.SIGNED);
        assertThat(reloaded.get().kmsKeyId()).isEqualTo("arn:aws:kms:us-east-1:111122223333:key/abc");
        assertThat(reloaded.get().signedAt()).isEqualTo(SIGNED_AT);
    }

    @Test
    void savesAndReadsBackARowWithNullKmsKeyIdAndNullSignedAt() {
        Attestation saved = repository.save(Attestation.create("TRON", "0xtx2", "e".repeat(64),
                AttestOutcome.REFUSED, null, null, CREATED_AT));

        Optional<Attestation> reloaded = repository.findById(saved.id());

        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().kmsKeyId()).isNull();
        assertThat(reloaded.get().signedAt()).isNull();
    }

    @Test
    void deleteFailsAtTheDatabaseLevel() {
        Attestation saved = repository.save(Attestation.create("ETHEREUM", "0xdelete-test",
                "f".repeat(64), AttestOutcome.BLOCKED, null, null, CREATED_AT));

        assertThatThrownBy(() -> {
            repository.delete(saved);
            repository.flush();
        }).isInstanceOf(InvalidDataAccessResourceUsageException.class);

        assertThat(repository.findById(saved.id())).isPresent();
    }

    @Test
    void updateFailsAtTheDatabaseLevel() throws SQLException {
        repository.save(Attestation.create("ETHEREUM", "0xupdate-test", "a".repeat(64),
                AttestOutcome.REFUSED, null, null, CREATED_AT));

        try (Connection app = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "crypto_app", CRYPTO_APP_PASSWORD);
             Statement statement = app.createStatement()) {
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE chain.attestations SET outcome = 'SIGNED' WHERE tx_hash = '0xupdate-test'"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
        }
    }

    @Test
    void checkConstraintRejectsAnOutcomeStringOutsideTheThreeAllowedValues() throws SQLException {
        try (Connection app = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "crypto_app", CRYPTO_APP_PASSWORD);
             Statement statement = app.createStatement()) {
            assertThatThrownBy(() -> statement.execute(
                    "INSERT INTO chain.attestations (chain, tx_hash, receipt_digest, outcome, created_at) "
                            + "VALUES ('ETHEREUM', '0xinvalid-outcome', '" + "b".repeat(64)
                            + "', 'INVALID', now())"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("chk_attest_outcome");
        }
    }

    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = Attestation.class)
    @EnableJpaRepositories(basePackageClasses = AttestationRepository.class)
    static class TestConfig {

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }
}
