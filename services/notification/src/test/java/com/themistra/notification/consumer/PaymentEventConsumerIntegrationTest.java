package com.themistra.notification.consumer;

import com.themistra.notification.delivery.UndeliveredEventLog;
import com.themistra.notification.routing.ExpiredParkedEvent;
import com.themistra.notification.routing.ParkedEventExpiryJob;
import com.themistra.notification.routing.PaymentRouting;
import com.themistra.notification.template.TemplateRenderer;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * T07: proves {@link PaymentEventConsumer}'s real listener wiring end to end. Real payment events
 * go through Kafka, are deduped, build the routing projection, and dispatch to the merchant.
 *
 * <p>Unlike the auth consumer tests, this class runs its own Kafka container instead of the shared
 * local broker, so it never depends on that broker's state. Every topic the service listens on is
 * created before the Spring context starts. A listener subscribed to a missing topic slows every
 * consumer in the group while the topic is auto-created.
 *
 * <p>Each captured dispatch is also rendered with the real {@link TemplateRenderer}. That proves
 * the version-2 templates are the ones chosen and that the consumer's keys fill every placeholder
 * (AC9).
 */
@Testcontainers
@SpringBootTest(properties = {
        "spring.kafka.consumer.group-id=payment-event-consumer-it",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "themistra.notification.link.base-url=https://app.example.test"})
@Import(PaymentEventConsumerIntegrationTest.SpyDispatcherConfig.class)
class PaymentEventConsumerIntegrationTest {

    private static final String NOTIFICATION_APP_PASSWORD = "it-notification-app-password";
    private static final List<String> TOPICS = List.of(
            "payments.invoice.created", "payments.payment.seen", "payments.payment.finalized",
            "payments.receipt.issued", "auth.email.requested", "auth.user.lifecycle");

    record DispatchCall(UUID accountUuid, String notificationKind, Map<String, String> eventData) {}

    @TestConfiguration
    static class SpyDispatcherConfig {
        static final CopyOnWriteArrayList<DispatchCall> CALLS = new CopyOnWriteArrayList<>();

