package com.themistra.payment.consumer;

import com.themistra.payment.common.CryptoClient;
import com.themistra.payment.invoice.InvoiceService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * T14 (R13, R14, O6). The first real (non-mocked) proof that {@link ChainEventConsumer}'s own
 * wiring behaves correctly against a live Postgres and a real Kafka broker - every prior task's
 * own tests proved this only at the Mockito level.
 *
 * <p><b>No existing precedent in this codebase combines Testcontainers-Postgres with a real
 * Kafka broker</b> (confirmed by direct search across all four services). This class adapts the
 * single-class/shared-container/{@code Awaitility} shape of {@code notification-service}'s own
 * {@code AuthEventConsumerIntegrationTest} - which, like this class, deliberately does
 * <b>not</b> use a Testcontainers Kafka image, relying instead on the already-running local
 * broker ({@code services/auth/compose.local.yaml}, shared by every service in this monorepo,
 * started for this session). This is Phase 4's own explicit, approved design (Finding 1's own
 * reworded Decision 1; Finding 7 rejected the alternative of pre-creating topics via
 * {@code AdminClient} for the same reason) - not a gap. {@code @MockBean CryptoClient}, and
 * raw-JDBC verification against {@code payments.payments}/{@code payments.invoices} in place of
 * that precedent's own projection-table checks (neither {@code Payment} nor {@code Invoice} is
 * reachable from this package, L12).</p>
 *
 * <p><b>Phase 4 Finding 2:</b> {@code auto-offset-reset=earliest} - without it, a message
 * published before the listener container's own initial partition assignment completes (a real,
 * asynchronous race at context startup) would silently never be read, timing out
 * {@code Awaitility} rather than failing clearly. <b>Finding 3, corrected at Phase 6:</b>
 * {@code spring.task.scheduling.enabled=false} does <b>not</b> actually stop
 * {@code OutboxRelay}/{@code InvoiceExpirySweepJob} from running, since
 * {@code PaymentServiceApplication} declares {@code @EnableScheduling} explicitly (independent
 * of the Boot auto-configuration property) - left in place as a harmless, documented no-op
 * rather than a real fix, since neither job can affect this task's own assertions (every seeded
 * invoice's own {@code expiresAt} is far beyond this test's own runtime; outbox-forwarded events
 * are outbound, never consumed by this task's own listener-side checks). <b>Finding 9:</b>
 * {@code CryptoClient} is mocked because this task's own scope is dedupe/ordering/staleness, not
 * crypto-service availability - {@code onChainTxSeen}'s own held-open-connection behavior across
 * the real {@code unregisterWatch} call remains covered by existing unit tests and is out of
 * scope here.</p>
 */
@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.kafka.consumer.group-id=payment-chain-idempotency-it",
                "spring.kafka.consumer.auto-offset-reset=earliest",
                "spring.task.scheduling.enabled=false"
        })
/* Phase 6 (self-caught, forced by reality): a plain full @SpringBootTest also scans
 * com.themistra.payment.archtestfixtures - ArchitectureTest's own deliberately-invalid @Entity
 * fixtures (RogueUnmappedEntity has no @Id by design, exercising a negative ArchUnit path) -
 * causing Hibernate to fail context startup entirely. No existing test in this codebase had
 * ever booted the real, unscoped PaymentServiceApplication before this one; every other
 * integration test either uses a narrow custom config (InvoiceRepositoryIntegrationTest) or a
 * slice (InvoiceControllerTest), which is why this was never surfaced until now. Scoping
 * @EntityScan to the real feature-module packages (matching ArchitectureTest's own
 * FEATURE_MODULES list exactly) excludes archtestfixtures without needing any other change.
 * T19 added "receipt" here (Receipt/LedgerEntry are the first entities in that module) - the
 * exact same "Not a managed type" failure this comment already describes for the whole class,
 * confirmed again by a real run before this fix. */
@EntityScan(basePackages = {
        "com.themistra.payment.invoice",
        "com.themistra.payment.payment",
        "com.themistra.payment.events",
        "com.themistra.payment.verification",
        "com.themistra.payment.consumer",
        "com.themistra.payment.receipt"
})
class ChainEventConsumerIdempotencyAndOrderingIntegrationTest {

