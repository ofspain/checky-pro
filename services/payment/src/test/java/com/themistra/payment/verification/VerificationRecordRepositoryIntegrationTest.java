package com.themistra.payment.verification;

import com.themistra.payment.payment.PaymentState;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T10 (Phase 3/4/8 Finding 3, AC8): a real Testcontainers proof that the JSONB {@code detail}
 * mapping (Hibernate 6's {@code @JdbcTypeCode(SqlTypes.JSON)} - the first JSONB column mapped
 * anywhere in this codebase) actually round-trips against a real Postgres, connected as {@code
 * payment_app} (not the migration-owning role) - the same narrow-context pattern {@code
 * InvoiceRepositoryIntegrationTest} (T08) and {@code WatchRepositoryIntegrationTest}
 * (crypto-service) established.
 *
 * <p><b>Cannot be run locally this session</b> - Docker is unavailable throughout. Written now
 * anyway, per this session's own established T08 lesson ("write the test even if it can't run
 * locally yet - reasoning through it can surface defects a mocked test never would"). Disclosed,
 * carried-forward gap, same as every prior Testcontainers-backed test this pipeline;
 * {@code VerificationRecordRepository} has no {@code @Modifying} custom query method, so it is
 * not at risk of T08's own {@code InvoiceRepositoryIntegrationTest} bug
 * ({@code TransactionRequiredException} from calling a {@code @Modifying} method outside a
 * transaction) - {@code SimpleJpaRepository}'s own inherited {@code save}/{@code findById}
 * already carry their own transactional wrapper.</p>
 */
@Testcontainers
@SpringBootTest(classes = VerificationRecordRepositoryIntegrationTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class VerificationRecordRepositoryIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    private static final String PAYMENT_APP_PASSWORD = "it-payment-app-password";

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "payment_app");
        registry.add("spring.datasource.password", () -> PAYMENT_APP_PASSWORD);
    }

    @BeforeAll
    static void migrateAndProvisionLocalPassword() throws SQLException {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("payments")
                .load()
                .migrate();

        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            statement.execute("ALTER ROLE payment_app PASSWORD '" + PAYMENT_APP_PASSWORD + "'");
        }
    }

    @Autowired
    private VerificationRecordRepository repository;

    /** AC8: a non-trivial, nested map (a string, a number, and a nested map), not just a flat
     * string map, proves the JSON mapping handles more than the simplest possible shape. */
    @Test
    void shouldRoundTripDetailJsonbMapping() throws SQLException {
        long paymentId = seedPaymentAsAdmin();
        Map<String, Object> nested = new HashMap<>();
        nested.put("reportedConfirmations", 2);
        Map<String, Object> detail = new HashMap<>();
        detail.put("reason", "stale");
        detail.put("attemptedConfirmations", 5);
        detail.put("context", nested);

        VerificationRecord saved = repository.save(VerificationRecord.create(
                paymentId, null, PaymentState.FINALIZED, "ETHEREUM:0xabc:chain.tx.seen", null, detail,
                Instant.now()));

        VerificationRecord retrieved = repository.findById(saved.getId()).orElseThrow();

        assertThat(retrieved.getDetail()).containsExactlyInAnyOrderEntriesOf(detail);
    }

    /** Hibernate JSON audit (open-issues sweep, 2026-10-19). A {@code detail} value whose boxed
     * type doesn't survive a generic JSON round-trip as itself ({@code Short}, {@code Long}) made
     * Hibernate's dirty check see a change right after the INSERT and issue an UPDATE, which
     * {@code payment_app} may not run on this append-only table. {@code LedgerEntry} hit this for
     * real; {@code @Immutable} on {@code VerificationRecord} closes it here before any caller
     * puts such a value in. Runs as {@code payment_app}, so the missing UPDATE grant is real. */
    @Test
    void shouldSaveADetailHoldingShortAndLongValuesWithoutASpuriousUpdate() throws SQLException {
        long paymentId = seedPaymentAsAdmin();
        Map<String, Object> detail = new HashMap<>();
        detail.put("tokenDecimals", (short) 6);
        detail.put("blockNumber", 21_000_000L);

        VerificationRecord saved = repository.save(VerificationRecord.create(
                paymentId, PaymentState.SEEN, PaymentState.CONFIRMING, "ETHEREUM:0xdef:chain.tx.confirmed", 3,
                detail, Instant.now()));

        assertThat(repository.findById(saved.getId()).orElseThrow().getDetail())
                .containsEntry("tokenDecimals", 6).containsEntry("blockNumber", 21_000_000);
    }

    /** AC3's own real-DB counterpart: a {@code recordIgnored}-shaped row (null {@code detail},
     * null {@code fromState}) must round-trip too - the JSONB mapping must not choke on a null
     * value. */
    @Test
    void shouldPersistRecordWithNullDetailAndNullFromState() throws SQLException {
        long paymentId = seedPaymentAsAdmin();

        VerificationRecord saved = repository.save(VerificationRecord.create(
                paymentId, null, PaymentState.FINALIZED, "ETHEREUM:0xdef:chain.tx.seen", null, null,
                Instant.now()));

        VerificationRecord retrieved = repository.findById(saved.getId()).orElseThrow();

        assertThat(retrieved.getFromState()).isNull();
        assertThat(retrieved.getDetail()).isNull();
        assertThat(retrieved.getToState()).isEqualTo(PaymentState.FINALIZED);
    }

    /** {@code verification_records.payment_id} has a real FK to {@code payments.payments.id}
     * (and that, in turn, to {@code payments.invoices.id}) - mirrors
     * {@code SchemaBaselineMigrationIntegrationTest}'s own identical {@code seedPaymentAsAdmin}
     * helper exactly, including its own "tx_hash must be unique per call" rationale (this class's
     * own two test methods share one Testcontainers instance). */
    private static long seedPaymentAsAdmin() throws SQLException {
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            long invoiceId;
            try (ResultSet inserted = statement.executeQuery("""
                    INSERT INTO payments.invoices
                        (invoice_uuid, merchant_account_uuid, chain, token_contract_address, token_decimals,
                         amount, pay_to_address, expires_at)
                    VALUES
                        (gen_random_uuid(), gen_random_uuid(), 'ETHEREUM', repeat('1', 40), 6,
                         1000, repeat('2', 40), now() + interval '1 hour')
                    RETURNING id
                    """)) {
                inserted.next();
                invoiceId = inserted.getLong(1);
            }
            try (ResultSet inserted = statement.executeQuery("""
                    INSERT INTO payments.payments
                        (payment_uuid, invoice_id, chain, tx_hash, to_address, token_contract_address, amount)
                    VALUES
                        (gen_random_uuid(), %d, 'ETHEREUM', '0xit-' || gen_random_uuid(), repeat('2', 40), repeat('1', 40), 1000)
                    RETURNING id
                    """.formatted(invoiceId))) {
                inserted.next();
                return inserted.getLong(1);
            }
        }
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = VerificationRecord.class)
    @EnableJpaRepositories(basePackageClasses = VerificationRecordRepository.class)
    static class TestConfig {
    }
}
