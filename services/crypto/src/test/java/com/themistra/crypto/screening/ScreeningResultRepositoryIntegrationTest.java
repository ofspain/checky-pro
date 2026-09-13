package com.themistra.crypto.screening;

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
 * {@code V9__crypto_app_screening_results_grant.sql}), proven end-to-end against a real Postgres -
 * mirrors {@code TokenAllowlistRepositoryIntegrationTest}'s own role-connection pattern: the datasource
 * connects as {@code crypto_app} (not the migration-owning role), so every {@code repository.save(...)}
 * call in this class already exercises the real {@code INSERT}/{@code SELECT} grant, not just a
 * hypothetical one. */
@Testcontainers
@SpringBootTest(classes = ScreeningResultRepositoryIntegrationTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ScreeningResultRepositoryIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    private static final String CRYPTO_APP_PASSWORD = "it-crypto-app-password";
    private static final Instant SCREENED_AT = Instant.parse("2026-09-12T00:00:00Z");

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
    private ScreeningResultRepository repository;

    @Test
    void savesAndReadsBackAFullyPopulatedRow() {
        ScreeningResult saved = repository.save(ScreeningResult.create("ETHEREUM", "0xfull", "0xtx",
                ScreeningOutcome.BLOCKED, "chainalysis", "{\"hit\":true}", SCREENED_AT));

        Optional<ScreeningResult> reloaded = repository.findById(saved.id());

        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().chain()).isEqualTo("ETHEREUM");
        assertThat(reloaded.get().address()).isEqualTo("0xfull");
        assertThat(reloaded.get().txHash()).isEqualTo("0xtx");
        assertThat(reloaded.get().outcome()).isEqualTo(ScreeningOutcome.BLOCKED);
        assertThat(reloaded.get().provider()).isEqualTo("chainalysis");
        // Postgres JSONB re-serializes on round-trip (e.g. adds a space after ':') - compare ignoring
        // whitespace rather than asserting byte-for-byte equality with what was written.
        assertThat(reloaded.get().rawResponse()).isEqualToIgnoringWhitespace("{\"hit\":true}");
        assertThat(reloaded.get().screenedAt()).isEqualTo(SCREENED_AT);
    }

    @Test
    void savesAndReadsBackARowWithNullTxHashAndNullRawResponse() {
        ScreeningResult saved = repository.save(ScreeningResult.create("TRON", "Tsparse", null,
                ScreeningOutcome.ERROR, FailClosedScreeningClient.PROVIDER_NAME, null, SCREENED_AT));

        Optional<ScreeningResult> reloaded = repository.findById(saved.id());

        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().txHash()).isNull();
        assertThat(reloaded.get().rawResponse()).isNull();
    }

    @Test
    void savesAndReadsBackARowWithClearedOutcome() {
        // Phase 11 (Kimi) Gap 7: CLEARED is the only outcome that may ever lead to a signature
        // downstream (L12-T19a) but was never round-tripped through the real converter/DB path -
        // only BLOCKED and ERROR were, above.
        ScreeningResult saved = repository.save(ScreeningResult.create("ETHEREUM", "0xcleared", "0xtx",
                ScreeningOutcome.CLEARED, "chainalysis", null, SCREENED_AT));

        Optional<ScreeningResult> reloaded = repository.findById(saved.id());

        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().outcome()).isEqualTo(ScreeningOutcome.CLEARED);
    }

    @Test
    void deleteFailsAtTheDatabaseLevel() {
        // V9 grants INSERT, SELECT only - no DELETE. A Postgres permission-denied error (SQLState
        // 42501) translates via Hibernate/Spring to InvalidDataAccessResourceUsageException, not
        // DataIntegrityViolationException (that one is reserved for constraint violations) - verified
        // by running this test and reading the actual thrown type, not assumed from
        // TokenAllowlistRepositoryIntegrationTest's own precedent (which asserts the narrower type and
        // is one of this service's disclosed, already-failing pre-existing integration tests).
        ScreeningResult saved = repository.save(ScreeningResult.create("ETHEREUM", "0xdelete-test", null,
                ScreeningOutcome.ERROR, "provider", null, SCREENED_AT));

        assertThatThrownBy(() -> {
            repository.delete(saved);
            repository.flush();
        }).isInstanceOf(InvalidDataAccessResourceUsageException.class);

        // Phase 11 (Kimi) Gap 4: characterize the failure mode, not just that some exception is
        // thrown - the row must genuinely still be there afterward, on a fresh find (the failed
        // delete/flush left the persistence context in an undefined state for the managed instance).
        assertThat(repository.findById(saved.id())).isPresent();
    }

    @Test
    void updateFailsAtTheDatabaseLevel() throws SQLException {
        // ScreeningResult has no mutator at all, so no JPA-entity code path can even attempt an UPDATE -
        // raw JDBC is the only way to exercise this, mirroring
        // TokenAllowlistRepositoryIntegrationTest's own established pattern for exactly this situation.
        repository.save(ScreeningResult.create("ETHEREUM", "0xupdate-test", null, ScreeningOutcome.ERROR,
                "provider", null, SCREENED_AT));

        try (Connection app = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "crypto_app", CRYPTO_APP_PASSWORD);
             Statement statement = app.createStatement()) {
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE chain.screening_results SET provider = 'HACKED' WHERE address = '0xupdate-test'"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");
        }
    }

    @Test
    void checkConstraintRejectsAnOutcomeStringOutsideTheThreeAllowedValues() throws SQLException {
        // Phase 11 (Kimi) Gap 5: the application-level enum/converter make an invalid value hard to
        // insert through this codebase's own code, but the spec's actual safety net is
        // chk_screening_outcome itself - a migration drift that weakened/dropped it would not be
        // caught by any other test in this suite. Raw JDBC, mirroring updateFailsAtTheDatabaseLevel's
        // own established pattern for reaching outside the entity's own guardrails.
        try (Connection app = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "crypto_app", CRYPTO_APP_PASSWORD);
             Statement statement = app.createStatement()) {
            assertThatThrownBy(() -> statement.execute(
                    "INSERT INTO chain.screening_results (chain, address, outcome, provider, screened_at) "
                            + "VALUES ('ETHEREUM', '0xinvalid-outcome', 'INVALID', 'provider', now())"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("chk_screening_outcome");
        }
    }

    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = ScreeningResult.class)
    @EnableJpaRepositories(basePackageClasses = ScreeningResultRepository.class)
    static class TestConfig {

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }
    }
}
