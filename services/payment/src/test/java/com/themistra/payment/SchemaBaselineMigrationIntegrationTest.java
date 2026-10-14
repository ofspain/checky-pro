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

    // T06/V3: outbox, shedlock, payments, and (since V6, T08 Phase 11) invoices each have their
    // own, differently-shaped privilege set (outbox: INSERT+SELECT+UPDATE; shedlock:
    // SELECT+INSERT+UPDATE, T08; payments: SELECT-only, T08; invoices: INSERT+SELECT+UPDATE,
    // T08 V6), so each is tracked and tested separately, not folded into GRANTED_TABLES's own
    // exactly-two-privileges check.
    private static final List<String> GRANTED_TABLES =
            List.of("ledger_entries", "verification_records");
    private static final List<String> ALL_GRANTED_TABLE_NAMES =
            List.of("ledger_entries", "verification_records", "invoices", "outbox", "shedlock", "payments");
    // The remaining three baseline tables (receipts, ledger_anchors, processed_events) get no
    // grant at all in this task — each one's own grant lands in whichever later task first needs
    // it (T17, deferred/T21-skipped, T11 respectively), mirroring crypto-service's own
    // incremental V2-V9 pattern.
    private static final List<String> UNGRANTED_TABLES =
            List.of("receipts", "ledger_anchors", "processed_events");

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
            assertThat(succeededVersions).containsExactly("1", "2", "3", "4", "5", "6");
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

    /** Phase 11 finding 1: the test above only proves Flyway's own bookkeeping skips an
     * already-applied version — it never re-executes V2's SQL, so it can't catch a real regression
     * in V2's own `IF NOT EXISTS` guard or its GRANT statements' idempotency. Re-executes V2's
     * actual file content directly against the admin connection a second time (V2 already ran once
     * via `@BeforeAll`), then re-proves the grant is still exactly as scoped afterward — mirrors
     * `notification-service`'s own identically-purposed test. */
    @Test
    void v2sOwnSqlIsIdempotentWhenReExecutedDirectlyNotJustSkippedByFlyway() throws IOException, SQLException {
        String v2Sql = Files.readString(Path.of("src/main/resources/db/migration/V2__payment_app_role_and_grants.sql"));

        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            assertThatCode(() -> statement.execute(v2Sql))
                    .as("V2's own CREATE ROLE guard and GRANT statements must tolerate a direct re-run")
                    .doesNotThrowAnyException();
        }

        long paymentId = seedPaymentAsAdmin();
        try (Connection app = connectAsPaymentApp(PAYMENT_APP_PASSWORD)) {
            assertInsertAndSelectSucceedUpdateAndDeleteAreDenied(app, "verification_records", paymentId);
        }
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

    /** AC5: payment_app has no access at all to any of the other seven tables — not just SELECT
     * (Phase 11 finding 4: a stray INSERT-only grant without SELECT would otherwise slip past a
     * SELECT-only check here, even though the information_schema test would still catch it). */
    @Test
    void paymentAppHasNoAccessAtAllToTablesOutsideAc3Scope() throws SQLException {
        try (Connection app = connectAsPaymentApp(PAYMENT_APP_PASSWORD); Statement statement = app.createStatement()) {
            for (String table : UNGRANTED_TABLES) {
                assertThatThrownBy(() -> statement.executeQuery("SELECT * FROM payments." + table))
                        .as("SELECT on %s must be denied for payment_app (not one of AC3's two named tables)", table)
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("permission denied");
                assertThatThrownBy(() -> statement.execute("INSERT INTO payments." + table + " DEFAULT VALUES"))
                        .as("INSERT on %s must be denied for payment_app", table)
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("permission denied");
                String anyRealColumn = anyRealColumnOf(table);
                assertThatThrownBy(() -> statement.execute(
                        "UPDATE payments." + table + " SET " + anyRealColumn + " = " + anyRealColumn))
                        .as("UPDATE on %s must be denied for payment_app", table)
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("permission denied");
                assertThatThrownBy(() -> statement.execute("DELETE FROM payments." + table))
                        .as("DELETE on %s must be denied for payment_app", table)
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
            assertThat(grantedTableNames).containsExactlyInAnyOrderElementsOf(ALL_GRANTED_TABLE_NAMES);
        }
    }

    /** Phase 11 finding 2: the test above only proves the *set of tables* with a grant row equals
     * GRANTED_TABLES — it never checks which privilege *types* are held on those tables. If V2
     * accidentally granted only SELECT (no INSERT) on `ledger_entries`, this would stay silent;
     * the functional test would still catch it, but with a less precise failure. AC3 requires
     * exactly INSERT+SELECT, nothing more, nothing less, on each granted table. */
    @Test
    void grantedTablesHaveExactlyInsertAndSelectPrivilegesNoMore() throws SQLException {
        for (String table : GRANTED_TABLES) {
            try (Connection admin = adminConnection();
                 Statement statement = admin.createStatement();
                 ResultSet resultSet = statement.executeQuery(
                         "SELECT privilege_type FROM information_schema.table_privileges "
                                 + "WHERE grantee = 'payment_app' AND table_schema = 'payments' AND table_name = '" + table + "'")) {
                List<String> privileges = new ArrayList<>();
                while (resultSet.next()) {
                    privileges.add(resultSet.getString(1));
                }
                assertThat(privileges).as("%s privilege set", table).containsExactlyInAnyOrder("INSERT", "SELECT");
            }
        }
    }

    /** T08/V6 (Phase 11): invoices' own privilege set widened from INSERT+SELECT to include
     * UPDATE - InvoiceRepository.updateStateIfCurrentState (Phase 9's own atomic conditional
     * update, the fix for the lost-update race) issues a real UPDATE against this table as
     * payment_app; without this grant it fails with a real permission-denied error. Self-
     * identified while resolving Kimi's Phase 11 Gap 1/2 suggestions - no prior test exercised a
     * real UPDATE against invoices as payment_app, so this gap was invisible to every mocked-
     * repository unit test. */
    @Test
    void invoicesHasExactlyInsertSelectAndUpdatePrivilegesNoMore() throws SQLException {
        try (Connection admin = adminConnection();
             Statement statement = admin.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT privilege_type FROM information_schema.table_privileges "
                             + "WHERE grantee = 'payment_app' AND table_schema = 'payments' AND table_name = 'invoices'")) {
            List<String> privileges = new ArrayList<>();
            while (resultSet.next()) {
                privileges.add(resultSet.getString(1));
            }
            assertThat(privileges).as("invoices privilege set").containsExactlyInAnyOrder("INSERT", "SELECT", "UPDATE");
        }
    }

    /** T06/V3 (Phase 9/11): outbox's own privilege set is wider than GRANTED_TABLES's (INSERT,
     * SELECT, UPDATE - OutboxRelay, T04, marks published_at on successful delivery), so it is
     * verified separately rather than folded into the exactly-INSERT-SELECT check above. */
    @Test
    void outboxHasExactlyInsertSelectAndUpdatePrivilegesNoMore() throws SQLException {
        try (Connection admin = adminConnection();
             Statement statement = admin.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT privilege_type FROM information_schema.table_privileges "
                             + "WHERE grantee = 'payment_app' AND table_schema = 'payments' AND table_name = 'outbox'")) {
            List<String> privileges = new ArrayList<>();
            while (resultSet.next()) {
                privileges.add(resultSet.getString(1));
            }
            assertThat(privileges).as("outbox privilege set").containsExactlyInAnyOrder("INSERT", "SELECT", "UPDATE");
        }
    }

    /** T08/V4: shedlock's own privilege set (SELECT, INSERT, UPDATE - mirrors notification-
     * service's own empirically-confirmed grant exactly; no DELETE, a lock row is never removed). */
    @Test
    void shedlockHasExactlySelectInsertAndUpdatePrivilegesNoMore() throws SQLException {
        try (Connection admin = adminConnection();
             Statement statement = admin.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT privilege_type FROM information_schema.table_privileges "
                             + "WHERE grantee = 'payment_app' AND table_schema = 'payments' AND table_name = 'shedlock'")) {
            List<String> privileges = new ArrayList<>();
            while (resultSet.next()) {
                privileges.add(resultSet.getString(1));
            }
            assertThat(privileges).as("shedlock privilege set").containsExactlyInAnyOrder("SELECT", "INSERT", "UPDATE");
        }
    }

    /** T08/V5: payments' own privilege set is SELECT-only (InvoiceExpirySweepJob's own native
     * in-flight-payment check reads it; this task never writes to payments.payments). */
    @Test
    void paymentsTableHasExactlySelectPrivilegeNoMore() throws SQLException {
        try (Connection admin = adminConnection();
             Statement statement = admin.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT privilege_type FROM information_schema.table_privileges "
                             + "WHERE grantee = 'payment_app' AND table_schema = 'payments' AND table_name = 'payments'")) {
            List<String> privileges = new ArrayList<>();
            while (resultSet.next()) {
                privileges.add(resultSet.getString(1));
            }
            assertThat(privileges).as("payments privilege set").containsExactly("SELECT");
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

    /** Phase 11 finding 3: V2 must stay environment-agnostic — `GRANT CONNECT` targets
     * `current_database()` dynamically, never a hardcoded database name. The executable SQL
     * (comments stripped, since this file's own explanatory comment legitimately says "this
     * migration is not tied to 'checky'", which would otherwise defeat a naive negative-proof)
     * must contain `current_database()` and must not contain the literal "checky". */
    @Test
    void v2NeverHardcodesTheDatabaseName() throws IOException {
        String v2 = stripSqlComments(
                Files.readString(Path.of("src/main/resources/db/migration/V2__payment_app_role_and_grants.sql")));

        assertThat(v2).contains("current_database()");
        assertThat(v2).doesNotContain("checky");
    }

    /** Phase 11 finding 7: AC1 says V1 is schema-only, with the role/grants split entirely into V2.
     * The byte-for-byte comparison against design.md enforces this only indirectly — if a future
     * edit ever added a GRANT/CREATE ROLE into design.md's own verbatim block, that test would
     * still pass while the V1/V2 architectural split was silently violated. This is a direct,
     * independent structural invariant on V1 itself. */
    @Test
    void v1ContainsNoRoleOrGrantStatements() throws IOException {
        String v1 = stripSqlComments(
                Files.readString(Path.of("src/main/resources/db/migration/V1__payments_baseline.sql")));

        assertThat(v1).doesNotContainPattern("(?i)CREATE ROLE");
        assertThat(v1).doesNotContainPattern("(?i)\\bGRANT\\b");
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

    /** Phase 11 finding 5: L6 requires `NUMERIC(78, 0)` on every amount column, and no
     * floating-point type anywhere. The byte-for-byte test is strong only if `design.md` itself is
     * correct; this probes the live Postgres catalog directly, proving the migration actually
     * instantiates the intended types rather than merely matching intended text. */
    @Test
    void amountColumnsAreNumericSeventyEightZeroAndNoFloatingPointTypeExistsAnywhere() throws SQLException, IOException {
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            for (String table : List.of("invoices", "payments")) {
                try (ResultSet resultSet = statement.executeQuery(
                        "SELECT data_type, numeric_precision, numeric_scale FROM information_schema.columns "
                                + "WHERE table_schema = 'payments' AND table_name = '" + table + "' AND column_name = 'amount'")) {
                    resultSet.next();
                    assertThat(resultSet.getString("data_type")).as("%s.amount type", table).isEqualTo("numeric");
                    assertThat(resultSet.getInt("numeric_precision")).as("%s.amount precision", table).isEqualTo(78);
                    assertThat(resultSet.getInt("numeric_scale")).as("%s.amount scale", table).isEqualTo(0);
                }
            }
        }

        String v1 = stripSqlComments(
                Files.readString(Path.of("src/main/resources/db/migration/V1__payments_baseline.sql")));
        assertThat(v1).doesNotContainPattern("(?i)\\bFLOAT\\b|\\bDOUBLE PRECISION\\b|\\bREAL\\b");
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

    /** The seven ungranted tables have no column in common ("id" doesn't exist on
     * `processed_events`/`shedlock`, whose primary keys are named differently) — this maps each
     * one to a real column safe to reference in a no-op `SET col = col` UPDATE. */
    private static String anyRealColumnOf(String ungrantedTable) {
        return switch (ungrantedTable) {
            case "invoices", "payments" -> "state";
            case "receipts" -> "signature";
            case "ledger_anchors" -> "head_entry_hash";
            case "processed_events" -> "event_key";
            case "outbox" -> "event_type";
            case "shedlock" -> "name";
            default -> throw new IllegalArgumentException("no known column for " + ungrantedTable);
        };
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
     * granted tables' own FK columns have something real to reference. Uses `RETURNING id`
     * (Phase 11 finding 6) rather than a separate `SELECT ... ORDER BY id DESC LIMIT 1` — the
     * latter is order-dependent and would break under parallel test execution or leftover rows
     * from an earlier run; `RETURNING` hands back exactly the row this statement itself inserted. */
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
            // tx_hash must be unique per call (payments.uq_payment_chain_tx) - this helper is
            // called from more than one test method against the one, class-shared Testcontainers
            // instance, so a literal constant here would collide on the second call.
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

    private static long seedReceiptAsAdmin(long paymentId) throws SQLException {
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement();
             ResultSet inserted = statement.executeQuery("""
                    INSERT INTO payments.receipts
                        (receipt_uuid, payment_id, digest_sha256, signature, kms_key_id, s3_bucket, s3_key, signed_at)
                    VALUES
                        (gen_random_uuid(), %d, repeat('a', 64), 'it-signature', 'it-kms-key', 'it-bucket', 'it-key', now())
                    RETURNING id
                    """.formatted(paymentId))) {
            inserted.next();
            return inserted.getLong(1);
        }
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static Connection connectAsPaymentApp(String password) throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), "payment_app", password);
    }
}