        @Bean
        @Primary
        NotificationDispatcher spyDispatcher() {
            return (accountUuid, notificationKind, eventData) ->
                    CALLS.add(new DispatchCall(accountUuid, notificationKind, eventData));
        }
    }

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse("apache/kafka:3.8.0"));

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "notification_app");
        registry.add("spring.datasource.password", () -> NOTIFICATION_APP_PASSWORD);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
    }

    @BeforeAll
    static void migrateProvisionPasswordAndCreateTopics()
            throws SQLException, InterruptedException, ExecutionException, TimeoutException {
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS notifications");
            statement.execute("CREATE EXTENSION IF NOT EXISTS citext SCHEMA notifications");
        }
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas("notifications")
                .load()
                .migrate();
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            statement.execute("ALTER ROLE notification_app PASSWORD '" + NOTIFICATION_APP_PASSWORD + "'");
        }
        try (AdminClient kafkaAdmin = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
            kafkaAdmin.createTopics(TOPICS.stream().map(topic -> new NewTopic(topic, 1, (short) 1)).toList())
                    .all().get(30, TimeUnit.SECONDS);
        }
    }

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private TemplateRenderer templateRenderer;

    @Autowired
    private PaymentRouting paymentRouting;

    @Autowired
    private ParkedEventExpiryJob parkedEventExpiryJob;

    @Autowired
    private UndeliveredEventLog undeliveredEventLog;

    /** Routing facts never change, so the first write wins and a repeat write is a quiet no-op,
     * never a key violation that would roll back the listener's transaction. receipt.issued
     * re-records both routes, so this repeat happens on every normal payment. */
    @Test
    void routingWritesAreInsertOnceAndTheFirstWriterWins() {
        UUID invoice = UUID.randomUUID();
        UUID payment = UUID.randomUUID();
        UUID firstMerchant = UUID.randomUUID();

        paymentRouting.recordInvoice(invoice, firstMerchant, (short) 6);
        paymentRouting.recordInvoice(invoice, UUID.randomUUID(), null);
        paymentRouting.recordPayment(payment, invoice);
        paymentRouting.recordPayment(payment, UUID.randomUUID());

        assertThat(paymentRouting.findMerchantForInvoice(invoice)).contains(firstMerchant);
        assertThat(paymentRouting.findTokenDecimals(invoice)).contains(6);
        assertThat(paymentRouting.findInvoiceForPayment(payment)).contains(invoice);
    }

    /** S2 (open-issues sweep): a route first learned without decimals (receipt.issued carries
     * none) takes them from a later invoice.created. Known decimals are never overwritten, and the
     * merchant never changes. Runs as notification_app, so it also proves V17's column grant. */
    @Test
    void missingDecimalsAreFilledLaterButNeverOverwritten() {
        UUID invoice = UUID.randomUUID();
        UUID merchant = UUID.randomUUID();

        paymentRouting.recordInvoice(invoice, merchant, null);
        assertThat(paymentRouting.findTokenDecimals(invoice)).isEmpty();

        paymentRouting.recordInvoice(invoice, UUID.randomUUID(), (short) 6);
        assertThat(paymentRouting.findTokenDecimals(invoice)).contains(6);
        assertThat(paymentRouting.findMerchantForInvoice(invoice)).contains(merchant);

        paymentRouting.recordInvoice(invoice, merchant, (short) 18);
        paymentRouting.recordInvoice(invoice, merchant, null);
        assertThat(paymentRouting.findTokenDecimals(invoice)).contains(6);
    }

    @Test
    void paymentLifecycleIsConsumedRoutedAndDispatchedToTheMerchantExactlyOnce() throws SQLException {
        UUID merchant = UUID.randomUUID();
        UUID invoice = UUID.randomUUID();
        UUID payment = UUID.randomUUID();
        UUID receipt = UUID.randomUUID();
        String txHash = "0x" + "ab".repeat(32);

        String invoiceCreated = "{\"invoiceUuid\":\"" + invoice + "\",\"merchantAccountUuid\":\"" + merchant
                + "\",\"chain\":\"ethereum\",\"tokenContractAddress\":\"0x" + "11".repeat(20)
                + "\",\"tokenDecimals\":6,\"amount\":\"1500000\",\"payToAddress\":\"0x" + "22".repeat(20)
                + "\",\"reference\":null,\"expiresAt\":\"2026-11-01T00:00:00Z\"}";
        String paymentSeen = "{\"paymentUuid\":\"" + payment + "\",\"invoiceUuid\":\"" + invoice
                + "\",\"chain\":\"ethereum\",\"txHash\":\"" + txHash + "\",\"tokenContractAddress\":\"0x"
                + "11".repeat(20) + "\",\"confirmations\":1,\"occurredAt\":\"2026-10-19T10:00:00Z\"}";
        String paymentFinalized = "{\"paymentUuid\":\"" + payment + "\",\"chain\":\"ethereum\",\"txHash\":\""
                + txHash + "\",\"fromAddress\":\"0x" + "33".repeat(20) + "\",\"toAddress\":\"0x"
                + "22".repeat(20) + "\",\"tokenContractAddress\":\"0x" + "11".repeat(20)
                + "\",\"amount\":\"1500000\",\"confirmations\":12,\"addressPoisoningFlag\":false,"
                + "\"occurredAt\":\"2026-10-19T10:05:00Z\"}";
        // No merchantAccountUuid: proves the routed fallback through the real projection.
        String receiptIssued = "{\"invoiceUuid\":\"" + invoice + "\",\"paymentUuid\":\"" + payment
                + "\",\"receiptUuid\":\"" + receipt + "\",\"chain\":\"ethereum\",\"txHash\":\"" + txHash
                + "\",\"amount\":\"1500000\",\"tokenContractAddress\":\"0x" + "11".repeat(20)
                + "\",\"kmsKeyId\":\"alias/receipts\",\"receiptUri\":\"s3://receipts-bucket/" + receipt
                + ".json\",\"occurredAt\":\"2026-10-19T10:06:00Z\"}";

        // One producer key keeps the events in order only within a topic; the topics are separate,
        // so each event waits for the previous dispatch before the next one is sent.
        kafkaTemplate.send("payments.invoice.created", invoice.toString(), invoiceCreated);
        awaitDispatches(merchant, 1);
        kafkaTemplate.send("payments.payment.seen", invoice.toString(), paymentSeen);
        awaitDispatches(merchant, 2);
        kafkaTemplate.send("payments.payment.finalized", payment.toString(), paymentFinalized);
        awaitDispatches(merchant, 3);
        kafkaTemplate.send("payments.receipt.issued", receipt.toString(), receiptIssued);
        awaitDispatches(merchant, 4);

        // Redeliver every event; none may dispatch again.
        kafkaTemplate.send("payments.invoice.created", invoice.toString(), invoiceCreated);
        kafkaTemplate.send("payments.payment.seen", invoice.toString(), paymentSeen);
        kafkaTemplate.send("payments.payment.finalized", payment.toString(), paymentFinalized);
        kafkaTemplate.send("payments.receipt.issued", receipt.toString(), receiptIssued);
        await().atMost(Duration.ofSeconds(30)).untilAsserted(() ->
                assertThat(processedEventCount(invoice, payment, receipt)).isEqualTo(4));
        await().during(Duration.ofSeconds(5)).atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(callsFor(merchant)).hasSize(4));

        List<DispatchCall> calls = callsFor(merchant);
        assertThat(calls).extracting(DispatchCall::notificationKind)
                .containsExactly("invoice.created", "payment.seen", "payment.finalized", "receipt.issued");
        assertThat(calls).allSatisfy(call -> {
            assertThat(call.eventData()).containsEntry("invoiceUuid", invoice.toString())
                    .containsEntry("invoiceId", invoice.toString())
                    .doesNotContainKeys("receiptUri", "fromAddress", "toAddress");
            assertThat(call.eventData().values()).noneMatch(value -> value != null && value.startsWith("s3://"));
        });
        assertThat(calls.get(0).eventData()).containsEntry("amount", "1.5");
        assertThat(calls.get(2).eventData()).containsEntry("amount", "1.5").containsEntry("txHash", txHash);
        assertThat(calls.get(3).eventData()).containsEntry("receiptId", receipt.toString())
                .containsEntry("receiptUuid", receipt.toString());
        assertThat(calls.subList(0, 3)).allSatisfy(call ->
                assertThat(call.eventData()).doesNotContainKey("receiptUuid"));

        assertThat(scalar("SELECT merchant_account_uuid::text || ':' || token_decimals "
                + "FROM notifications.invoice_routes WHERE invoice_uuid = '" + invoice + "'"))
                .isEqualTo(merchant + ":6");
        assertThat(scalar("SELECT invoice_uuid::text FROM notifications.payment_routes "
                + "WHERE payment_uuid = '" + payment + "'")).isEqualTo(invoice.toString());

        for (DispatchCall call : calls) {
            for (String channel : List.of("EMAIL", "IN_APP")) {
                TemplateRenderer.RenderedMessage rendered =
                        templateRenderer.render(call.notificationKind(), channel, call.eventData());
                boolean receiptEmail = "receipt.issued".equals(call.notificationKind()) && "EMAIL".equals(channel);
                // FU-T07-2: only the receipt email moved to version 3, to carry the link.
                assertThat(rendered.version()).as("%s %s version", call.notificationKind(), channel)
                        .isEqualTo(receiptEmail ? 3 : 2);
                assertThat(rendered.body()).as("%s %s body", call.notificationKind(), channel)
                        .doesNotContain("{{", "s3://");
                if (receiptEmail) {
                    assertThat(rendered.body()).endsWith("https://app.example.test/app/receipts/" + receipt);
                } else {
                    assertThat(rendered.body()).doesNotContain("/receipts/");
                }
                // Kimi sweep test review, Gap 4 (2026-10-20): the invoice email's link, from a
                // consumed event rather than hand-built data.
                if ("invoice.created".equals(call.notificationKind()) && "EMAIL".equals(channel)) {
                    assertThat(rendered.body()).endsWith("https://app.example.test/app/invoices/" + invoice);
                } else {
                    assertThat(rendered.body()).doesNotContain("/invoices/");
                }
                if (rendered.subject() != null) {
                    assertThat(rendered.subject()).doesNotContain("{{");
                }
            }
        }
    }

    /** FU-T07-4 (closed 2026-10-19; this test pinned the old drop until then). A payment.seen
     * consumed before its invoice.created finds no merchant route. It is recorded as processed
     * and parked, then dispatched by invoice.created, right after the invoice's own notification.
     * The parked row is removed in that same transaction. */
    @Test
    void paymentSeenBeforeItsInvoiceIsParkedThenDispatchedWhenTheInvoiceArrives() throws SQLException {
        UUID merchant = UUID.randomUUID();
        UUID invoice = UUID.randomUUID();
        UUID payment = UUID.randomUUID();

        kafkaTemplate.send("payments.payment.seen", invoice.toString(), "{\"paymentUuid\":\"" + payment
                + "\",\"invoiceUuid\":\"" + invoice + "\",\"chain\":\"ethereum\",\"txHash\":\"0x"
                + "cd".repeat(32) + "\",\"tokenContractAddress\":\"0x" + "11".repeat(20)
                + "\",\"confirmations\":1,\"occurredAt\":\"2026-10-19T11:00:00Z\"}");
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() ->
                assertThat(scalar("SELECT event_key FROM notifications.processed_events WHERE event_key = "
                        + "'payment.seen:" + payment + "'")).isNotNull());

        kafkaTemplate.send("payments.invoice.created", invoice.toString(), "{\"invoiceUuid\":\"" + invoice
                + "\",\"merchantAccountUuid\":\"" + merchant + "\",\"chain\":\"ethereum\","
                + "\"tokenContractAddress\":\"0x" + "11".repeat(20) + "\",\"tokenDecimals\":6,"
                + "\"amount\":\"2000000\",\"payToAddress\":\"0x" + "22".repeat(20)
                + "\",\"reference\":null,\"expiresAt\":\"2026-11-01T00:00:00Z\"}");
        awaitDispatches(merchant, 2);
        await().during(Duration.ofSeconds(5)).atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(callsFor(merchant)).extracting(DispatchCall::notificationKind)
                        .containsExactly("invoice.created", "payment.seen"));
        assertThat(callsFor(merchant).get(1).eventData())
                .containsEntry("sourceEventKey", "payment.seen:" + payment)
                .containsEntry("txHash", "0x" + "cd".repeat(32));
        assertThat(parkedCount(invoice, payment)).as("the replayed event is no longer parked").isZero();

        // The route payment.seen carried was still recorded, so a later payment.finalized resolves.
        assertThat(scalar("SELECT invoice_uuid::text FROM notifications.payment_routes "
                + "WHERE payment_uuid = '" + payment + "'")).isEqualTo(invoice.toString());
    }

    /** FU-T07-4: payment.finalized names only its payment, so arriving first it is parked with no
     * invoice. payment.seen then records the payment route, but the invoice is still unknown, so
     * it parks too. invoice.created replays both in lifecycle order (seen before finalized, though
     * finalized arrived first), the finalized one found through the payment route. An unrelated
     * parked event stays parked. */
    @Test
    void eventsArrivingBeforeEveryRouteAreReplayedInLifecycleOrder() throws SQLException {
        UUID merchant = UUID.randomUUID();
        UUID invoice = UUID.randomUUID();
        UUID payment = UUID.randomUUID();
        UUID strayPayment = UUID.randomUUID();
        String txHash = "0x" + "ef".repeat(32);

        kafkaTemplate.send("payments.payment.finalized", strayPayment.toString(), finalizedJson(strayPayment, txHash));
        kafkaTemplate.send("payments.payment.finalized", payment.toString(), finalizedJson(payment, txHash));
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() ->
                assertThat(parkedCount(invoice, payment) + parkedCount(UUID.randomUUID(), strayPayment)).isEqualTo(2));
        kafkaTemplate.send("payments.payment.seen", invoice.toString(), "{\"paymentUuid\":\"" + payment
                + "\",\"invoiceUuid\":\"" + invoice + "\",\"chain\":\"ethereum\",\"txHash\":\"" + txHash
                + "\",\"tokenContractAddress\":\"0x" + "11".repeat(20)
                + "\",\"confirmations\":1,\"occurredAt\":\"2026-10-19T11:00:00Z\"}");
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() ->
                assertThat(parkedCount(invoice, payment)).isEqualTo(2));
        assertThat(callsFor(merchant)).isEmpty();

        kafkaTemplate.send("payments.invoice.created", invoice.toString(), "{\"invoiceUuid\":\"" + invoice
                + "\",\"merchantAccountUuid\":\"" + merchant + "\",\"chain\":\"ethereum\","
                + "\"tokenContractAddress\":\"0x" + "11".repeat(20) + "\",\"tokenDecimals\":6,"
                + "\"amount\":\"2000000\",\"payToAddress\":\"0x" + "22".repeat(20)
                + "\",\"reference\":null,\"expiresAt\":\"2026-11-01T00:00:00Z\"}");
        awaitDispatches(merchant, 3);

        assertThat(callsFor(merchant)).extracting(DispatchCall::notificationKind)
                .containsExactly("invoice.created", "payment.seen", "payment.finalized");
        assertThat(callsFor(merchant).get(2).eventData()).containsEntry("amount", "2");
        assertThat(parkedCount(invoice, payment)).isZero();
        assertThat(parkedCount(UUID.randomUUID(), strayPayment)).as("an unrelated event stays parked").isEqualTo(1);
    }

    /** Expiry (open-issues sweep, 2026-10-20), on real Postgres as notification_app: only events
     * parked before the cutoff are removed and reported; a newer one stays parked and replayable. */
    @Test
    void expiryRemovesOnlyEventsParkedBeforeTheCutoff() throws SQLException {
        UUID oldPayment = UUID.randomUUID();
        UUID newPayment = UUID.randomUUID();
        paymentRouting.park("payment.finalized:" + oldPayment, "payment.finalized", "{}", null, oldPayment);
        paymentRouting.park("payment.finalized:" + newPayment, "payment.finalized", "{}", null, newPayment);
        backdateParked("payment.finalized:" + oldPayment, "8 days");

        List<ExpiredParkedEvent> expired = paymentRouting.expireParkedBefore(Instant.now().minus(Duration.ofDays(7)));

        assertThat(expired).extracting(ExpiredParkedEvent::eventKey).contains("payment.finalized:" + oldPayment)
                .doesNotContain("payment.finalized:" + newPayment);
        assertThat(expired).filteredOn(event -> event.eventKey().equals("payment.finalized:" + oldPayment))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.kind()).isEqualTo("payment.finalized");
                    assertThat(event.parkedAt()).isBefore(Instant.now().minus(Duration.ofDays(7)));
                });
        assertThat(parkedCount(UUID.randomUUID(), oldPayment)).isZero();
        assertThat(parkedCount(UUID.randomUUID(), newPayment)).isEqualTo(1);
    }

    /** Kimi sweep review, Finding 7 (2026-10-20): the real job, against real Postgres, leaves one
     * durable EXPIRED row per removed event (V19) and none for an event still within its age. The
     * row names no channel, recipient, account or template, and never carries the payload. */
    @Test
    void theExpiryJobLeavesADurableExpiredRecordInTheDeliveryLog() throws SQLException {
        UUID oldPayment = UUID.randomUUID();
        UUID newPayment = UUID.randomUUID();
        String oldKey = "payment.seen:" + oldPayment;
        String newKey = "payment.seen:" + newPayment;
        String payload = "{\"secretMarker\":\"" + UUID.randomUUID() + "\"}";
        paymentRouting.park(oldKey, "payment.seen", payload, null, oldPayment);
        paymentRouting.park(newKey, "payment.seen", payload, null, newPayment);
        backdateParked(oldKey, "8 days");
        // The scheduler already ran the job once at startup, and its ShedLock row is held for at
        // least 10s, during which ShedLock silently skips any call, this one included. The row is
        // expired, not deleted: ShedLock remembers it exists and only ever UPDATEs it afterwards.
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            statement.execute("UPDATE notifications.shedlock SET lock_until = now() - interval '1 second' "
                    + "WHERE name = 'parked-event-expiry'");
        }

        parkedEventExpiryJob.expire();

        assertThat(parkedCount(UUID.randomUUID(), oldPayment)).isZero();
        assertThat(parkedCount(UUID.randomUUID(), newPayment)).isEqualTo(1);
        assertThat(scalar("SELECT count(*)::text FROM notifications.delivery_log WHERE source_event_key = '"
                + newKey + "'")).isEqualTo("0");
        try (Connection admin = adminConnection();
             Statement statement = admin.createStatement();
             ResultSet row = statement.executeQuery("SELECT outcome, channel, recipient, account_uuid, "
                     + "template_name, template_version, attempt, error_detail FROM notifications.delivery_log "
                     + "WHERE source_event_key = '" + oldKey + "'")) {
            assertThat(row.next()).as("one delivery_log row for the expired event").isTrue();
            assertThat(row.getString("outcome")).isEqualTo("EXPIRED");
            assertThat(row.getString("channel")).isNull();
            assertThat(row.getString("recipient")).isNull();
            assertThat(row.getString("account_uuid")).isNull();
            assertThat(row.getString("template_name")).isNull();
            assertThat(row.getObject("template_version")).isNull();
            assertThat(row.getInt("attempt")).isEqualTo(1);
            assertThat(row.getString("error_detail"))
                    .startsWith("payment.seen parked at ")
                    .contains("expired after 168h", "never notified")
                    .doesNotContain("secretMarker");
            assertThat(row.next()).as("exactly one row").isFalse();
        }
    }

    /** The record is only ever written inside the deleting transaction (MANDATORY), so it can never
     * exist without the delete, or the delete without it. */
    @Test
    void anExpiredRecordCannotBeWrittenOutsideATransaction() {
        assertThatThrownBy(() -> undeliveredEventLog.recordExpired("payment.seen:" + UUID.randomUUID(),
                "payment.seen", Instant.now(), 168))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    private static void backdateParked(String eventKey, String age) throws SQLException {
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement()) {
            statement.execute("UPDATE notifications.parked_payment_events SET parked_at = now() - interval '"
                    + age + "' WHERE event_key = '" + eventKey + "'");
        }
    }

    private static String finalizedJson(UUID payment, String txHash) {
        return "{\"paymentUuid\":\"" + payment + "\",\"chain\":\"ethereum\",\"txHash\":\"" + txHash
                + "\",\"fromAddress\":\"0x" + "33".repeat(20) + "\",\"toAddress\":\"0x" + "22".repeat(20)
                + "\",\"tokenContractAddress\":\"0x" + "11".repeat(20) + "\",\"amount\":\"2000000\","
                + "\"confirmations\":12,\"addressPoisoningFlag\":false,\"occurredAt\":\"2026-10-19T10:59:00Z\"}";
    }

    private static int parkedCount(UUID invoice, UUID payment) throws SQLException {
        return Integer.parseInt(scalar("SELECT count(*)::text FROM notifications.parked_payment_events "
                + "WHERE invoice_uuid = '" + invoice + "' OR payment_uuid = '" + payment + "'"));
    }

    private static void awaitDispatches(UUID merchant, int expected) {
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() ->
                assertThat(callsFor(merchant)).hasSize(expected));
    }

    private static List<DispatchCall> callsFor(UUID merchant) {
        return SpyDispatcherConfig.CALLS.stream().filter(call -> merchant.equals(call.accountUuid())).toList();
    }

    private static int processedEventCount(UUID invoice, UUID payment, UUID receipt) throws SQLException {
        String count = scalar("SELECT count(*)::text FROM notifications.processed_events WHERE event_key IN ("
                + "'invoice.created:" + invoice + "', 'payment.seen:" + payment + "', "
                + "'payment.finalized:" + payment + "', 'receipt.issued:" + receipt + "')");
        return Integer.parseInt(count);
    }

    private static String scalar(String sql) throws SQLException {
        try (Connection admin = adminConnection();
             Statement statement = admin.createStatement();
             ResultSet resultSet = statement.executeQuery(sql)) {
            return resultSet.next() ? resultSet.getString(1) : null;
        }
    }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }
}
