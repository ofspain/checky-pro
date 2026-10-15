package com.themistra.payment.invoice;

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

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * T08 (R5), Phase 11 Gaps 1/2/7 (Kimi): a real Testcontainers proof that
 * {@code findExpiredCandidatesWithoutInFlightPayment} and {@code updateStateIfCurrentState} do
 * what the native/JPQL query text claims against a real Postgres, connected as {@code
 * payment_app} (not the migration-owning role) - the same narrow-context pattern {@code
 * WatchRepositoryIntegrationTest} (crypto-service) established. {@code payments.payments} has no
 * JPA entity (task 11's own scope), so rows are seeded via a raw admin connection.
 *
 * <p><b>Significant finding surfaced while writing this test (not by running it - Docker is
 * unavailable this session, so this class itself cannot be executed locally; found by re-reading
 * every grant migration against {@code InvoiceRepository}'s own real queries):</b> {@code
 * updateStateIfCurrentState} issues a real {@code UPDATE payments.invoices ...} as {@code
 * payment_app}, but V3 only ever granted {@code INSERT, SELECT} on {@code invoices} - no
 * migration granted {@code UPDATE} until this task's own {@code V6}, added specifically because
 * of this. No prior test caught this because every existing {@code InvoiceService} test mocks
 * {@code InvoiceRepository}; {@link
 * com.themistra.payment.SchemaBaselineMigrationIntegrationTest#grantedTablesHaveExactlyInsertAndSelectPrivilegesNoMore}
 * (before this task's own fix) was itself asserting the absence of exactly this grant. This class
 * is the first test that would have caught the gap by actually calling {@code
 * updateStateIfCurrentState} over a real {@code payment_app} connection.</p>
 *
 * <p><b>Confirmed green 2026-10-14</b> once Docker became available this session - this class
 * itself had its own {@code TransactionRequiredException} bug (see the two
 * {@code updateStateIfCurrentState*} tests' own Javadoc), unrelated to the V6 grant finding
 * above; both are now fixed.</p>
 */
@Testcontainers
@SpringBootTest(classes = InvoiceRepositoryIntegrationTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
class InvoiceRepositoryIntegrationTest {

    private static final UUID MERCHANT_ACCOUNT_UUID = UUID.randomUUID();
    private static final Instant PAST = Instant.now().minus(1, ChronoUnit.HOURS);
    private static final Instant FUTURE = Instant.now().plus(1, ChronoUnit.HOURS);

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
    private InvoiceRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @AfterEach
    void cleanUp() throws SQLException {
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            statement.execute("DELETE FROM payments.payments");
            statement.execute("DELETE FROM payments.invoices");
        }
    }

    private Invoice seedInvoice(InvoiceState initialPersistedState, Instant expiresAt) {
        Invoice invoice = Invoice.create(UUID.randomUUID(), MERCHANT_ACCOUNT_UUID, "ETHEREUM", "0xabc",
                (short) 18, new BigDecimal("1000"), "0xdef", null, expiresAt, UUID.randomUUID(), Instant.now());
        Invoice saved = repository.save(invoice);
        if (initialPersistedState != InvoiceState.OPEN) {
            setStateAsAdmin(saved.getId(), initialPersistedState);
        }
        return saved;
    }

    private static void seedPaymentForInvoice(long invoiceId, String state) throws SQLException {
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            statement.execute("""
                    INSERT INTO payments.payments
                    (payment_uuid, invoice_id, chain, tx_hash, to_address, token_contract_address, amount, state)
                    VALUES ('%s', %d, 'ETHEREUM', '%s', '0xdef', '0xabc', 1000, '%s')
                    """.formatted(UUID.randomUUID(), invoiceId, UUID.randomUUID(), state));
        }
    }

    private static void setStateAsAdmin(Long invoiceId, InvoiceState state) {
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            statement.execute("UPDATE payments.invoices SET state = '" + state + "' WHERE id = " + invoiceId);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    /** R5 (AC1/AC2, Phase 11 Gaps 1/2): seeds one invoice per non-terminal payment state
     * (CREATED/WATCHING/SEEN/CONFIRMING, each excluded), one with a terminal (FINALIZED) payment
     * (eligible - a terminal payment does not block expiry), one not yet expired, one already
     * EXPIRED, and one truly eligible invoice with no payment at all - asserts the candidate set
     * is exactly the truly-eligible one. */
    @Test
    void findExpiredCandidatesWithoutInFlightPaymentReturnsOnlyTheEligibleInvoice() throws SQLException {
        Invoice eligible = seedInvoice(InvoiceState.OPEN, PAST);

        Invoice withCreatedPayment = seedInvoice(InvoiceState.OPEN, PAST);
        seedPaymentForInvoice(withCreatedPayment.getId(), "CREATED");

        Invoice withWatchingPayment = seedInvoice(InvoiceState.OPEN, PAST);
        seedPaymentForInvoice(withWatchingPayment.getId(), "WATCHING");

        Invoice withSeenPayment = seedInvoice(InvoiceState.OPEN, PAST);
        seedPaymentForInvoice(withSeenPayment.getId(), "SEEN");

        Invoice withConfirmingPayment = seedInvoice(InvoiceState.OPEN, PAST);
        seedPaymentForInvoice(withConfirmingPayment.getId(), "CONFIRMING");

        Invoice withTerminalPayment = seedInvoice(InvoiceState.OPEN, PAST);
        seedPaymentForInvoice(withTerminalPayment.getId(), "FINALIZED");

        seedInvoice(InvoiceState.OPEN, FUTURE);
        seedInvoice(InvoiceState.EXPIRED, PAST);

        List<Invoice> candidates = repository.findExpiredCandidatesWithoutInFlightPayment(Instant.now());

        assertThat(candidates)
                .extracting(Invoice::getInvoiceUuid)
                .containsExactlyInAnyOrder(eligible.getInvoiceUuid(), withTerminalPayment.getInvoiceUuid());
    }

    /** Phase 11 (self-identified): proves the V6 grant actually exists and works - without it,
     * this call fails with a real "permission denied for table invoices" SQLException rather than
     * returning 1. Also closes Gap 7: updated_at must advance.
     *
     * <p><b>Confirmed broken, then fixed, 2026-10-14 once Docker became available this
     * session:</b> {@code updateStateIfCurrentState} is a {@code @Modifying} repository method -
     * unlike the auto-generated {@code save}/{@code findById} CRUD methods Spring Data wraps in
     * their own transaction automatically, a custom {@code @Modifying} query requires the
     * caller to provide one; calling it directly (not through {@code InvoiceService}'s own
     * {@code @Transactional} methods, the real production caller) threw {@code
     * TransactionRequiredException}. Not a production defect - only this test's own direct call
     * needed one. A bare {@code @Transactional} on the test method would not actually fix this
     * either: Spring's test transactions roll back by default, and even with {@code @Commit}
     * the commit only happens after the method returns - this test's own assertions run via a
     * *separate* raw JDBC connection, so they would see pre-update data regardless. A {@link
     * TransactionTemplate} scoped to just the repository call commits before the method
     * continues, exactly as {@code ChainEventConsumer.onChainTxReorged} (T12) already uses the
     * identical pattern for an identical reason.</p> */
    @Test
    void updateStateIfCurrentStateActuallyUpdatesStateAndUpdatedAtForARealOpenInvoice() throws SQLException {
        Invoice invoice = seedInvoice(InvoiceState.OPEN, PAST);
        Instant before = fetchUpdatedAt(invoice.getId());
        Instant now = Instant.now().plus(1, ChronoUnit.SECONDS);

        int rowsUpdated = new TransactionTemplate(transactionManager).execute(status ->
                repository.updateStateIfCurrentState(invoice.getId(), InvoiceState.OPEN, InvoiceState.EXPIRED, now));

        assertThat(rowsUpdated).isEqualTo(1);
        assertThat(fetchState(invoice.getId())).isEqualTo("EXPIRED");
        assertThat(fetchUpdatedAt(invoice.getId())).isAfter(before);
    }

    /** AC6 (Phase 9 correction): proves the WHERE-guard really prevents overwriting a real,
     * already-changed row (e.g. a payment became in-flight first) - not just a mocked-repository
     * behavior. See the test above for why a {@link TransactionTemplate}, not a bare {@code
     * @Transactional}, is the real fix for this class's own {@code TransactionRequiredException}. */
    @Test
    void updateStateIfCurrentStateReturnsZeroAndLeavesRowUnchangedWhenNoLongerOpen() throws SQLException {
        Invoice invoice = seedInvoice(InvoiceState.PAID, PAST);

        int rowsUpdated = new TransactionTemplate(transactionManager).execute(status ->
                repository.updateStateIfCurrentState(invoice.getId(), InvoiceState.OPEN, InvoiceState.EXPIRED, Instant.now()));

        assertThat(rowsUpdated).isZero();
        assertThat(fetchState(invoice.getId())).isEqualTo("PAID");
    }

    private static String fetchState(Long invoiceId) throws SQLException {
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT state FROM payments.invoices WHERE id = " + invoiceId)) {
            resultSet.next();
            return resultSet.getString("state");
        }
    }

    private static Instant fetchUpdatedAt(Long invoiceId) throws SQLException {
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT updated_at FROM payments.invoices WHERE id = " + invoiceId)) {
            resultSet.next();
            return resultSet.getTimestamp("updated_at").toInstant();
        }
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    @Configuration
    @EnableAutoConfiguration
    @EntityScan(basePackageClasses = Invoice.class)
    @EnableJpaRepositories(basePackageClasses = InvoiceRepository.class)
    static class TestConfig {
    }
}
