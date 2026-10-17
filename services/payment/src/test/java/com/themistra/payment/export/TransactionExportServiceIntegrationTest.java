package com.themistra.payment.export;

import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T22 (R27). A real Testcontainers proof of the three-table join - the query cannot be
 * meaningfully proven against a mock. Mirrors {@code PaymentRepositoryIntegrationTest}'s own
 * established shape; connects via plain auto-configured datasource credentials (the Postgres
 * container's own superuser), since this is a read-only query with no grant concerns of its own
 * (unlike a {@code payment_app}-role write path).
 */
@Testcontainers
@org.springframework.boot.test.context.SpringBootTest(
        classes = TransactionExportServiceIntegrationTest.TestConfig.class,
        webEnvironment = org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE)
class TransactionExportServiceIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @BeforeAll
    static void migrate() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("payments")
                .load()
                .migrate();
    }

    @Autowired
    private TransactionExportService exportService;

    private static final UUID MERCHANT_A = UUID.randomUUID();
    private static final UUID MERCHANT_B = UUID.randomUUID();

    @AfterEach
    void cleanUp() throws SQLException {
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            statement.execute("DELETE FROM payments.receipts");
            statement.execute("DELETE FROM payments.payments");
            statement.execute("DELETE FROM payments.invoices");
        }
    }

    /** Named test (R27). Seeds one full, real ATTESTED payment (invoice + payment + receipt) for
     * merchant A and asserts every one of the eleven frozen columns, including the receiptUri
     * formula (bucket/key + ".json"). */
    @Test
    void shouldExportTaxReadyHistoryForMerchantAndDateRange() throws SQLException {
        Instant finalizedAt = Instant.parse("2026-06-15T12:00:00Z");
        Instant attestedAt = Instant.parse("2026-06-15T12:05:00Z");
        Seeded row = seedAttestedPayment(MERCHANT_A, "ETHEREUM", "0xtoken", (short) 6,
                new BigDecimal("1000"), "0xinvoice-expected", "0xobserved-destination",
                new BigDecimal("1000"), finalizedAt, attestedAt, "my-bucket", "my-prefix");

        List<TransactionExportRow> rows = exportService.export(MERCHANT_A,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-12-31T23:59:59Z"));

        assertThat(rows).hasSize(1);
        TransactionExportRow result = rows.get(0);
        assertThat(result.invoiceUuid()).isEqualTo(row.invoiceUuid);
        assertThat(result.paymentUuid()).isEqualTo(row.paymentUuid);
        assertThat(result.receiptUuid()).isEqualTo(row.receiptUuid);
        assertThat(result.chain()).isEqualTo("ETHEREUM");
        assertThat(result.txHash()).isEqualTo(row.txHash);
        assertThat(result.tokenContractAddress()).isEqualTo("0xtoken");
        assertThat(result.tokenDecimals()).isEqualTo((short) 6);
        assertThat(result.amount()).isEqualTo("1000");
        assertThat(result.payToAddress()).isEqualTo("0xobserved-destination");
        assertThat(result.finalizedAt()).isEqualTo(finalizedAt);
        assertThat(result.attestedAt()).isEqualTo(attestedAt);
        assertThat(result.receiptUri()).isEqualTo("s3://my-bucket/my-prefix.json");
    }

    @Test
    void shouldExcludeOtherMerchantsPayments() throws SQLException {
        seedAttestedPayment(MERCHANT_B, "ETHEREUM", "0xtoken", (short) 6, new BigDecimal("1000"),
                "0xinvoice", "0xobserved", new BigDecimal("1000"), Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-01T01:00:00Z"), "bucket", "prefix");

        List<TransactionExportRow> rows = exportService.export(MERCHANT_A,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-12-31T23:59:59Z"));

        assertThat(rows).isEmpty();
    }

    @Test
    void shouldExcludePaymentsOutsideDateRange() throws SQLException {
        seedAttestedPayment(MERCHANT_A, "ETHEREUM", "0xtoken", (short) 6, new BigDecimal("1000"),
                "0xinvoice", "0xobserved", new BigDecimal("1000"), Instant.parse("2025-01-01T00:00:00Z"),
                Instant.parse("2025-01-01T01:00:00Z"), "bucket", "prefix");

        List<TransactionExportRow> rows = exportService.export(MERCHANT_A,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-12-31T23:59:59Z"));

        assertThat(rows).isEmpty();
    }

    /** AC2's inclusive-range proof: a payment finalized exactly on the `from` boundary and one
     * exactly on the `to` boundary are both included. */
    @Test
    void shouldIncludePaymentsOnDateRangeBoundaries() throws SQLException {
        Instant from = Instant.parse("2026-06-01T00:00:00Z");
        Instant to = Instant.parse("2026-06-30T00:00:00Z");
        seedAttestedPayment(MERCHANT_A, "ETHEREUM", "0xtoken", (short) 6, new BigDecimal("1000"),
                "0xinvoice", "0xobserved1", new BigDecimal("1000"), from, from.plusSeconds(60), "bucket", "p1");
        seedAttestedPayment(MERCHANT_A, "ETHEREUM", "0xtoken", (short) 6, new BigDecimal("1000"),
                "0xinvoice", "0xobserved2", new BigDecimal("1000"), to, to.plusSeconds(60), "bucket", "p2");

        List<TransactionExportRow> rows = exportService.export(MERCHANT_A, from, to);

        assertThat(rows).hasSize(2);
    }

    /** Phase 11 Kimi Gap 3: the existing boundary test uses two distinct instants (one on
     * `from`, one on `to`) - this proves the single-instant case where `from == to ==
     * finalizedAt` is itself included, not merely "a row equal to one of two different
     * bounds". */
    @Test
    void shouldIncludePaymentWhenFromEqualsTo() throws SQLException {
        Instant exact = Instant.parse("2026-06-15T12:00:00Z");
        seedAttestedPayment(MERCHANT_A, "ETHEREUM", "0xtoken", (short) 6, new BigDecimal("1000"),
                "0xinvoice", "0xobserved", new BigDecimal("1000"), exact, exact.plusSeconds(60), "bucket", "prefix");

        List<TransactionExportRow> rows = exportService.export(MERCHANT_A, exact, exact);

        assertThat(rows).hasSize(1);
    }

    @Test
    void shouldExcludeFinalizedNotYetAttestedPayments() throws SQLException {
        seedPaymentWithState(MERCHANT_A, "FINALIZED", Instant.parse("2026-06-01T00:00:00Z"), null);

        List<TransactionExportRow> rows = exportService.export(MERCHANT_A,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-12-31T23:59:59Z"));

        assertThat(rows).isEmpty();
    }

    @Test
    void shouldReturnEmptyListWhenNoMatchingRows() {
        List<TransactionExportRow> rows = exportService.export(MERCHANT_A,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-12-31T23:59:59Z"));

        assertThat(rows).isEmpty();
    }

    /** Phase 3 Kimi Finding 4 (accepted at Phase 4): deterministic, finalizedAt-ascending
     * order, not merely "some stable order" - seeded in reverse to rule out coincidental
     * insertion-order match. */
    @Test
    void shouldReturnRowsInAscendingFinalizedAtOrder() throws SQLException {
        Instant later = Instant.parse("2026-06-15T00:00:00Z");
        Instant earlier = Instant.parse("2026-06-01T00:00:00Z");
        Seeded laterRow = seedAttestedPayment(MERCHANT_A, "ETHEREUM", "0xtoken", (short) 6,
                new BigDecimal("1000"), "0xinvoice", "0xlater", new BigDecimal("1000"), later,
                later.plusSeconds(60), "bucket", "later");
        Seeded earlierRow = seedAttestedPayment(MERCHANT_A, "ETHEREUM", "0xtoken", (short) 6,
                new BigDecimal("1000"), "0xinvoice", "0xearlier", new BigDecimal("1000"), earlier,
                earlier.plusSeconds(60), "bucket", "earlier");

        List<TransactionExportRow> rows = exportService.export(MERCHANT_A,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-12-31T23:59:59Z"));

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).paymentUuid()).isEqualTo(earlierRow.paymentUuid);
        assertThat(rows.get(1).paymentUuid()).isEqualTo(laterRow.paymentUuid);
    }

    /** Phase 11 Kimi Gap 4: the test above seeds different {@code finalizedAt} values, so it
     * would still pass even if the {@code paymentUuid} tie-breaker (AC10) were wrong or absent.
     * Both rows share the exact same {@code finalizedAt}; the expected order is derived from the
     * two real, randomly-generated {@code paymentUuid}s' own canonical string order - the same
     * byte-wise comparison Postgres's {@code uuid ASC} performs - not a hand-picked pair, so the
     * test is not coincidentally correct regardless of which UUID sorts first. */
    @Test
    void shouldTieBreakByPaymentUuidWhenFinalizedAtIsEqual() throws SQLException {
        Instant sameInstant = Instant.parse("2026-06-10T00:00:00Z");
        Seeded first = seedAttestedPayment(MERCHANT_A, "ETHEREUM", "0xtoken", (short) 6,
                new BigDecimal("1000"), "0xinvoice", "0xfirst", new BigDecimal("1000"), sameInstant,
                sameInstant.plusSeconds(60), "bucket", "first");
        Seeded second = seedAttestedPayment(MERCHANT_A, "ETHEREUM", "0xtoken", (short) 6,
                new BigDecimal("1000"), "0xinvoice", "0xsecond", new BigDecimal("1000"), sameInstant,
                sameInstant.plusSeconds(60), "bucket", "second");
        UUID expectedFirst = first.paymentUuid.toString().compareTo(second.paymentUuid.toString()) <= 0
                ? first.paymentUuid : second.paymentUuid;
        UUID expectedSecond = expectedFirst.equals(first.paymentUuid) ? second.paymentUuid : first.paymentUuid;

        List<TransactionExportRow> rows = exportService.export(MERCHANT_A,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-12-31T23:59:59Z"));

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).paymentUuid()).isEqualTo(expectedFirst);
        assertThat(rows.get(1).paymentUuid()).isEqualTo(expectedSecond);
    }

    /** Phase 3 Kimi Finding 2 (accepted at Phase 4): proves, not just documents, that
     * amount/payToAddress reflect the observed payments row, not the invoice's expected values -
     * seeded deliberately different (an UNDERPAID-shaped discrepancy and a different observed
     * destination than the invoice originally requested). */
    @Test
    void shouldSourceAmountAndPayToAddressFromPaymentsNotInvoices() throws SQLException {
        Seeded seeded = seedAttestedPayment(MERCHANT_A, "ETHEREUM", "0xtoken", (short) 6,
                new BigDecimal("1000"), "0xinvoice-expected-address", "0xobserved-different-address",
                new BigDecimal("999"), Instant.parse("2026-06-01T00:00:00Z"),
                Instant.parse("2026-06-01T01:00:00Z"), "bucket", "prefix");

        List<TransactionExportRow> rows = exportService.export(MERCHANT_A,
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-12-31T23:59:59Z"));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).amount()).isEqualTo("999");
        assertThat(rows.get(0).payToAddress()).isEqualTo("0xobserved-different-address");
    }

    private record Seeded(UUID invoiceUuid, UUID paymentUuid, UUID receiptUuid, String txHash) {
    }

    private static Seeded seedAttestedPayment(UUID merchantAccountUuid, String chain, String tokenContractAddress,
            short tokenDecimals, BigDecimal invoiceAmount, String invoicePayToAddress,
            String observedPayToAddress, BigDecimal observedAmount, Instant finalizedAt, Instant attestedAt,
            String s3Bucket, String s3Key) throws SQLException {
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            UUID invoiceUuid = UUID.randomUUID();
            long invoiceId;
            try (ResultSet rs = statement.executeQuery(("""
                    INSERT INTO payments.invoices
                        (invoice_uuid, merchant_account_uuid, chain, token_contract_address, token_decimals,
                         amount, pay_to_address, expires_at)
                    VALUES ('%s', '%s', '%s', '%s', %d, %s, '%s', now() + interval '1 hour')
                    RETURNING id
                    """).formatted(invoiceUuid, merchantAccountUuid, chain, tokenContractAddress,
                    tokenDecimals, invoiceAmount, invoicePayToAddress))) {
                rs.next();
                invoiceId = rs.getLong(1);
            }
            UUID paymentUuid = UUID.randomUUID();
            String txHash = "0xit-" + UUID.randomUUID();
            long paymentId;
            try (ResultSet rs = statement.executeQuery(("""
                    INSERT INTO payments.payments
                        (payment_uuid, invoice_id, chain, tx_hash, to_address, token_contract_address, amount,
                         state, finalized_at, attested_at)
                    VALUES ('%s', %d, '%s', '%s', '%s', '%s', %s, 'ATTESTED', '%s', '%s')
                    RETURNING id
                    """).formatted(paymentUuid, invoiceId, chain, txHash, observedPayToAddress,
                    tokenContractAddress, observedAmount, finalizedAt, attestedAt))) {
                rs.next();
                paymentId = rs.getLong(1);
            }
            UUID receiptUuid = UUID.randomUUID();
            try (ResultSet rs = statement.executeQuery(("""
                    INSERT INTO payments.receipts
                        (receipt_uuid, payment_id, digest_sha256, signature, kms_key_id, s3_bucket, s3_key, signed_at)
                    VALUES ('%s', %d, repeat('a', 64), 'sig', 'kid', '%s', '%s', now())
                    RETURNING id
                    """).formatted(receiptUuid, paymentId, s3Bucket, s3Key))) {
                rs.next();
            }
            return new Seeded(invoiceUuid, paymentUuid, receiptUuid, txHash);
        }
    }

    private static void seedPaymentWithState(UUID merchantAccountUuid, String state, Instant finalizedAt,
            Instant attestedAt) throws SQLException {
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            long invoiceId;
            try (ResultSet rs = statement.executeQuery(("""
                    INSERT INTO payments.invoices
                        (invoice_uuid, merchant_account_uuid, chain, token_contract_address, token_decimals,
                         amount, pay_to_address, expires_at)
                    VALUES (gen_random_uuid(), '%s', 'ETHEREUM', repeat('1', 40), 6, 1000, repeat('2', 40),
                            now() + interval '1 hour')
                    RETURNING id
                    """).formatted(merchantAccountUuid))) {
                rs.next();
                invoiceId = rs.getLong(1);
            }
            String finalizedSql = finalizedAt == null ? "NULL" : "'" + finalizedAt + "'";
            String attestedSql = attestedAt == null ? "NULL" : "'" + attestedAt + "'";
            statement.execute(("""
                    INSERT INTO payments.payments
                        (payment_uuid, invoice_id, chain, tx_hash, to_address, token_contract_address, amount,
                         state, finalized_at, attested_at)
                    VALUES (gen_random_uuid(), %d, 'ETHEREUM', '0xit-' || gen_random_uuid(), repeat('2', 40),
                            repeat('1', 40), 1000, '%s', %s, %s)
                    """).formatted(invoiceId, state, finalizedSql, attestedSql));
        }
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = com.themistra.payment.payment.Payment.class)
    static class TestConfig {

        @Bean
        TransactionExportRepository transactionExportRepository(EntityManager entityManager) {
            return new TransactionExportRepository(entityManager);
        }

        @Bean
        TransactionExportService transactionExportService(TransactionExportRepository repository) {
            return new TransactionExportService(repository);
        }
    }
}
