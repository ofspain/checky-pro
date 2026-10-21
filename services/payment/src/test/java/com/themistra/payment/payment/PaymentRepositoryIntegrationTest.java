package com.themistra.payment.payment;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T19 (R24), Phase 11 Kimi Gap 4: a real Testcontainers proof that {@code markAttestedIfFinalized}'s
 * own JPQL actually updates {@code state} to {@code ATTESTED} and sets {@code attested_at} - the
 * mocked-repository tests in {@code PaymentServiceTest} only prove the service calls the
 * repository method with the right arguments, never that the query text itself does the right
 * thing against a real schema. Mirrors {@code InvoiceRepositoryIntegrationTest}'s own
 * established shape (T08) - connects as {@code payment_app} (not the migration-owning role),
 * since {@code payments} already has {@code INSERT, UPDATE} (V7).
 *
 * <p>The identical gap exists for {@code markHeldIfFinalized} (T17) - never exercised against a
 * real database either. Disclosed here, not fixed - out of this task's own scope to retrofit.</p>
 *
 * <p>A {@code @Modifying} repository method called directly (not from a {@code @Transactional}
 * service method) throws {@code TransactionRequiredException} - the identical, already-named
 * lesson {@code InvoiceRepositoryIntegrationTest} (T08) first ran into. Wrapped in a
 * {@code TransactionTemplate} scoped to just the repository call, same fix.</p>
 */
@Testcontainers
@SpringBootTest(classes = PaymentRepositoryIntegrationTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class PaymentRepositoryIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
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
    private PaymentRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private int markAttestedIfFinalized(long paymentId, PaymentState expected, PaymentState target, Instant now) {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        return transactionTemplate.execute(status -> repository.markAttestedIfFinalized(paymentId, expected, target, now));
    }

    @AfterEach
    void cleanUp() throws SQLException {
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            statement.execute("DELETE FROM payments.payments");
            statement.execute("DELETE FROM payments.invoices");
        }
    }

    /** Frontend Phase 1b (2026-10-21): an invoice's payments, oldest first, and only that invoice's. */
    @Test
    void findByInvoiceIdListsOnlyThatInvoicesPaymentsOldestFirst() throws SQLException {
        long invoiceId = seedInvoice();
        long otherInvoiceId = seedInvoice();
        seedPayment(invoiceId, "0xlater", "2026-10-21T10:00:02Z");
        seedPayment(invoiceId, "0xfirst", "2026-10-21T10:00:00Z");
        seedPayment(otherInvoiceId, "0xother", "2026-10-21T09:00:00Z");
        seedPayment(invoiceId, "0xmiddle", "2026-10-21T10:00:01Z");

        assertThat(repository.findByInvoiceIdOrderByCreatedAtAscIdAsc(invoiceId))
                .extracting(Payment::getTxHash).containsExactly("0xfirst", "0xmiddle", "0xlater");
        assertThat(repository.findByInvoiceIdOrderByCreatedAtAscIdAsc(-1L)).isEmpty();
    }

    private static long seedInvoice() throws SQLException {
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement();
             ResultSet rs = statement.executeQuery("""
                     INSERT INTO payments.invoices
                         (invoice_uuid, merchant_account_uuid, chain, token_contract_address, token_decimals,
                          amount, pay_to_address, expires_at)
                     VALUES (gen_random_uuid(), gen_random_uuid(), 'ETHEREUM', repeat('1', 40), 6,
                             1000, repeat('2', 40), now() + interval '1 hour')
                     RETURNING id
                     """)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static void seedPayment(long invoiceId, String txHash, String createdAt) throws SQLException {
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            statement.execute(("""
                    INSERT INTO payments.payments
                        (payment_uuid, invoice_id, chain, tx_hash, to_address, token_contract_address, amount, state,
                         created_at)
                    VALUES (gen_random_uuid(), %d, 'ETHEREUM', '%s', repeat('2', 40), repeat('1', 40), 1000, 'SEEN',
                            '%s')
                    """).formatted(invoiceId, txHash, createdAt));
        }
    }

    private static long seedFinalizedPayment() throws SQLException {
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            long invoiceId;
            try (ResultSet rs = statement.executeQuery("""
                    INSERT INTO payments.invoices
                        (invoice_uuid, merchant_account_uuid, chain, token_contract_address, token_decimals,
                         amount, pay_to_address, expires_at)
                    VALUES (gen_random_uuid(), gen_random_uuid(), 'ETHEREUM', repeat('1', 40), 6,
                            1000, repeat('2', 40), now() + interval '1 hour')
                    RETURNING id
                    """)) {
                rs.next();
                invoiceId = rs.getLong(1);
            }
            try (ResultSet rs = statement.executeQuery(("""
                    INSERT INTO payments.payments
                        (payment_uuid, invoice_id, chain, tx_hash, to_address, token_contract_address, amount, state)
                    VALUES (gen_random_uuid(), %d, 'ETHEREUM', '0xit-' || gen_random_uuid(), repeat('2', 40),
                            repeat('1', 40), 1000, 'FINALIZED')
                    RETURNING id
                    """).formatted(invoiceId))) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    @Test
    void markAttestedIfFinalizedShouldUpdateStateAndSetAttestedAt() throws SQLException {
        long paymentId = seedFinalizedPayment();
        Instant now = Instant.parse("2026-10-15T00:00:00Z");

        int updated = markAttestedIfFinalized(paymentId, PaymentState.FINALIZED, PaymentState.ATTESTED, now);

        assertThat(updated).isEqualTo(1);
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT state, attested_at FROM payments.payments WHERE id = " + paymentId)) {
            rs.next();
            assertThat(rs.getString("state")).isEqualTo("ATTESTED");
            assertThat(rs.getTimestamp("attested_at")).isNotNull();
            assertThat(rs.getTimestamp("attested_at").toInstant()).isEqualTo(now);
        }
    }

    @Test
    void markAttestedIfFinalizedShouldReturnZeroWhenNotFinalized() throws SQLException {
        long paymentId = seedFinalizedPayment();
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            statement.execute("UPDATE payments.payments SET state = 'ATTESTED' WHERE id = " + paymentId);
        }

        int updated = markAttestedIfFinalized(paymentId, PaymentState.FINALIZED, PaymentState.ATTESTED, Instant.now());

        assertThat(updated).isEqualTo(0);
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = Payment.class)
    @EnableJpaRepositories(basePackageClasses = PaymentRepository.class)
    static class TestConfig {
    }
}
