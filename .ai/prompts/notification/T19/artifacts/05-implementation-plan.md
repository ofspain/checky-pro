# notification · T19 · Phase 5 — Implementation Plan

One new file, `DeliveryLogDisputeGradeIntegrationTest.java`, in `com.themistra.notification.delivery`.
Frozen in Phase 4; this plan adds only the concrete shape.

## Verified facts this plan relies on

- `RetryScheduler.processOne(DeliveryRetry)` is package-private (`RetryScheduler.java:69`) and does
  **not** check `nextAttemptAt`. Due-ness is enforced only by `sweep()`'s query. So the test may replay
  a not-yet-due row directly, and never needs to wait on backoff.
- `processOne` **deletes** the `delivery_retry` row on a terminal outcome. Therefore every assertion
  in this test is on `delivery_log`, never on `delivery_retry`.
- `DeliveryRetryRepository` has no source-key finder, only
  `findByNextAttemptAtLessThanEqualOrderByNextAttemptAtAscIdAsc(Instant)`. The test locates its row by
  `findAll()` filtered on `getSourceEventKey()`. Acceptable in a test; not production code.
- `channel_preferences` columns: `account_uuid`, `category`, `channel`, `enabled`, `updated_at`
  (NOT NULL). The insert shape is the one `DeliveryOrchestratorIntegrationTest.insertChannelPreference`
  already uses, reused verbatim except for the `updated_at` default, which the table supplies.
- `invoice.created` maps to PAYMENT (`DeliveryOrchestrator.java:101`).
- `notification_app` holds only `INSERT, SELECT` on `delivery_log` (`V2:40`). Preference rows are set
  through the admin connection, as the sibling test does.

## File to create

### `services/notification/src/test/java/com/themistra/notification/delivery/DeliveryLogDisputeGradeIntegrationTest.java`

Structure (mirrors `DeliveryOrchestratorIntegrationTest`'s setup exactly):

- `@Testcontainers`, `@SpringBootTest`, `@Import(ControllableEmailTransportConfig.class)`
- Constants: `NOTIFICATION_APP_PASSWORD`, datasource triple, the `999999` scheduler-interval override.
- `@Container POSTGRES` (postgres:16-alpine); `@BeforeAll migrateAndProvisionPassword()` identical
  to the sibling test (schema, citext, Flyway, `ALTER ROLE notification_app PASSWORD`).
- Nested `ControllableEmailTransport` and its `@TestConfiguration`, copied from the sibling (a
  test-local copy, not shared, so the two classes stay independent). `failNextAttempts(n)` drives
  transient failures.
- `@BeforeEach`: clear `FakeEmailTransport` and reset the controllable transport to zero failures.
- Helpers: `adminConnection()`, `insertChannelPreference(...)` (copied shape),
  `deliveryLogRowsFor(sourceEventKey)` (JDBC read of `channel, outcome, template_version, attempt,
  recipient, created_at, source_event_key`).
- Autowired: `DeliveryOrchestrator`, `RetryScheduler`, `DeliveryRetryRepository`,
  `DeliveryLogRepository`, `ControllableEmailTransport`, `DataSource` as needed for `notification_app`
  raw SQL.

### The four test methods

1. **`deliveryLogRejectsUpdateAndDeleteAsNotificationAppRole`** (AC1). Insert one real row via
   `dispatch`. Then open a JDBC connection as `notification_app` with the real password and run
   `UPDATE notifications.delivery_log SET outcome = 'X' WHERE id = ?` and
   `DELETE FROM notifications.delivery_log WHERE id = ?`. Each must throw a `PSQLException` whose
   `getSQLState()` is `42501`. Then confirm the row is unchanged via `deliveryLogRowsFor`.

2. **`retryChainAppendsMultipleRowsForTheSameSourceEventKey`** (AC2, AC3). Dispatch one
   `verify_email` event with EMAIL forced to fail once (`failNextAttempts(1)`). The first attempt
   writes a `FAILED` EMAIL row and a `delivery_retry` row. Locate that retry row by source key and
   call `retryScheduler.processOne(row)`. Assert the rows for that key now include both the `FAILED`
   attempt and a `SENT` attempt, both carrying the same `source_event_key`, distinct `attempt` values,
   recipient, channel, timestamp, and a non-null `template_version` on the rendered rows.

3. **`suppressedChannelLeavesASuppressedDeliveryLogRow`** (AC4). Insert a `channel_preferences` row
   disabling `EMAIL` for category `PAYMENT`. Dispatch `invoice.created`. Assert exactly one EMAIL row
   with outcome `SUPPRESSED` for that source key, and that IN_APP still produced its own row. A
   suppression that leaves no row would fail this assertion.

4. **`noApplicationCodeUpdatesOrDeletesDeliveryLogRows`** (AC5). Static inspection, recorded in the
   implementation notes, reinforced by AC1. Implemented as a test that scans `src/main/java` for
   `delivery_log` mutation SQL and for any `delete`/`update` call on `DeliveryLogRepository`, failing
   if any appears. Dispatch one event first so the test also confirms the log is written.

## Files to modify

None.

## Execution order

1. Write the class with the setup, helpers, and nested transport.
2. Run `mvn -pl services/notification test -Dtest=DeliveryLogDisputeGradeIntegrationTest` and make
   it green. Each assertion must fail for the stated reason before passing, so confirm at least one
   deliberate failure per method (for example, a wrong expected outcome) to show the assertion is live.
3. Full suite: `mvn -pl services/notification clean verify`.
