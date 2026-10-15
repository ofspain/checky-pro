package com.themistra.payment.receipt;

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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T19 (R23/R24), Phase 11 Kimi Gap 10: a real Testcontainers proof that {@code Receipt} saves
 * and reloads via JPA as {@code payment_app} - a bug in the entity mapping (wrong column name,
 * a missing {@code @JdbcTypeCode(SqlTypes.CHAR)} on {@code digestSha256}, etc.) would not be
 * caught by any mocked-repository test. {@code ReceiptRepository} has no custom {@code
 * @Modifying} method, so {@code save}/{@code findById} already carry their own transactional
 * wrapper (mirrors {@code VerificationRecordRepositoryIntegrationTest}'s own identical
 * precedent, T10 - no {@code TransactionTemplate} needed here, unlike {@code
 * PaymentRepositoryIntegrationTest}'s own {@code markAttestedIfFinalized} test).
 */
@Testcontainers
@SpringBootTest(classes = ReceiptRepositoryIntegrationTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ReceiptRepositoryIntegrationTest {

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
    private ReceiptRepository repository;

    @AfterEach
    void cleanUp() throws SQLException {
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            statement.execute("DELETE FROM payments.receipts");
            statement.execute("DELETE FROM payments.payments");
            statement.execute("DELETE FROM payments.invoices");
        }
    }

    private static long seedPayment() throws SQLException {
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
                        (payment_uuid, invoice_id, chain, tx_hash, to_address, token_contract_address, amount)
                    VALUES (gen_random_uuid(), %d, 'ETHEREUM', '0xit-' || gen_random_uuid(), repeat('2', 40), repeat('1', 40), 1000)
                    RETURNING id
                    """).formatted(invoiceId))) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    @Test
    void shouldSaveAndReloadReceiptWithAllFieldsIntact() throws SQLException {
        long paymentId = seedPayment();
        UUID receiptUuid = UUID.randomUUID();
        String digest = "a".repeat(64);
        Instant signedAt = Instant.parse("2026-10-15T00:00:01Z");
        Instant createdAt = Instant.parse("2026-10-15T00:00:02Z");

        Receipt saved = repository.save(Receipt.create(receiptUuid, paymentId, digest, "deadbeef", "kms-key-1",
                "my-bucket", "receipts/prefix", signedAt, createdAt));

        Receipt reloaded = repository.findById(saved.getId()).orElseThrow();
        assertThat(reloaded.getReceiptUuid()).isEqualTo(receiptUuid);
        assertThat(reloaded.getPaymentId()).isEqualTo(paymentId);
        assertThat(reloaded.getDigestSha256()).isEqualTo(digest);
        assertThat(reloaded.getSignature()).isEqualTo("deadbeef");
        assertThat(reloaded.getKmsKeyId()).isEqualTo("kms-key-1");
        assertThat(reloaded.getS3Bucket()).isEqualTo("my-bucket");
        assertThat(reloaded.getS3Key()).isEqualTo("receipts/prefix");
        assertThat(reloaded.getSignedAt()).isEqualTo(signedAt);
        assertThat(reloaded.getCreatedAt()).isEqualTo(createdAt);
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = Receipt.class)
    @EnableJpaRepositories(basePackageClasses = ReceiptRepository.class)
    static class TestConfig {
    }
}
