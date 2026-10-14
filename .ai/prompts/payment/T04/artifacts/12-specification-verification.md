# payment · T04 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T04 — Outbox & EventTopics |
| **Consumes** | `artifacts/11-test-review-resolution.md` |

## Verdict: **PASS**

## Traceability matrix

| AC | Requirement | Implementation | Test coverage |
|---|---|---|---|
| AC1 | `OutboxEvent` maps exactly the real 7-column `outbox` table, no `idempotencyKey` | `events/OutboxEvent.java` — verified byte-for-byte against `V1__payments_baseline.sql` in Phase 6/7/8 | Indirectly — `SchemaBaselineMigrationIntegrationTest` (T02) covers the table shape; mapping itself exercised by every `OutboxPublisherTest`/`OutboxRelayTest` case |
| AC2 | `OutboxPublisher.publish` 4-param signature, serializes via `ObjectMapper`, saves in caller's transaction, null/blank guards, documented dup-row limitation | `events/OutboxPublisher.java` | `OutboxPublisherTest` — 9 cases: happy path, 4×null guard, 3×blank guard, unserializable-payload wrapping |
| AC3 | `EventTopics` routes all 4 aggregate types; unmapped throws `IllegalStateException` | `events/EventTopics.java` — verified verbatim against `design.md` §4c | `EventTopicsTest` — 4 parameterized mappings (named test `shouldRouteEachPublishedEventToItsPaymentsTopic`, R30) + unmapped/blank/null cases + exact-map-contents case (Phase 11 Gap 1) |
| AC4 | `OutboxRelay.relay()` batch-100 poll, Kafka send keyed by `aggregateId`, mark-published-after-success, continue-past-failure | `events/OutboxRelay.java` | `OutboxRelayTest` — 13 cases: success, failed send, interrupted, timeout, one-failure-doesn't-block-batch, all 4 routings, failed mark-published save, unroutable type, empty batch, batch-size-100 |
| AC5 | `KafkaProducerConfig` explicit `ProducerFactory<String,String>`/`KafkaTemplate<String,String>`, `acks=all`, `enable.idempotence=true` | `events/KafkaProducerConfig.java` | `KafkaProducerConfigTest` — explicit bean wins over `KafkaAutoConfiguration`, correct config values, single template bean |
| AC6 | `application.properties` has the relay-interval property; `PaymentServiceApplication` has `@EnableScheduling` | both files edited | `ApplicationPropertiesContentTest.outboxRelayIntervalIsConfigured` (exact value, Phase 11 Gap 2) + `T01SkeletonRegressionTest.applicationClassIsBareWithStandardMainMethod` (annotation present, count 3) |
| AC7 | `ClockConfig` — single `Clock.systemUTC()` bean | `common/ClockConfig.java` | `ClockConfigTest` (Phase 11 Gap 3) |

## Required Tests (frozen brief)

- `EventTopicsTest` named test `shouldRouteEachPublishedEventToItsPaymentsTopic` — present, passing. **Satisfied, and exceeded** (Phase 11 added the exact-map-contents guard beyond the brief's own minimum).

## Scope discipline

`git diff --stat 850961c8..HEAD` (T04's own full range) touches exactly the 9 files the frozen
brief's Files to Create/Modify lists (7 new production classes + `ClockConfig` + the 2 modified
files), nothing under `db/migration/`, no other `common`/`common.config` file, nothing under
`spec/`. Files NOT to Modify constraint held.

## Locked decisions

- **L8** (outbox for every publish) — `OutboxPublisher` is the only class that writes an
  `OutboxEvent`; `OutboxEventRepository` and the entity itself are package-private/protected,
  closing off any bypass. Held.
- **L12** (module boundaries) — `events` package owns `OutboxEvent` exclusively; no other
  package imports it. Held.

## Open items carried forward (not blockers for this task)

- Phase 7/8/9 finding 3 (`OutboxPublisher` has no runtime transaction-active guard) — disclosed,
  accepted, matches `crypto-service`'s own identical precedent. Not an AC2 violation (AC2 does
  not require a runtime guard, only that `publish` itself never opens its own transaction, which
  it doesn't).
- Phase 8/9 finding 4 (unroutable events re-log forever) — disclosed, accepted, already
  documented in `OutboxRelay`'s own comments.

## Test suite status

85/85 non-Docker-dependent tests passing (`mvn -pl services/payment -am test
-Dtest='!SchemaBaselineMigrationIntegrationTest'`). `SchemaBaselineMigrationIntegrationTest`
(17 tests, T02, Testcontainers) not run this session — Docker unavailable in this environment —
but unaffected by any T04 change (no migration touched) and last confirmed green in Phase 6
(full 68/68 suite, Docker up, before this phase's 17 new tests were added).

## Verdict rationale

All 7 acceptance criteria are implemented and test-covered (several beyond the frozen brief's
own minimum, per the genuine Phase 11 Kimi review). No LOCKED decision violated. No file outside
the frozen brief's own scope touched. The one disclosed limitation (AC2's documented duplicate-
row risk) is a named, accepted constraint of the frozen brief itself, not a defect. **PASS.**
Proceeding to Phase 13.
