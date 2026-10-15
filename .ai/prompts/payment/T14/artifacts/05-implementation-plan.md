# payment · T14 · Phase 5 — Implementation Plan

Consumes: `artifacts/04-frozen-task-brief.md` (`STATUS: FROZEN`)

## Files to Create

- `services/payment/src/test/java/com/themistra/payment/consumer/ChainEventConsumerIdempotencyAndOrderingIntegrationTest.java`

## Class skeleton

```java
package com.themistra.payment.consumer;

import com.themistra.payment.common.CryptoClient;
import com.themistra.payment.invoice.InvoiceService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.kafka.consumer.group-id=payment-chain-idempotency-it",
                "spring.kafka.consumer.auto-offset-reset=earliest",
                "spring.task.scheduling.enabled=false"
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

    // --- seeding ---

    private UUID seedInvoice(UUID watchId) {
        UUID invoiceUuid = UUID.randomUUID();
        invoiceService.create(invoiceUuid, MERCHANT_ACCOUNT_UUID, CHAIN, TOKEN_CONTRACT_ADDRESS,
                (short) 18, new BigDecimal("1000"), "0xdef", null,
                Instant.now().plusSeconds(3600), watchId);
        return invoiceUuid;
    }

    // --- raw JSON builders (Phase 4 Finding 5: amount as a quoted string, never a JSON number) ---

    private static String seenJson(String idempotencyKey, UUID watchId, UUID invoiceUuid, String txHash) {
        return """
                {"idempotencyKey":"%s","watchId":"%s","invoiceUuid":"%s","chain":"%s",
                 "txHash":"%s","tokenContractAddress":"%s","confirmations":1,
                 "occurredAt":"2026-07-13T00:00:00Z"}
                """.formatted(idempotencyKey, watchId, invoiceUuid, CHAIN, txHash, TOKEN_CONTRACT_ADDRESS);
    }

    private static String confirmedJson(String idempotencyKey, UUID watchId, UUID invoiceUuid, String txHash, int confirmations) {
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

    // --- raw JDBC verification (consumer package has no entity access, L12) ---

    private static String fetchPaymentState(String txHash) throws SQLException {
        try (Connection admin = adminConnection(); Statement statement = admin.createStatement();
             ResultSet rs = statement.executeQuery(
                     "SELECT state FROM payments.payments WHERE tx_hash = '" + txHash + "'")) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private static int countPaymentsForTxHash(String txHash) throws SQLException { /* COUNT(*) */ }

    private static Connection adminConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    // --- AC1 (R13, named) ---
    @Test
    void shouldDedupeDuplicateChainEventsByEventKey() throws SQLException { /* see Required Tests */ }

    // --- AC2 (R14, named, 3 scenarios) ---
    @Test
    void shouldIgnoreOutOfOrderOrStaleChainEvents() throws SQLException { /* see Required Tests */ }

    // --- AC3 (O6, reworded) ---
    @Test
    void shouldHandleTheFullForwardSequenceAndReachFinalized() throws SQLException { /* see Required Tests */ }
}
```

## Test bodies (Phase 6's own job to write verbatim; sketched here for Phase 5's own planning completeness)

### `shouldDedupeDuplicateChainEventsByEventKey` (AC1)

1. Seed an invoice with a fresh `watchId`.
2. Build one `seenJson(...)` for a fresh `txHash` — same idempotency key both times (the schema's
   own fixed `chain:txHash:seen` format already guarantees this for an identical payload).
3. `kafkaTemplate.send("chain.tx.seen", watchId.toString(), json)` — send once.
4. `await().atMost(Duration.ofSeconds(40)).untilAsserted(() -> assertThat(fetchPaymentState(txHash)).isEqualTo("SEEN"))`.
5. Send the **identical** JSON again.
6. `await().pollDelay(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(countPaymentsForTxHash(txHash)).isEqualTo(1))`
   — exactly one `Payment` row, the redelivery was a genuine no-op, not two rows.
7. Also assert `processedEventRepository.existsById(idempotencyKey)` is `true` (the dedupe row
   itself).

### `shouldIgnoreOutOfOrderOrStaleChainEvents` (AC2, three scenarios, Decision 2 + Finding 6)

**(a) `finalized` for an unknown payment:**
1. Fresh `txHash`, no prior `seen`/invoice needed (the event is unknown-payment by construction).
2. Send `finalizedJson(...)`.
3. `await().pollDelay(3s).atMost(10s).untilAsserted(() -> assertThat(fetchPaymentState(txHash)).isNull())`
   — no payment row was ever created.

**(b) `reorged` for an already-`FINALIZED` payment:**
1. Seed invoice, drive a real payment through `seen` → `confirmed` → `finalized` (reusing the
   same helper sequence AC3 uses) until `fetchPaymentState(txHash)` is `"FINALIZED"`.
2. Send `reorgedJson(...)` for the same `txHash`.
3. `await().pollDelay(3s).atMost(10s).untilAsserted(() -> assertThat(fetchPaymentState(txHash)).isEqualTo("FINALIZED"))`
   — unchanged.

**(c) `confirmed` for an already-`FINALIZED` payment (Finding 6's own cross-topic race):**
1. Same finalized-payment setup as (b), a **different** `txHash`.
2. Send `confirmedJson(...)` with a confirmation count for that now-finalized `txHash`.
3. Same unchanged-state assertion as (b).

### `shouldHandleTheFullForwardSequenceAndReachFinalized` (AC3)

1. Seed invoice with a fresh `watchId`.
2. Send `seenJson(...)` → await `"SEEN"`.
3. Send `confirmedJson(...)` → await `"CONFIRMING"`.
4. Send `finalizedJson(...)` → await `"FINALIZED"`.
5. Assert the row's own `amount`/`from_address`/`to_address` (raw JDBC) match the finalized
   event's own values — proving the real listener wiring persisted the real observed fields, not
   just the state label.

## Public/Private Methods

All `private` (test-internal helpers) or package-private test methods — no production API
surface at all, consistent with this task's own "zero production code" scope.

## Entities/Repositories/Services Used

`InvoiceService` (public, seeding), `ProcessedEventRepository` (package-private, same-package
dedupe verification), `KafkaTemplate<String, String>` (`KafkaProducerConfig`'s own
`outboxKafkaTemplate` bean), `CryptoClient` (`@MockBean`, replaced entirely). No `Payment`/
`Invoice` entity — raw JDBC only (L12).

## Execution Order

1. Write the class skeleton (containers, properties, seeding helpers, JSON builders, cleanup).
2. `shouldDedupeDuplicateChainEventsByEventKey` (AC1) — simplest, single-topic, validates the
   whole harness works before building the more complex multi-event tests.
3. `shouldHandleTheFullForwardSequenceAndReachFinalized` (AC3) — establishes the
   seen→confirmed→finalized helper sequence the two cross-topic AC2 scenarios then reuse.
4. `shouldIgnoreOutOfOrderOrStaleChainEvents` (AC2, all three scenarios).
5. Run the class for real against the now-running local stack; iterate on any real
   timing/flake issue Phase 6 surfaces (expected, given this is new test infrastructure).

Every file/decision above traces to the frozen brief's own Files/AC sections — no file beyond
the one listed is touched, consistent with this task's own zero-production-code scope.
