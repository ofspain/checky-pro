# notification · T17 · Phase 5 — Implementation Plan

One file, mirroring the exact Testcontainers/Flyway/admin-connection boilerplate
`AuthEventConsumerIntegrationTest` and `DeliveryOrchestratorIntegrationTest` already establish —
verified directly against both files, reused verbatim where the pattern is identical, not
reinvented.

## A real-behavior check made before writing any code

`PreferenceResolver`'s own `DEFAULTS` map (`PreferenceResolver.java:30-36`) resolves
`SECURITY:EMAIL` **and** `SECURITY:IN_APP` both to `true` when no `channel_preference` row exists
at all. `verify_email`'s category is `SECURITY` (`DeliveryOrchestrator.java:98`). So the test needs
**zero** `channel_preference` setup — the simplest possible state (no preference rows) already
produces the two-channel dispatch Finding #4 predicted; inserting an explicit "enabled" row would
be redundant, not clearer.

`email.verify`'s own seed body (`V3__seed_launch_templates.sql:16-17`) references `{{displayName}}`,
which is permanently `null` in production today (T11 Kimi Phase 8 Finding #2 — no consumed event
carries it). This is the real, permanent default this task's own test exercises as-is; not a gap
this task introduces or needs to work around.

## File to create

### `src/test/java/com/themistra/notification/delivery/VerifyEmailRedeliveryIntegrationTest.java`

```java
package com.themistra.notification.delivery;

@Testcontainers
@SpringBootTest(properties = "spring.kafka.consumer.group-id=verify-email-redelivery-it")
class VerifyEmailRedeliveryIntegrationTest {

    private static final String NOTIFICATION_APP_PASSWORD = "it-notification-app-password";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = ...;          // identical to both precedents

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        // datasource triple, identical to both precedents, PLUS:
        registry.add("themistra.notification.retry.scheduler-interval-seconds", () -> "999999");
    }

    @BeforeAll
    static void migrateAndProvisionPassword() throws SQLException { ... }  // identical to both precedents

    @Autowired private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired private DeliveryLogRepository deliveryLogRepository;        // same package - direct injection
    @Autowired private FakeEmailTransport fakeEmailTransport;

    @BeforeEach
    void clearFakeEmailTransport() {
        fakeEmailTransport.clear();
    }

    private static Connection adminConnection() throws SQLException { ... } // identical to both precedents

    private static boolean processedEventExists(String eventKey) throws SQLException {
        // JDBC SELECT 1 FROM notifications.processed_events WHERE event_key = ? - mirrors
        // AuthEventConsumerIntegrationTest's own JDBC-for-contact-projection pattern (Finding #2)
    }

    @Test
    void verifyEmailRedeliveryProducesExactlyOneEmailAndNoDuplicateDeliveryLogRows() {
        UUID accountUuid = UUID.randomUUID();
        String occurredAt = Instant.parse("2026-01-01T00:00:00Z").toString();
        String eventKey = accountUuid + ":verify_email:" + occurredAt;
        String json = "{\"accountUuid\":\"" + accountUuid + "\",\"purpose\":\"verify_email\","
                + "\"token\":\"raw-token-e2e\",\"email\":\"e2e@example.com\","
                + "\"occurredAt\":\"" + occurredAt + "\"}";

        kafkaTemplate.send("auth.email.requested", accountUuid.toString(), json);

        // AC1 + AC3 (first delivery)
        await().atMost(Duration.ofSeconds(40)).untilAsserted(() ->
                assertThat(processedEventExists(eventKey)).isTrue());
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() ->
                assertThat(fakeEmailTransport.sentMessages())             // exact accessor name: Phase 6's
                        .hasSize(1));                                     // own job to confirm against source
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() ->
                assertThat(deliveryLogRepository.findByAccountUuid(accountUuid))  // or equivalent query -
                        .hasSize(2)                                               // Phase 6 confirms exact
                        .allSatisfy(log -> assertThat(log.getOutcome()).isEqualTo("SENT")));

        // AC2 (redelivery)
        kafkaTemplate.send("auth.email.requested", accountUuid.toString(), json);
        await().pollDelay(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(fakeEmailTransport.sentMessages()).hasSize(1);
            assertThat(deliveryLogRepository.findByAccountUuid(accountUuid)).hasSize(2);
        });
    }
}
```

Exact accessor/query method names (`FakeEmailTransport`'s own capture-list getter,
`DeliveryLogRepository`'s own account-scoped finder — added if it doesn't already exist, or a
direct JDBC query if adding a repository method is out of this task's own minimal scope) are
confirmed against real source during Phase 6, not guessed here — this plan fixes the test's own
shape and assertions, not every private method signature.

## Files to modify

None expected. If `DeliveryLogRepository` has no existing account-scoped finder method, Phase 6
decides between adding one (package-private, matching every sibling repository's own existing
convention) or using a direct JDBC query (matching `AuthEventConsumerIntegrationTest`'s own
established JDBC fallback pattern) — whichever avoids touching production code for a single test's
sake, preferring JDBC if an acceptable one-line addition doesn't already fit the repository's own
existing method shapes.

## Execution order

1. Read `DeliveryLogRepository` and `FakeEmailTransport` in full first, to confirm their exact real
   method names before writing a single assertion against them (not assumed from the sketch above).
2. Write `VerifyEmailRedeliveryIntegrationTest.java` per the skeleton above.
3. Run it alone (`mvn -pl services/notification test -Dtest=VerifyEmailRedeliveryIntegrationTest`),
   confirming both the first-delivery and redelivery assertions pass against the real chain.
4. Full suite: `mvn -pl services/notification clean verify`.
