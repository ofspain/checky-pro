# payment · T04 · Phase 11 — Test Review Resolution

Consumes `artifacts/11-test-review.md` (Kimi 2.7). Records disposition of each gap and the
fixes actually applied, mirroring the established `payment` T02 Phase 11 precedent (gaps
verified against source, then fixed in the same phase rather than deferred).

## Gap 1 — No guard against extra/altered `EventTopics` mappings

**ACCEPTED and FIXED.** Added `mappingIsExactlyTheFourAggregateTypesDesignDotMdSpecifies` to
`EventTopicsTest.java`, using reflection on the private static map (no new public mutation
surface added, per design.md §4c) to assert the map is exactly the four entries, not merely a
superset.

## Gap 2 — `outboxRelayIntervalIsConfigured` checks presence, not shape

**ACCEPTED and FIXED.** Tightened the assertion in `ApplicationPropertiesContentTest.java` from
`contains("...=")` to the exact `themistra.payment.outbox.relay-interval-ms=${OUTBOX_RELAY_INTERVAL_MS:2000}`.

## Gap 3 — `ClockConfig` has no test

**ACCEPTED and FIXED**, despite no `crypto-service` precedent (verified directly — crypto has
no `ClockConfigTest`). Added `ClockConfigTest.java`: asserts exactly one `Clock` bean, UTC zone,
and that it is live (not a stub). Cheap, directly tied to AC7, worth doing even without a sibling
precedent.

## Gap 4 — `KafkaProducerConfig` has no test

**ACCEPTED and FIXED.** Added `KafkaProducerConfigTest.java`, mirroring `crypto-service`'s
identical test verbatim (confirmed by direct read first): asserts the real bean wins over
`KafkaAutoConfiguration`'s own, with `acks=all` and `enable.idempotence=true`, and exactly one
`KafkaTemplate<String,String>` bean.

## Gap 5 — `OutboxPublisher` untested

**ACCEPTED and FIXED.** Added `OutboxPublisherTest.java`, mirroring `crypto-service`'s identical
test, minus the idempotency-key parameter/assertions (payment's own 4-parameter `publish` has no
such parameter — Phase 3/6 disposition). Covers the happy path (serialization, metadata,
`createdAt` from the injected `Clock`), all null/blank guards, and the unserializable-payload
`IllegalStateException` wrapping.

## Gap 6 — `OutboxRelay` untested

**ACCEPTED and FIXED.** Added `OutboxRelayTest.java`, mirroring `crypto-service`'s identical
test, adapted for payment's own 4 aggregate types and its 5-parameter (no idempotency key)
`OutboxEvent.create`. Covers successful send, failed send, interrupted/timeout paths, one
failure not blocking the batch, all 4 aggregate-type routings, a failed mark-published save,
an unroutable aggregate type, an empty batch, and the batch-size-100 poll.

## Gap 7 — `OutboxEvent`'s `@PrePersist` fallback is not exercised

**ACCEPTED as written; NO test added.** Verified `crypto-service` has no `OutboxEventTest`
either — this is an established, consistent precedent (the fallback path is unreachable through
the only sanctioned write path, `OutboxPublisher`, which is itself now fully tested per Gap 5).
Not actioned, to stay consistent with that precedent rather than add asymmetric coverage.

## Verification performed this phase

- All 6 new/modified test files individually run: 41/41 passing.
- Mutation checks (introduce defect → confirm red → revert → confirm green) on:
  `OutboxRelay.BATCH_SIZE` (100→50), `KafkaProducerConfig`'s `acks` config ("all"→"1"),
  `ClockConfig`'s zone (UTC→America/New_York), `OutboxPublisher`'s `createdAt` wiring
  (`clock.instant()`→`Instant.EPOCH`). All four caught correctly; all four production files
  confirmed reverted to a clean `git status`.
- Full non-Docker-dependent suite: `mvn -pl services/payment -am test
  -Dtest='!SchemaBaselineMigrationIntegrationTest'` — **85/85 passing** (up from 59 before this
  phase's additions). `SchemaBaselineMigrationIntegrationTest` (17 tests, T02, Testcontainers)
  unaffected by this task and not run this phase — Docker is not running in this environment.

## Net result

6 of 7 gaps accepted and fixed in this phase (new tests added: `ClockConfigTest`,
`KafkaProducerConfigTest`, `OutboxPublisherTest`, `OutboxRelayTest`; existing tests strengthened:
`EventTopicsTest`, `ApplicationPropertiesContentTest`). Gap 7 accepted as a documented, precedent-
consistent non-action. No production code changed. Proceeding to Phase 12.
