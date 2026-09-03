# crypto · T04 · Phase 10 — Test Generation

Test-only phase. No production code changed beyond one necessary fix to a pre-existing T02 test (see
"Regression found and fixed" below) — `mvn -pl services/crypto -am compile` output is unchanged from
Phase 9's. 6 new test files, 1 pre-existing test file corrected, all mapped below to the frozen
brief's acceptance criteria. **25/25 Docker-independent tests passing**; 2 Testcontainers-based test
classes (7 tests) were written but could not be run in this environment — Docker is unavailable
(confirmed via `docker ps`), same limitation as T03 and as Phase 6's own implementation notes.

## Regression found and fixed (not new test authorship, but necessary)

`ChainBaselineMigrationIntegrationTest` (T02) asserted `outbox` was an **ungranted** table
(`UNGRANTED_TABLES`, asserting `SELECT` is denied) and that exactly migrations `"1", "2"` succeeded.
Both assertions are now false once `V3__crypto_app_outbox_grant.sql` exists on the classpath — Flyway's
`migrate()` with no target picks up every pending migration, so T02's own `@BeforeAll` now applies
`V3` too. Neither Phase 7 (self-review) nor Phase 8 (Kimi's independent review) caught this. Fixed:
- Removed `"outbox"` from `UNGRANTED_TABLES` (its own access is now verified by the new
  `OutboxGrantMigrationIntegrationTest` instead, including that `DELETE` is still denied).
- Updated `bothMigrationsAreRecordedAsSuccessfulInFlywayHistory` → renamed
  `allMigrationsAreRecordedAsSuccessfulInFlywayHistory`, asserting `"1", "2", "3"`.

This is flagged prominently because it's the kind of regression that would have silently broken
`mvn -pl services/crypto verify` (task 27's own gate) without anyone having touched the test file's
logic — a direct, necessary consequence of this task's own schema change, not scope creep.

## Files created

- `services/crypto/src/test/java/com/themistra/crypto/events/EventTopicsTest.java` — 6 tests.
- `services/crypto/src/test/java/com/themistra/crypto/events/OutboxPublisherTest.java` — 6 tests.
- `services/crypto/src/test/java/com/themistra/crypto/events/OutboxRelayTest.java` — 7 tests.
- `services/crypto/src/test/java/com/themistra/crypto/events/OutboxTransactionIntegrationTest.java` —
  2 tests (needs Docker).
- `services/crypto/src/test/java/com/themistra/crypto/OutboxGrantMigrationIntegrationTest.java` — 3
  tests (needs Docker), top-level package mirroring `ChainBaselineMigrationIntegrationTest`.

## Files modified

- `services/crypto/src/test/java/com/themistra/crypto/ChainBaselineMigrationIntegrationTest.java` —
  the regression fix above.

## Test manifest

| Test | AC / Requirement | Notes |
|---|---|---|
| `EventTopicsTest.shouldRouteEachChainEventToItsTopic` (×5, parameterized) | **Named test** (package.md §8) → R26, AC1–AC5 | |
| `EventTopicsTest.unmappedAggregateTypeFailsLoudRatherThanGuessing` | R26, AC6 | |
| `OutboxPublisherTest.publishSerializesPayloadAndSavesEventWithGivenMetadataIncludingIdempotencyKey` | AC7, AC8 | Also asserts `createdAt` equals the fixed `Clock`'s instant (Phase 9 Finding 4) |
| `OutboxPublisherTest.null*Throws` (×5) | AC7, amendment #12 | One per required parameter |
| `OutboxRelayTest.successfulSendMarksEventPublishedAndPersistsIt` | AC10 | |
| `OutboxRelayTest.failedSendLeavesEventUnpublishedForRetry` | AC10 | |
| `OutboxRelayTest.interruptedSendLeavesEventUnpublishedAndRestoresInterruptFlag` | Phase 9 Finding 3 | Mocks the returned `CompletableFuture` to throw `InterruptedException` from `.get(...)`; asserts the flag is restored |
| `OutboxRelayTest.savingPublishedStateFailsLeavesEventUnpublishedForRetry` | Phase 9 Finding 3 | `DataAccessException` from the post-send `repository.save` |
| `OutboxRelayTest.unroutableAggregateTypeIsSkippedWithoutThrowingOrSaving` | AC6, AC10 | |
| `OutboxRelayTest.emptyBatchIsANoOp` | AC10 | |
| `OutboxRelayTest.pollsWithABatchSizeOf100` | Phase 3 amendment #10 | `Pageable` captor, asserts `getPageSize() == 100` |
| `OutboxTransactionIntegrationTest.publishInsideARolledBackTransactionPersistsNoRow` | **AC11** (amendment #5) | Real Testcontainers Postgres + real `@Transactional`; needs Docker |
| `OutboxTransactionIntegrationTest.publishInsideACommittedTransactionPersistsTheRow` | AC11 (positive counterpart) | Needs Docker |
| `OutboxGrantMigrationIntegrationTest.cryptoAppCanInsertIntoOutboxAndTheGeneratedIdIsALong` | **AC9, AC12** | Needs Docker |
| `OutboxGrantMigrationIntegrationTest.cryptoAppCanUpdatePublishedAtOnItsOwnRow` | AC9 | Needs Docker |
| `OutboxGrantMigrationIntegrationTest.cryptoAppStillCannotDeleteFromOutbox` | AC9 (negative proof — grant is exactly `INSERT, SELECT, UPDATE`, not wider) | Needs Docker |

**AC1–AC12: all covered** — AC9/AC11/AC12 by tests written but not run in this environment (Docker
unavailable); everything else run and green.

## Negative-proof (mutation testing)

Per this codebase's established convention, ran a real mutation against the highest-value new logic
from Phase 9's fixes (the `InterruptedException` handling, since it's genuinely new Java-concurrency
correctness logic, not just a mirror of auth's precedent):

1. Removed `Thread.currentThread().interrupt()` from `OutboxRelay.relayOne`'s `InterruptedException`
   catch block.
2. Re-ran `OutboxRelayTest` alone: **1 test failed** —
   `interruptedSendLeavesEventUnpublishedAndRestoresInterruptFlag`, exactly the test asserting that
   behavior, with a clean `Expecting value to be true but was false` message.
3. Reverted via `diff` against a pre-mutation backup (confirmed byte-identical); re-ran the full
   Docker-independent suite: 25/25 green again.

## Known limitations (flagged, not hidden)

- **`OutboxTransactionIntegrationTest` and `OutboxGrantMigrationIntegrationTest` (7 tests total) could
  not be executed in this environment.** Docker is unavailable (`docker ps` fails). Both compile
  cleanly and were written against the same established Testcontainers patterns
  `ChainBaselineMigrationIntegrationTest` (T02) already proved work in this repository — real risk is
  low, but AC9/AC11/AC12 remain **reasoned-correct, not empirically proven**, exactly the same posture
  T03 and Phase 6 of this task already disclosed for their own Docker-dependent pieces.
- `OutboxTransactionIntegrationTest` deliberately builds a narrow, hand-assembled Spring context
  (plain `@Configuration`, no component scanning) rather than booting the full
  `CryptoServiceApplication`, specifically to avoid starting `OutboxRelay`'s `@Scheduled` polling
  and `KafkaProducerConfig`'s beans against a Kafka broker that isn't running in the test — this is a
  deliberate scope-narrowing choice, not an oversight, documented in the test class's own Javadoc.
- No test exercises the `ExecutionException`/`TimeoutException` branch of `OutboxRelay.relayOne`
  under an actual 30-second timeout — doing so would require either a real 30-second wait (unacceptable
  for a unit test) or a design change to make the timeout injectable, which wasn't part of Phase 9's
  approved fix. `ExecutionException` is exercised via `failedSendLeavesEventUnpublishedForRetry`
  (an already-failed future, same catch block), which covers the branch's actual logic; only the
  JDK-guaranteed fact that `.get(timeout, unit)` throws `TimeoutException` on expiry — not this
  codebase's own logic — goes unexercised.