    private static final String PAYMENT_APP_PASSWORD = "it-payment-app-password";
    private static final UUID MERCHANT_ACCOUNT_UUID = UUID.randomUUID();
    private static final String CHAIN = "ETHEREUM";
    private static final String TOKEN_CONTRACT_ADDRESS = "0x111";

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "payment_app");
        registry.add("spring.datasource.password", () -> PAYMENT_APP_PASSWORD);
    }

    /** Phase 11 Kimi Finding 5: this task's own approved design (Phase 4/9) relies on the real,
     * already-running local Kafka broker, not a Testcontainers image - a missing broker
     * previously failed only after a confusing 40-second {@code Awaitility} timeout deep inside
     * the first test method. Failing fast here, with a clear message naming the exact compose
     * command, is cheap and makes the real cause obvious immediately. */
    @BeforeAll
    static void checkKafkaBrokerIsReachable() throws IOException {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress("localhost", 9094), 2000);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "No Kafka broker reachable at localhost:9094 - this test needs the shared local "
                            + "stack running first: docker compose -f services/auth/compose.local.yaml up -d", e);
        }
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
    private InvoiceService invoiceService;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @MockBean
    private CryptoClient cryptoClient;

    /** This task's own scope has no HTTP/security surface at all, but a full, unscoped
     * {@code @SpringBootTest} still instantiates {@code ResourceServerConfig}'s own real
     * {@code securityFilterChain} bean, which needs a {@code JwtDecoder} - normally
     * auto-configured from auth-service's own reachable JWKS endpoint, unavailable here. Mocked
     * out rather than excluding the whole security auto-configuration, to keep this test's own
     * context shape as close to the real application's as possible. */
    @MockBean
    private JwtDecoder jwtDecoder;

    /** Phase 4 Finding 4: widened beyond payments/invoices to also clear processed_events,
     * outbox, and verification_records - the static container is shared across every test
     * method in this class, so leftovers would otherwise accumulate across the whole run. */
    @AfterEach
    void cleanUp() throws SQLException {
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            statement.execute("DELETE FROM payments.verification_records");
            statement.execute("DELETE FROM payments.processed_events");
            statement.execute("DELETE FROM payments.outbox");
            statement.execute("DELETE FROM payments.payments");
            statement.execute("DELETE FROM payments.invoices");
        }
    }

    /** Phase 8 Kimi Finding 2: {@code kafkaTemplate.send(...)} is fire-and-forget by default -
     * a producer-side delivery failure would otherwise only surface as a later, less-specific
     * {@code Awaitility} timeout. Waiting on the real send future surfaces it immediately, at
     * the actual point of failure. */
    private void sendAndWait(String topic, String key, String json) {
        try {
            kafkaTemplate.send(topic, key, json).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while sending to " + topic, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Failed to send to " + topic, e);
        }
    }

    private UUID seedInvoice(UUID watchId) {
        UUID invoiceUuid = UUID.randomUUID();
        invoiceService.create(invoiceUuid, MERCHANT_ACCOUNT_UUID, CHAIN, TOKEN_CONTRACT_ADDRESS,
                (short) 18, new BigDecimal("1000"), "0xdef", null,
                Instant.now().plusSeconds(3600), watchId);
        return invoiceUuid;
    }

    // Phase 4 Finding 5: amount is always a quoted JSON string, matching the real schema's own
    // decimal-string requirement - never a Jackson-serialized BigDecimal, which would risk
    // writing a JSON number and testing the wrong producer contract.

    private static String seenJson(String idempotencyKey, UUID watchId, UUID invoiceUuid, String txHash) {
        return """
                {"idempotencyKey":"%s","watchId":"%s","invoiceUuid":"%s","chain":"%s",
                 "txHash":"%s","tokenContractAddress":"%s","confirmations":1,
                 "occurredAt":"2026-07-13T00:00:00Z"}
                """.formatted(idempotencyKey, watchId, invoiceUuid, CHAIN, txHash, TOKEN_CONTRACT_ADDRESS);
    }

    private static String confirmedJson(String idempotencyKey, UUID watchId, UUID invoiceUuid, String txHash,
                                         int confirmations) {
        return """
                {"idempotencyKey":"%s","watchId":"%s","invoiceUuid":"%s","chain":"%s",
                 "txHash":"%s","tokenContractAddress":"%s","confirmations":%d,
                 "occurredAt":"2026-07-13T00:05:00Z"}
                """.formatted(idempotencyKey, watchId, invoiceUuid, CHAIN, txHash, TOKEN_CONTRACT_ADDRESS, confirmations);
    }

    private static String finalizedJson(String idempotencyKey, UUID watchId, UUID invoiceUuid, String txHash) {
        return """
                {"idempotencyKey":"%s","watchId":"%s","invoiceUuid":"%s","chain":"%s",
                 "txHash":"%s","fromAddress":"0xfrom","toAddress":"0xdef",
                 "tokenContractAddress":"%s","amount":"1000","confirmations":12,
                 "addressPoisoningFlag":false,"occurredAt":"2026-07-13T00:10:00Z"}
                """.formatted(idempotencyKey, watchId, invoiceUuid, CHAIN, txHash, TOKEN_CONTRACT_ADDRESS);
    }

    private static String reorgedJson(String idempotencyKey, UUID watchId, UUID invoiceUuid, String txHash) {
        return """
                {"idempotencyKey":"%s","watchId":"%s","invoiceUuid":"%s","chain":"%s",
                 "txHash":"%s","tokenContractAddress":"%s","occurredAt":"2026-07-13T00:15:00Z"}
                """.formatted(idempotencyKey, watchId, invoiceUuid, CHAIN, txHash, TOKEN_CONTRACT_ADDRESS);
    }

    // Raw JDBC only below - neither Payment nor Invoice is reachable from this package (L12).

    private static String fetchPaymentState(String txHash) throws SQLException {
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT state FROM payments.payments WHERE tx_hash = '" + txHash + "'")) {
            return resultSet.next() ? resultSet.getString(1) : null;
        }
    }

    private static int countPaymentsForTxHash(String txHash) throws SQLException {
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(*) FROM payments.payments WHERE tx_hash = '" + txHash + "'")) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    /** Phase 8 Kimi Finding 4: AC3 must also prove the real invoice-state transition
     * ({@code InvoiceService.markOutcomeIfOpenOrExpired}, T13) that {@code onChainTxFinalized}'s
     * own real wiring performs - not just the payment row's own fields. */
    private static String fetchInvoiceState(UUID invoiceUuid) throws SQLException {
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT state FROM payments.invoices WHERE invoice_uuid = '" + invoiceUuid + "'")) {
            return resultSet.next() ? resultSet.getString(1) : null;
        }
    }

    /** Phase 11 Kimi Finding 2: R14 also requires "the ignore is itself recorded" - proves a
     * real {@code verification_records} row exists for the stale event's own idempotency key,
     * not just that the payment's own state stayed unchanged. Not applicable to the
     * unknown-payment scenario: {@code applyFinalized}'s own "no known payment" branch returns
     * before any {@code Payment} row (and therefore any {@code VerificationRecord}, which has a
     * {@code NOT NULL payment_id}) ever exists to attach one to - confirmed, not assumed. */
    private static boolean verificationRecordExistsForTriggerEventKey(String idempotencyKey) throws SQLException {
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(*) FROM payments.verification_records WHERE trigger_event_key = '"
                             + idempotencyKey + "'")) {
            resultSet.next();
            return resultSet.getInt(1) > 0;
        }
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private record FinalizedPayment(UUID watchId, UUID invoiceUuid) {
    }

    /** Drives a real payment, over the real broker, from nothing all the way to {@code FINALIZED}
     * - the shared setup {@code shouldHandleTheFullForwardSequenceAndReachFinalized} and two of
     * the three split R14 scenarios below need. */
    private FinalizedPayment finalizeARealPayment(String txHash) {
        UUID watchId = UUID.randomUUID();
        UUID invoiceUuid = seedInvoice(watchId);

        sendAndWait("chain.tx.seen", watchId.toString(),
                seenJson(CHAIN + ":" + txHash + ":seen", watchId, invoiceUuid, txHash));
        await().atMost(Duration.ofSeconds(40)).untilAsserted(() ->
                assertThat(fetchPaymentState(txHash)).isEqualTo("SEEN"));

        sendAndWait("chain.tx.confirmed", watchId.toString(),
                confirmedJson(CHAIN + ":" + txHash + ":confirmed", watchId, invoiceUuid, txHash, 3));
        await().atMost(Duration.ofSeconds(40)).untilAsserted(() ->
                assertThat(fetchPaymentState(txHash)).isEqualTo("CONFIRMING"));

        sendAndWait("chain.tx.finalized", watchId.toString(),
                finalizedJson(CHAIN + ":" + txHash + ":finalized", watchId, invoiceUuid, txHash));
        await().atMost(Duration.ofSeconds(40)).untilAsserted(() ->
                assertThat(fetchPaymentState(txHash)).isEqualTo("FINALIZED"));

        return new FinalizedPayment(watchId, invoiceUuid);
    }

    /** AC1, R13, named. The identical message, redelivered, must not create a second
     * {@code Payment} row or re-apply any effect - proven against the real
     * {@code processed_events} table and a real redelivered Kafka message, not a mocked
     * {@code insertIfNew} return value. */
    @Test
    void shouldDedupeDuplicateChainEventsByEventKey() {
        UUID watchId = UUID.randomUUID();
        UUID invoiceUuid = seedInvoice(watchId);
        String txHash = "0x" + UUID.randomUUID();
        String idempotencyKey = CHAIN + ":" + txHash + ":seen";
        String json = seenJson(idempotencyKey, watchId, invoiceUuid, txHash);

        sendAndWait("chain.tx.seen", watchId.toString(), json);
        await().atMost(Duration.ofSeconds(40)).untilAsserted(() ->
                assertThat(fetchPaymentState(txHash)).isEqualTo("SEEN"));
        assertThat(processedEventRepository.existsById(idempotencyKey)).isTrue();

        sendAndWait("chain.tx.seen", watchId.toString(), json);
        await().pollDelay(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(countPaymentsForTxHash(txHash))
                        .as("a redelivered, already-processed event must not create a second payment row")
                        .isEqualTo(1));
    }

    /** R14, scenario (a): {@code finalized} for a payment that was never seen - the "out of
     * order" case named literally in the task's own text. No invoice needs seeding (Phase 8
     * Kimi review, confirming self-review Finding 1): {@code applyFinalized}'s own "no known
     * payment" branch is reached by correlating {@code (chain, txHash)}, before any invoice
     * lookup is ever attempted. */
    @Test
    void shouldIgnoreFinalizedForAnUnknownPayment() throws SQLException {
        String txHash = "0x" + UUID.randomUUID();
        UUID watchId = UUID.randomUUID();
        UUID invoiceUuid = UUID.randomUUID();
        String idempotencyKey = CHAIN + ":" + txHash + ":finalized";

        sendAndWait("chain.tx.finalized", watchId.toString(),
                finalizedJson(idempotencyKey, watchId, invoiceUuid, txHash));

        // Phase 8 Kimi Finding 3: first prove the message was actually consumed (not just that
        // enough wall-clock time passed) before asserting the negative outcome - otherwise a
        // lost/never-delivered message would make this assertion pass vacuously.
        await().atMost(Duration.ofSeconds(40)).untilAsserted(() ->
                assertThat(processedEventRepository.existsById(idempotencyKey)).isTrue());
        assertThat(fetchPaymentState(txHash))
                .as("a finalized event with no prior seen must never create a payment row")
                .isNull();
    }

    /** R14, scenario (b): {@code reorged} for a payment that has already reached
     * {@code FINALIZED} - stale relative to current state, R14's own literal wording. */
    @Test
    void shouldIgnoreReorgedForAnAlreadyFinalizedPayment() throws SQLException {
        String txHash = "0x" + UUID.randomUUID();
        FinalizedPayment payment = finalizeARealPayment(txHash);
        UUID reorgInvoiceUuid = UUID.randomUUID(); // not read by applyReorged's own stale path
        String idempotencyKey = CHAIN + ":" + txHash + ":reorged";

        sendAndWait("chain.tx.reorged", payment.watchId().toString(),
                reorgedJson(idempotencyKey, payment.watchId(), reorgInvoiceUuid, txHash));

        await().atMost(Duration.ofSeconds(40)).untilAsserted(() ->
                assertThat(processedEventRepository.existsById(idempotencyKey)).isTrue());
        assertThat(fetchPaymentState(txHash))
                .as("a reorg of an already-finalized payment must leave its state unchanged")
                .isEqualTo("FINALIZED");
        assertThat(verificationRecordExistsForTriggerEventKey(idempotencyKey))
                .as("R14 requires the ignore to be recorded, not just silently dropped")
                .isTrue();
    }

    /** AC2, R14, named (keeps the exact name {@code package.md} §8 maps to R14 - Phase 8 Kimi
     * review's own recommendation, splitting the former three-scenario bundle into independent
     * methods for diagnostic granularity while keeping this one as the most interesting case).
     * {@code confirmed} for a payment that has already reached {@code FINALIZED} - the real
     * cross-topic race (Phase 4 Finding 6): {@code chain.tx.confirmed} and
     * {@code chain.tx.finalized} are separate topics with no cross-topic ordering guarantee, so
     * a confirmed event can genuinely arrive after finalization. Every same-event-type
     * staleness scenario (e.g. a lower confirmation count, or a second genuine reorg - the
     * identical limitation documented in {@code ChainTxReorged}'s own Phase 9 Finding 9) is
     * contractually unreachable via real, dedupe-respecting delivery, since every
     * {@code chain.tx.*} schema's own idempotency key is fixed per
     * {@code (chain, txHash, eventtype)} - this cross-topic scenario is the real,
     * end-to-end-testable proof of R14 instead. */
    @Test
    void shouldIgnoreOutOfOrderOrStaleChainEvents() throws SQLException {
        String txHash = "0x" + UUID.randomUUID();
        FinalizedPayment payment = finalizeARealPayment(txHash);
        UUID crossRaceInvoiceUuid = UUID.randomUUID(); // not read by applyConfirmed's own stale path
        String idempotencyKey = CHAIN + ":" + txHash + ":confirmed";

        sendAndWait("chain.tx.confirmed", payment.watchId().toString(),
                confirmedJson(idempotencyKey, payment.watchId(), crossRaceInvoiceUuid, txHash, 20));

        await().atMost(Duration.ofSeconds(40)).untilAsserted(() ->
                assertThat(processedEventRepository.existsById(idempotencyKey)).isTrue());
        assertThat(fetchPaymentState(txHash))
                .as("a confirmed event arriving after finalization must leave the state unchanged")
                .isEqualTo("FINALIZED");
        assertThat(verificationRecordExistsForTriggerEventKey(idempotencyKey))
                .as("R14 requires the ignore to be recorded, not just silently dropped")
                .isTrue();
    }

    /** AC3, O6 (reworded, Phase 4 Finding 6): proves the real listener wiring correctly reaches
     * {@code FINALIZED} via the full forward sequence with the real observed fields persisted,
     * including the real invoice-state transition (Phase 8 Kimi Finding 4) -
     * {@code InvoiceService.markOutcomeIfOpenOrExpired} (T13) is as much a part of
     * {@code onChainTxFinalized}'s own real wiring as the payment row's own fields. Not a claim
     * about proving Kafka's own cross-topic ordering guarantee, which is the broker's property,
     * not this consumer's. */
    @Test
    void shouldHandleTheFullForwardSequenceAndReachFinalized() throws SQLException {
        String txHash = "0x" + UUID.randomUUID();

        FinalizedPayment payment = finalizeARealPayment(txHash);

        try (Connection admin = adminConnection(); Statement statement = admin.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT amount, from_address, to_address, address_poisoning_flag "
                             + "FROM payments.payments WHERE tx_hash = '" + txHash + "'")) {
            assertThat(resultSet.next()).isTrue();
            assertThat(resultSet.getBigDecimal("amount")).isEqualByComparingTo("1000");
            assertThat(resultSet.getString("from_address")).isEqualTo("0xfrom");
            assertThat(resultSet.getString("to_address")).isEqualTo("0xdef");
            // Phase 11 Kimi Finding 1: addressPoisoningFlag is persisted in the same atomic
            // update as the other finalized fields - a regression dropping it would otherwise
            // go unnoticed by this test.
            assertThat(resultSet.getBoolean("address_poisoning_flag")).isFalse();
        }
        assertThat(fetchInvoiceState(payment.invoiceUuid())).isEqualTo("PAID");
    }
}
