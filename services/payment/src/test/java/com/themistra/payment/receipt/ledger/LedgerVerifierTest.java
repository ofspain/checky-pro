package com.themistra.payment.receipt.ledger;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T20 (R22). A real Testcontainers proof - the hash-chain verification property cannot be
 * meaningfully proven against a mocked repository, and the task's own text explicitly requires
 * testing against "a deliberately corrupted row." Mirrors {@code LedgerServiceTest}'s own
 * already-established harness shape (T19) almost exactly - the only real way to simulate a
 * corrupted row is a raw admin connection, bypassing {@code payment_app}'s own grants and {@code
 * LedgerEntry}'s own {@code @Immutable} entirely (the same "insider with database-owner access"
 * threat `SECURITY-THREAT-MODEL.md` names, simulated the only way it's actually reachable).
 */
@Testcontainers
@SpringBootTest(classes = LedgerVerifierTest.TestConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class LedgerVerifierTest {

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
            statement.execute("""
                    INSERT INTO payments.invoices
                        (invoice_uuid, merchant_account_uuid, chain, token_contract_address, token_decimals,
                         amount, pay_to_address, expires_at)
                    VALUES (gen_random_uuid(), gen_random_uuid(), 'ETHEREUM', repeat('1', 40), 6,
                            1000, repeat('2', 40), now() + interval '1 hour')
                    """);
        }
    }

    @Autowired
    private LedgerVerifier verifier;

    @Autowired
    private LedgerService ledgerService;

    @AfterEach
    void cleanUp() throws SQLException {
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            statement.execute("DELETE FROM payments.ledger_entries");
            statement.execute("DELETE FROM payments.receipts");
            statement.execute("DELETE FROM payments.payments");
        }
    }

    private static long seedReceipt() throws SQLException {
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            long paymentId;
            try (ResultSet rs = statement.executeQuery("""
                    INSERT INTO payments.payments (payment_uuid, invoice_id, chain, tx_hash, to_address, token_contract_address, amount)
                    VALUES (gen_random_uuid(), 1, 'ETHEREUM', '0xtest-' || gen_random_uuid(), repeat('2', 40), repeat('1', 40), 1000)
                    RETURNING id
                    """)) {
                rs.next();
                paymentId = rs.getLong(1);
            }
            try (ResultSet rs = statement.executeQuery(("""
                    INSERT INTO payments.receipts (receipt_uuid, payment_id, digest_sha256, signature, kms_key_id, s3_bucket, s3_key, signed_at)
                    VALUES (gen_random_uuid(), %d, repeat('a', 64), 'sig', 'key', 'bucket', 'prefix', now())
                    RETURNING id
                    """).formatted(paymentId))) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    @Test
    void shouldReportIntactForAnEmptyLedger() {
        assertThat(verifier.verify()).isInstanceOf(LedgerVerificationResult.Intact.class);
    }

    @Test
    void shouldReportIntactForAGenesisOnlyLedger() throws SQLException {
        ledgerService.append(seedReceipt(), Map.of("k", "v1"));

        assertThat(verifier.verify()).isInstanceOf(LedgerVerificationResult.Intact.class);
    }

    @Test
    void shouldReportIntactForAnUncorruptedMultiEntryChain() throws SQLException {
        ledgerService.append(seedReceipt(), Map.of("k", "v1"));
        ledgerService.append(seedReceipt(), Map.of("k", "v2"));
        ledgerService.append(seedReceipt(), Map.of("k", "v3"));

        assertThat(verifier.verify()).isInstanceOf(LedgerVerificationResult.Intact.class);
    }

    /** Named test, `package.md` §8. The task's own text: "test with a deliberately corrupted
     * row." */
    @Test
    void shouldDetectBrokenHashChainOnVerification() throws SQLException {
        ledgerService.append(seedReceipt(), Map.of("k", "v1"));
        LedgerEntry middle = ledgerService.append(seedReceipt(), Map.of("k", "v2"));
        ledgerService.append(seedReceipt(), Map.of("k", "v3"));

        corruptColumn("payload", "'{\"k\":\"tampered\"}'", middle.getSeq());

        LedgerVerificationResult result = verifier.verify();
        assertThat(result).isInstanceOf(LedgerVerificationResult.Broken.class);
        LedgerVerificationResult.Broken broken = (LedgerVerificationResult.Broken) result;
        assertThat(broken.seq()).isEqualTo(middle.getSeq());
        assertThat(broken.reason()).isEqualTo(LedgerVerifier.REASON_ENTRY_HASH_MISMATCH);
    }

    /** Distinct from the named test above (Phase 1's own boundary test): the entry's own hash is
     * still internally self-consistent with its own (corrupted) {@code prevHash}, so AC1 passes
     * and AC2 is the one that actually fires. */
    @Test
    void shouldDetectPrevHashCorruptionDistinctFromEntryHashCorruption() throws SQLException {
        ledgerService.append(seedReceipt(), Map.of("k", "v1"));
        LedgerEntry second = ledgerService.append(seedReceipt(), Map.of("k", "v2"));

        // Recompute entryHash to stay self-consistent with a corrupted prevHash, so only AC2 fires.
        String corruptedPrevHash = "1".repeat(64);
        corruptColumn("prev_hash", "'" + corruptedPrevHash + "'", second.getSeq());
        // entry_hash must still match sha256(payload + corruptedPrevHash) for AC1 to pass.
        String recomputed = sha256ForTest(com.themistra.payment.common.CanonicalJson.serialize(Map.of("k", "v2")) + corruptedPrevHash);
        corruptColumn("entry_hash", "'" + recomputed + "'", second.getSeq());

        LedgerVerificationResult result = verifier.verify();
        assertThat(result).isInstanceOf(LedgerVerificationResult.Broken.class);
        LedgerVerificationResult.Broken broken = (LedgerVerificationResult.Broken) result;
        assertThat(broken.seq()).isEqualTo(second.getSeq());
        assertThat(broken.reason()).isEqualTo(LedgerVerifier.REASON_PREV_HASH_MISMATCH);
    }

    @Test
    void shouldDetectCorruptedGenesisPrevHash() throws SQLException {
        LedgerEntry genesis = ledgerService.append(seedReceipt(), Map.of("k", "v1"));

        String corruptedPrevHash = "2".repeat(64);
        String recomputed = sha256ForTest(com.themistra.payment.common.CanonicalJson.serialize(Map.of("k", "v1")) + corruptedPrevHash);
        corruptColumn("prev_hash", "'" + corruptedPrevHash + "'", genesis.getSeq());
        corruptColumn("entry_hash", "'" + recomputed + "'", genesis.getSeq());

        LedgerVerificationResult result = verifier.verify();
        assertThat(result).isInstanceOf(LedgerVerificationResult.Broken.class);
        LedgerVerificationResult.Broken broken = (LedgerVerificationResult.Broken) result;
        assertThat(broken.seq()).isEqualTo(genesis.getSeq());
        assertThat(broken.reason()).isEqualTo(LedgerVerifier.REASON_GENESIS_PREV_HASH_INVALID);
    }

    /** Phase 4 Finding 7's own corrected mechanism - advances the identity sequence directly
     * between two real {@code append(...)} calls, never deleting a committed row. */
    @Test
    void shouldNotFalselyReportBrokenAcrossASeqGap() throws SQLException {
        LedgerEntry first = ledgerService.append(seedReceipt(), Map.of("k", "v1"));

        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            statement.execute("SELECT nextval(pg_get_serial_sequence('payments.ledger_entries', 'seq'))");
        }

        LedgerEntry second = ledgerService.append(seedReceipt(), Map.of("k", "v2"));
        assertThat(second.getSeq()).isGreaterThan(first.getSeq() + 1);

        assertThat(verifier.verify()).isInstanceOf(LedgerVerificationResult.Intact.class);
    }

    /** Phase 8 Kimi Finding 4: an entry failing both AC1 and AC2 must report the AC1 reason -
     * the within-entry check order is AC1 before AC2/AC3. */
    @Test
    void shouldReportEntryHashMismatchBeforePrevHashMismatchWhenBothFail() throws SQLException {
        ledgerService.append(seedReceipt(), Map.of("k", "v1"));
        LedgerEntry second = ledgerService.append(seedReceipt(), Map.of("k", "v2"));

        // Corrupt BOTH prevHash and entryHash in a way that leaves them NOT self-consistent with
        // each other either - AC1 fails regardless of AC2's own outcome.
        corruptColumn("prev_hash", "'" + "3".repeat(64) + "'", second.getSeq());
        corruptColumn("entry_hash", "'" + "4".repeat(64) + "'", second.getSeq());

        LedgerVerificationResult result = verifier.verify();
        assertThat(result).isInstanceOf(LedgerVerificationResult.Broken.class);
        LedgerVerificationResult.Broken broken = (LedgerVerificationResult.Broken) result;
        assertThat(broken.reason()).isEqualTo(LedgerVerifier.REASON_ENTRY_HASH_MISMATCH);
    }

    /** Phase 8/9 Kimi Finding 1: a malformed row (the exact insider-tampering scenario this
     * class exists to detect) is reported as {@code Broken}, never left to throw. */
    @Test
    void shouldReportBrokenNotThrowWhenPayloadIsNull() throws SQLException {
        LedgerEntry entry = ledgerService.append(seedReceipt(), Map.of("k", "v1"));

        // Phase 11 Gap 1 (Kimi): the container is class-scoped - dropping NOT NULL here would
        // otherwise leak into every later test in this class. Restored in a finally block, not
        // left to the next test's own good luck.
        withNullableColumn("payload", () -> {
            corruptColumn("payload", "NULL", entry.getSeq());

            LedgerVerificationResult result = verifier.verify();
            assertThat(result).isInstanceOf(LedgerVerificationResult.Broken.class);
            LedgerVerificationResult.Broken broken = (LedgerVerificationResult.Broken) result;
            assertThat(broken.seq()).isEqualTo(entry.getSeq());
            assertThat(broken.reason()).contains("entry could not be verified");
        });
    }

    /** Phase 11 Gap 2 (Kimi): the symmetric case - a NULL prevHash must also be reported as
     * Broken, not thrown. Confirmed empirically (not assumed) that this does NOT actually
     * exercise the catch-all handler the way a null payload does: Java's own {@code +} string
     * concatenation operator tolerates a null operand (producing the literal text {@code
     * "null"}), so the AC1 hash-recomputation step - which runs before AC2 would ever reach the
     * {@code .equals(...)} call that WOULD throw on a null prevHash - already produces a
     * mismatched hash and reports {@code REASON_ENTRY_HASH_MISMATCH} first. The catch-all
     * handler's own real purpose is narrower than this scenario: it exists for failures AC1's
     * own string concatenation can't silently tolerate, like a null payload ({@code
     * CanonicalJson.serialize(null)} throws directly) or an unsupported value type (the next
     * test) - not a null prevHash, which the ordinary AC1 path already handles safely on its
     * own. */
    @Test
    void shouldReportEntryHashMismatchNotThrowWhenPrevHashIsNull() throws SQLException {
        LedgerEntry entry = ledgerService.append(seedReceipt(), Map.of("k", "v1"));

        withNullableColumn("prev_hash", () -> {
            corruptColumn("prev_hash", "NULL", entry.getSeq());

            LedgerVerificationResult result = verifier.verify();
            assertThat(result).isInstanceOf(LedgerVerificationResult.Broken.class);
            LedgerVerificationResult.Broken broken = (LedgerVerificationResult.Broken) result;
            assertThat(broken.seq()).isEqualTo(entry.getSeq());
            assertThat(broken.reason()).isEqualTo(LedgerVerifier.REASON_ENTRY_HASH_MISMATCH);
        });
    }

    /** Phase 11 Gap 3 (Kimi): distinct from a NULL value - an unsupported JSON value type
     * (a bare decimal, which {@code CanonicalJson} rejects) proves the catch-all handler is
     * truly general, not just a null check. */
    @Test
    void shouldReportBrokenNotThrowWhenPayloadContainsAnUnsupportedValueType() throws SQLException {
        LedgerEntry entry = ledgerService.append(seedReceipt(), Map.of("k", "v1"));

        corruptColumn("payload", "'{\"amount\": 1.5}'", entry.getSeq());

        LedgerVerificationResult result = verifier.verify();
        assertThat(result).isInstanceOf(LedgerVerificationResult.Broken.class);
        LedgerVerificationResult.Broken broken = (LedgerVerificationResult.Broken) result;
        assertThat(broken.seq()).isEqualTo(entry.getSeq());
        assertThat(broken.reason()).contains("entry could not be verified");
    }

    /** Phase 11 Gap 4 (Kimi): the positional prior-entry lookup must detect a TRUE insider
     * deletion, not just a harmless rolled-back-transaction seq gap - the complement to {@link
     * #shouldNotFalselyReportBrokenAcrossASeqGap()}. */
    @Test
    void shouldDetectBrokenChainWhenAMiddleEntryIsDeleted() throws SQLException {
        LedgerEntry first = ledgerService.append(seedReceipt(), Map.of("k", "v1"));
        LedgerEntry second = ledgerService.append(seedReceipt(), Map.of("k", "v2"));
        LedgerEntry third = ledgerService.append(seedReceipt(), Map.of("k", "v3"));

        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            statement.execute("DELETE FROM payments.ledger_entries WHERE seq = " + second.getSeq());
        }

        LedgerVerificationResult result = verifier.verify();
        assertThat(result).isInstanceOf(LedgerVerificationResult.Broken.class);
        LedgerVerificationResult.Broken broken = (LedgerVerificationResult.Broken) result;
        assertThat(broken.seq()).isEqualTo(third.getSeq());
        assertThat(broken.reason()).isEqualTo(LedgerVerifier.REASON_PREV_HASH_MISMATCH);
    }

    /** Phase 11 Gap 5 (Kimi): the named test corrupts a middle entry - this proves the head
     * entry itself is also checked, ruling out an off-by-one in the loop. */
    @Test
    void shouldDetectCorruptionOfTheHeadEntry() throws SQLException {
        ledgerService.append(seedReceipt(), Map.of("k", "v1"));
        ledgerService.append(seedReceipt(), Map.of("k", "v2"));
        LedgerEntry head = ledgerService.append(seedReceipt(), Map.of("k", "v3"));

        corruptColumn("payload", "'{\"k\":\"tampered\"}'", head.getSeq());

        LedgerVerificationResult result = verifier.verify();
        assertThat(result).isInstanceOf(LedgerVerificationResult.Broken.class);
        LedgerVerificationResult.Broken broken = (LedgerVerificationResult.Broken) result;
        assertThat(broken.seq()).isEqualTo(head.getSeq());
        assertThat(broken.reason()).isEqualTo(LedgerVerifier.REASON_ENTRY_HASH_MISMATCH);
    }

    /** Phase 11 Gap 6 (Kimi): a direct corruption of only the stored {@code entry_hash} column
     * (payload and prevHash both left untouched) isolates AC1 from the payload-corruption shape
     * the named test already covers. */
    @Test
    void shouldDetectDirectEntryHashCorruptionWithUntouchedPayload() throws SQLException {
        ledgerService.append(seedReceipt(), Map.of("k", "v1"));
        LedgerEntry second = ledgerService.append(seedReceipt(), Map.of("k", "v2"));

        corruptColumn("entry_hash", "'" + "0".repeat(64) + "'", second.getSeq());

        LedgerVerificationResult result = verifier.verify();
        assertThat(result).isInstanceOf(LedgerVerificationResult.Broken.class);
        LedgerVerificationResult.Broken broken = (LedgerVerificationResult.Broken) result;
        assertThat(broken.seq()).isEqualTo(second.getSeq());
        assertThat(broken.reason()).isEqualTo(LedgerVerifier.REASON_ENTRY_HASH_MISMATCH);
    }

    /** Phase 11 Gap 7 (Kimi): {@code verify()} is explicitly read-only (this task's own Phase 2
     * Constraints) - proves it, rather than only asserting it in a Javadoc comment. */
    @Test
    void verifyShouldNotMutateTheLedger() throws SQLException {
        ledgerService.append(seedReceipt(), Map.of("k", "v1"));
        ledgerService.append(seedReceipt(), Map.of("k", "v2"));
        long countBefore = countLedgerEntries();

        verifier.verify();

        assertThat(countLedgerEntries()).isEqualTo(countBefore);
    }

    private static long countLedgerEntries() throws SQLException {
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement();
             ResultSet rs = statement.executeQuery("SELECT count(*) FROM payments.ledger_entries")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    /** Phase 11 Gap 1 (Kimi): drops the column's own {@code NOT NULL} constraint for the
     * duration of {@code action}, then restores it unconditionally - the Testcontainers
     * instance is class-scoped (shared across every test method), so a dropped constraint left
     * in place would silently change every later test's own schema assumptions. */
    @FunctionalInterface
    private interface SqlAction {
        void run() throws SQLException;
    }

    private static void withNullableColumn(String column, SqlAction action) throws SQLException {
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            statement.execute("ALTER TABLE payments.ledger_entries ALTER COLUMN " + column + " DROP NOT NULL");
        }
        try {
            action.run();
        } finally {
            // The corrupted row must be gone before SET NOT NULL can succeed - Postgres refuses
            // to re-add a NOT NULL constraint while any existing row violates it.
            try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
                statement.execute("DELETE FROM payments.ledger_entries");
                statement.execute("ALTER TABLE payments.ledger_entries ALTER COLUMN " + column + " SET NOT NULL");
            }
        }
    }

    private static void corruptColumn(String column, String sqlLiteralValue, long seq) throws SQLException {
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            statement.execute("UPDATE payments.ledger_entries SET " + column + " = " + sqlLiteralValue + " WHERE seq = " + seq);
        }
    }

    private static String sha256ForTest(String input) {
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = LedgerEntry.class)
    @EnableJpaRepositories(basePackageClasses = LedgerEntryRepository.class)
    @Import({LedgerService.class, LedgerVerifier.class})
    static class TestConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(Instant.parse("2026-10-15T00:00:00Z"), ZoneOffset.UTC);
        }
    }
}
