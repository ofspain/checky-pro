package com.themistra.payment;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T02 — real Testcontainers proof that V1/V2 do what the frozen brief's AC1-AC8 claim, not just
 * that the SQL text looks right. Runs both migrations via the Flyway Java API (no
 * application.properties/Spring-context auto-migration path exists yet — that's task 3) against a
 * real Postgres 16 container, then connects as payment_app over real TCP/JDBC — the same auth path
 * that genuinely enforces password authentication, unlike a `docker exec` shell connection, which
 * silently bypasses it via Postgres's own loopback trust rule (confirmed manually in Phase 6).
 */
@Testcontainers
class SchemaBaselineMigrationIntegrationTest {

    private static final List<String> GRANTED_TABLES = List.of("ledger_entries", "verification_records");
    // The other seven baseline tables (invoices, payments, receipts, ledger_anchors,
    // processed_events, outbox, shedlock) get no grant at all in this task — each one's own grant
    // lands in whichever later task first needs it (T05, T09/T11/T13, T17, deferred/T21-skipped,
    // T11, T08 respectively), mirroring crypto-service's own incremental V2-V9 pattern.
    private static final List<String> UNGRANTED_TABLES =
            List.of("invoices", "payments", "receipts", "ledger_anchors", "processed_events", "outbox", "shedlock");

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    private static final String PAYMENT_APP_PASSWORD = "it-payment-app-password";

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

    /** AC1: all nine baseline tables exist, and no others. */
    @Test
    void allNineBaselineTablesExistAndNoOthers() throws SQLException {
        List<String> expected = List.of("invoices", "payments", "verification_records", "receipts",
                "ledger_entries", "ledger_anchors", "processed_events", "outbox", "shedlock");

        try (Connection admin = adminConnection();
             Statement statement = admin.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT table_name FROM information_schema.tables "
                             + "WHERE table_schema = 'payments' AND table_name != 'flyway_schema_history'")) {
            List<String> actual = new ArrayList<>();
            while (resultSet.next()) {
                actual.add(resultSet.getString(1));
            }
            assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
        }
    }

    /** AC1's own "byte-for-byte" claim, automated: the strongest possible regression guard for the
     * verbatim artifact, stronger than any column/constraint introspection could be, since it
     * catches literally any textual deviation from design.md §4c — the same check Phase 6 ran
     * manually via `diff`, now permanent. */
    @Test
    void v1MigrationFileIsByteForByteIdenticalToDesignDocVerbatimBlock() throws IOException {
        Path designDoc = Path.of("../../spec/payment-service/design.md");
        Path v1Migration = Path.of("src/main/resources/db/migration/V1__payments_baseline.sql");

        String verbatimBlock = extractFirstSqlFenceAfter(Files.readString(designDoc), "V1__payments_baseline.sql");
        String migrationContent = Files.readString(v1Migration);

        assertThat(migrationContent).isEqualTo(verbatimBlock);
    }

    /** design.md §4c has more than one ```sql fence (the Flyway migration, then the published
     * event schema as ```json — no ambiguity there — but also the Crypto Service contract block as
     * a plain, unlabelled fence earlier in the file). Anchoring on the heading text immediately
     * before the migration's own fence, rather than "the first ```sql fence found," avoids ever
     * silently extracting the wrong block if the file gains another ```sql fence above this one. */
    private static String extractFirstSqlFenceAfter(String designDocContent, String anchorText) {
        int anchorIndex = designDocContent.indexOf(anchorText);
        if (anchorIndex < 0) {
            throw new AssertionError("anchor text \"" + anchorText + "\" not found in design.md");
        }
        String[] lines = designDocContent.substring(anchorIndex).split("\n", -1);
        StringBuilder block = new StringBuilder();
        boolean inFence = false;
        for (String line : lines) {
            if (!inFence && line.equals("```sql")) {
                inFence = true;
                continue;
            }
            if (inFence && line.equals("```")) {
                break;
            }
            if (inFence) {
                block.append(line).append('\n');
            }
        }
        return block.toString();
    }

    /** AC2: both migrations applied and recorded as successful. */
    @Test
    void bothMigrationsAreRecordedAsSuccessfulInFlywayHistory() throws SQLException {
        try (Connection admin = adminConnection();
             Statement statement = admin.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT version, success FROM payments.flyway_schema_history "
                             + "WHERE version IS NOT NULL ORDER BY installed_rank")) {
            List<String> succeededVersions = new ArrayList<>();
            while (resultSet.next()) {
                assertThat(resultSet.getBoolean("success")).as("version %s must have succeeded", resultSet.getString("version")).isTrue();
                succeededVersions.add(resultSet.getString("version"));
            }
            assertThat(succeededVersions).containsExactly("1", "2");
        }
    }

    /** AC2: payment_app has LOGIN and database CONNECT (confirmed manually in Phase 6; now
     * permanent). */
    @Test
    void paymentAppHasLoginAndDatabaseConnect() throws SQLException {
        try (Connection admin = adminConnection();
             Statement statement = admin.createStatement();
             ResultSet canLogin = statement.executeQuery(
                     "SELECT rolcanlogin FROM pg_roles WHERE rolname = 'payment_app'")) {
            assertThat(canLogin.next()).as("payment_app role must exist").isTrue();
            assertThat(canLogin.getBoolean(1)).as("payment_app must have LOGIN").isTrue();
        }
        try (Connection admin = adminConnection();
             Statement statement = admin.createStatement();
             ResultSet canConnect = statement.executeQuery(
                     "SELECT has_database_privilege('payment_app', current_database(), 'CONNECT')")) {
            canConnect.next();
            assertThat(canConnect.getBoolean(1)).as("payment_app must have CONNECT").isTrue();
        }
    }

    /** V2's `IF NOT EXISTS` role guard must survive a genuine re-run, not just look idempotent. */
    @Test
    void v2RoleCreationGuardIsIdempotentUnderARealReRun() {
        assertThatCode(() -> Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("payments")
                .load()
                .migrate())
                .doesNotThrowAnyException();
    }

    @Test
    void paymentAppRoleRequiresItsProvisionedPassword() {
        assertThatThrownBy(() -> connectAsPaymentApp("definitely-wrong-password"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("password authentication failed");

        assertThatCode(() -> connectAsPaymentApp(PAYMENT_APP_PASSWORD).close())
                .doesNotThrowAnyException();
    }

    /** AC3 (positive + negative) and AC4: payment_app can INSERT/SELECT but not UPDATE/DELETE on
     * its two granted tables, and the identity-column INSERT works without a separate
     * sequence-permission failure. */
    @Test
    void paymentAppCanInsertAndSelectButNotUpdateOrDeleteOnTheTwoGrantedTables() throws SQLException {
        long paymentId = seedPaymentAsAdmin();
        long receiptId = seedReceiptAsAdmin(paymentId);

        try (Connection app = connectAsPaymentApp(PAYMENT_APP_PASSWORD)) {
            assertInsertAndSelectSucceedUpdateAndDeleteAreDenied(app, "ledger_entries", receiptId);
            assertInsertAndSelectSucceedUpdateAndDeleteAreDenied(app, "verification_records", paymentId);
        }
    }

    /** AC5: payment_app has no access at all to any of the other seven tables. */
    @Test
    void paymentAppHasNoAccessAtAllToTablesOutsideAc3Scope() throws SQLException {
        try (Connection app = connectAsPaymentApp(PAYMENT_APP_PASSWORD); Statement statement = app.createStatement()) {
            for (String table : UNGRANTED_TABLES) {
                assertThatThrownBy(() -> statement.executeQuery("SELECT * FROM payments." + table))
                        .as("SELECT on %s must be denied for payment_app (not one of AC3's two named tables)", table)
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("permission denied");
            }
        }
    }

    /** AC5, the real query form: a direct information_schema check proves zero grant rows exist for
     * payment_app on any of the seven ungranted tables, not just that a SELECT happens to fail.
     * table_privileges returns one row per privilege type (so each granted table appears twice —
     * INSERT and SELECT), so the distinct table-name set, not the raw row list, is what must match
     * GRANTED_TABLES exactly. */
    @Test
    void informationSchemaShowsNoGrantRowsForPaymentAppOnUngrantedTables() throws SQLException {
        try (Connection admin = adminConnection();
             Statement statement = admin.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT DISTINCT table_name FROM information_schema.table_privileges "
                             + "WHERE grantee = 'payment_app' AND table_schema = 'payments'")) {
            List<String> grantedTableNames = new ArrayList<>();
            while (resultSet.next()) {
                grantedTableNames.add(resultSet.getString(1));
            }
            assertThat(grantedTableNames).containsExactlyInAnyOrderElementsOf(GRANTED_TABLES);
        }
    }

    /** AC6: no password for payment_app is committed anywhere in V2 — a text-scan, not a DB
     * behavior check, mirroring T01's own file-content convention for this exact kind of guard.
     * Checked against the file with its own SQL comments stripped first: V2's own explanatory
     * comment legitimately documents the local-dev `ALTER ROLE ... PASSWORD 'payment-app-local-
     * only'` instruction in prose, which would otherwise defeat this exact check — the same
     * comment-vs-content ambiguity already found and fixed twice in T01's own pom checks. */
    @Test
    void noPasswordIsCommittedInTheRoleMigration() throws IOException {
        String v2 = stripSqlComments(
                Files.readString(Path.of("src/main/resources/db/migration/V2__payment_app_role_and_grants.sql")));

        assertThat(v2).doesNotContainPattern("(?i)PASSWORD\\s+'[^']+'");
    }

    /** Strips `-- ...` line comments before a negative-proof check — the SQL counterpart to the
     * `stripXmlComments`/`stripJavaComments` helpers T01's own test file needed for the identical
     * reason. */
    private static String stripSqlComments(String sql) {
        return sql.replaceAll("--.*", "");
    }

    /** AC7: the owner/grantee split only means anything if the baseline tables are owned by the
     * migration/admin role, never by payment_app. */
    @Test
    void baselineTablesAreOwnedByTheMigrationRoleNeverByPaymentApp() throws SQLException {
        try (Connection admin = adminConnection();
             Statement statement = admin.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT tablename, tableowner FROM pg_tables WHERE schemaname = 'payments'")) {
            while (resultSet.next()) {
                assertThat(resultSet.getString("tableowner"))
                        .as("payments.%s must not be owned by payment_app", resultSet.getString("tablename"))
                        .isNotEqualTo("payment_app");
            }
        }
    }

    /** AC8: payment_app cannot create or drop anything in the payments schema. */
    @Test
    void paymentAppCannotPerformDdlInThePaymentsSchema() throws SQLException {
        try (Connection app = connectAsPaymentApp(PAYMENT_APP_PASSWORD); Statement statement = app.createStatement()) {
            assertThatThrownBy(() -> statement.execute("CREATE TABLE payments.evil_test(id int)"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");

            assertThatThrownBy(() -> statement.execute("DROP TABLE payments.ledger_entries"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("must be owner of table ledger_entries");
        }
    }

    private void assertInsertAndSelectSucceedUpdateAndDeleteAreDenied(Connection app, String table, long fkId) throws SQLException {
        try (Statement statement = app.createStatement()) {
            String insertSql = switch (table) {
                case "ledger_entries" -> "INSERT INTO payments.ledger_entries (receipt_id, prev_hash, entry_hash, payload) "
                        + "VALUES (" + fkId + ", repeat('0', 64), repeat('a', 64), '{}')";
                case "verification_records" -> "INSERT INTO payments.verification_records (payment_id, to_state) "
                        + "VALUES (" + fkId + ", 'SEEN')";
                default -> throw new IllegalArgumentException("no INSERT fixture for " + table);
            };
            statement.execute(insertSql);

            String pkColumn = table.equals("ledger_entries") ? "seq" : "id";
            String fkColumn = table.equals("ledger_entries") ? "receipt_id" : "payment_id";
            try (ResultSet resultSet = statement.executeQuery(
                    "SELECT count(*) FROM payments." + table + " WHERE " + fkColumn + " = " + fkId)) {
                resultSet.next();
                assertThat(resultSet.getInt(1)).as("SELECT on %s must see the row payment_app just inserted", table).isEqualTo(1);
            }

            String updateColumn = table.equals("ledger_entries") ? "prev_hash" : "to_state";
            String updateValue = table.equals("ledger_entries") ? "repeat('9', 64)" : "'CONFIRMING'";
            assertThatThrownBy(() -> statement.execute(
                    "UPDATE payments." + table + " SET " + updateColumn + " = " + updateValue + " WHERE " + fkColumn + " = " + fkId))
                    .as("UPDATE on %s must be denied for payment_app", table)
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");

            assertThatThrownBy(() -> statement.execute("DELETE FROM payments." + table + " WHERE " + fkColumn + " = " + fkId))
                    .as("DELETE on %s must be denied for payment_app", table)
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("permission denied");

            assertThat(pkColumn).isNotBlank(); // documents the per-table PK name; no separate assertion needed
        }
    }

    /** Seeds an invoice + payment row as admin (payment_app has no grant on either table) so the
     * granted tables' own FK columns have something real to reference. */
    private static long seedPaymentAsAdmin() throws SQLException {
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            statement.execute("""
                    INSERT INTO payments.invoices
                        (invoice_uuid, merchant_account_uuid, chain, token_contract_address, token_decimals,
                         amount, pay_to_address, expires_at)
                    VALUES
                        (gen_random_uuid(), gen_random_uuid(), 'ETHEREUM', repeat('1', 40), 6,
                         1000, repeat('2', 40), now() + interval '1 hour')
                    """);
            try (ResultSet invoiceId = statement.executeQuery(
                    "SELECT id FROM payments.invoices ORDER BY id DESC LIMIT 1")) {
                invoiceId.next();
                long invId = invoiceId.getLong(1);
                statement.execute("""
                        INSERT INTO payments.payments
                            (payment_uuid, invoice_id, chain, tx_hash, to_address, token_contract_address, amount)
                        VALUES
                            (gen_random_uuid(), %d, 'ETHEREUM', '0xit-schema-test', repeat('2', 40), repeat('1', 40), 1000)
                        """.formatted(invId));
            }
            try (ResultSet paymentId = statement.executeQuery(
                    "SELECT id FROM payments.payments ORDER BY id DESC LIMIT 1")) {
                paymentId.next();
                return paymentId.getLong(1);
            }
        }
    }

    private static long seedReceiptAsAdmin(long paymentId) throws SQLException {
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            statement.execute("""
                    INSERT INTO payments.receipts
                        (receipt_uuid, payment_id, digest_sha256, signature, kms_key_id, s3_bucket, s3_key, signed_at)
                    VALUES
                        (gen_random_uuid(), %d, repeat('a', 64), 'it-signature', 'it-kms-key', 'it-bucket', 'it-key', now())
                    """.formatted(paymentId));
            try (ResultSet receiptId = statement.executeQuery(
                    "SELECT id FROM payments.receipts ORDER BY id DESC LIMIT 1")) {
                receiptId.next();
                return receiptId.getLong(1);
            }
        }
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static Connection connectAsPaymentApp(String password) throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "payment_app", password);
    }
}
