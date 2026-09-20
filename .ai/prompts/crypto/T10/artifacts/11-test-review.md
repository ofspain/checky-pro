<!-- MODEL: Kimi 2.7 -- Phase 11 (Test Review) for crypto / T10. -->

# crypto / T10 / Phase 11 -- Test Review Findings

**Scope:** Review the Phase 10 test suite (`ProviderHealthPropertiesTest`, `ProviderHealthTest`, `ProviderDegradedPublisherTest`, `ProviderHealthTrackerTest`, `ProviderHealthRepositoryIntegrationTest`) against the frozen brief's acceptance criteria and `spec/crypto-service/agents.md`.

**Directive:** Do not rewrite production code or tests. Return gaps as **Gap / Why it matters / Suggested test.**

---

## Gap 1 -- The required module-boundary test (AC5) is missing

**Why it matters:** AC5 requires that no import in `provider/` reaches `adapter/`, `observation/`, or `quorum/`. The frozen brief's Required Tests explicitly calls for a static/reflection check. The Phase 10 manifest does not include such a test, and no module-boundary test file exists under `provider/`.

**Suggested test:** Add `ProviderHealthModuleBoundaryTest` using either a simple reflection scan of `com.themistra.crypto.provider` classes' imports or an ArchUnit rule (`noClasses().that().resideInAPackage("..provider..").should().dependOnClassesThat().resideInAnyPackage("..adapter..", "..observation..", "..quorum..")`).

---

## Gap 2 -- No test exercises the first-write INSERT race for concurrent calls

**Why it matters:** The production code acknowledges that two concurrent first-time calls for the same `(chain, provider)` can both attempt an INSERT, causing one to fail with a raw `DataIntegrityViolationException`. This is a real concurrency failure mode with no regression guard.

**Suggested test:** Add a concurrency test that submits two simultaneous `recordUnhealthy` calls for the same `(chain, provider)` against a real repository and asserts that at least one succeeds and the other either succeeds via retry or fails with a domain-level exception rather than a raw `DataIntegrityViolationException`. If the accepted behavior is to let it fail, document that with the test.

---

## Gap 3 -- No test demonstrates or guards against the lost-update race

**Why it matters:** `ProviderHealth` has no `@Version` column, so two concurrent read-modify-write transactions can silently overwrite each other. The production code accepts this as a launch-scope risk. Without a test, a future refactor could unknowingly make it worse or a schema change intended to fix it would have no verification.

**Suggested test:** Add a concurrency test where Thread A reads the row, marks unhealthy, and saves; Thread B reads the same row before A commits, marks healthy, and saves. Assert the final persisted state and document whether the observed behavior is acceptable. Alternatively, add a regression guard test that asserts `ProviderHealth` has no `@Version` field and document the accepted risk.

---

## Gap 4 -- No test verifies `ProviderHealthTracker` methods are `@Transactional`

**Why it matters:** The production Javadoc explains that `recordUnhealthy`'s transition path needs an explicit `@Transactional` boundary so the DB save and outbox publish commit atomically. A future refactor that removed the annotation would break the outbox-in-same-transaction invariant without failing any existing test.

**Suggested test:** Add a reflection-based test asserting that `ProviderHealthTracker.recordHealthy`, `recordUnhealthy`, and `recordDisagreement` are annotated with `@Transactional`.

---

## Gap 5 -- No test verifies the `ProviderDegradedPublisher.Payload` serializes to JSON as expected

**Why it matters:** The payload contains an `Instant` and a `DegradationReason` enum. Depending on the `ObjectMapper` configuration, `Instant` may serialize as epoch millis or as an ISO-8601 string, and the enum may serialize as a name or ordinal. The future `provider-degraded.v1.schema.json` (task 23) will depend on this serialization shape.

**Suggested test:** Add `ProviderDegradedPublisherTest.serializesPayloadToJsonWithIsoInstantAndEnumName` that captures the `OutboxEvent` payload string and asserts it contains `"occurredAt":"2026-09-03T12:00:00Z"` (or the configured format) and `"reason":"LAGGING"`.

---

## Gap 6 -- No test verifies `provider_health`'s unique constraint

**Why it matters:** The table has `UNIQUE (chain, provider)`. A regression that accidentally removed or weakened this constraint would allow duplicate health rows, breaking the upsert semantics.

**Suggested test:** Add `aSecondRowForTheSameChainAndProviderViolatesTheUniqueConstraint` to `ProviderHealthRepositoryIntegrationTest` that saves one row and then asserts saving a second row with the same `(chain, provider)` fails with `DataIntegrityViolationException`.

---

## Gap 7 -- No test exercises `ProviderHealthTracker` under concurrent disagreement counting

**Why it matters:** The in-memory disagreement counter uses `ConcurrentHashMap`/`AtomicInteger` and is intended to be thread-safe. No test exercises concurrent `recordDisagreement` calls for the same provider.

**Suggested test:** Add a concurrency test that invokes `recordDisagreement` `threshold` times concurrently for the same provider and asserts exactly one `chain.provider.degraded` event is published (or at most one, accepting the duplicate-event race documented in the code).

---

## Gap 8 -- No test verifies the exact idempotency-key prefix format

**Why it matters:** The current test only asserts that two publishes produce different keys and that each starts with `chain:provider:degraded:occurredAt`. It does not assert the exact format (e.g., that there is exactly one colon separator between components, or that the UUID is at the end). A regression that changed the separator ordering would not be caught.

**Suggested test:** Enhance `twoPublishesWithIdenticalArgumentsProduceDifferentIdempotencyKeys` to assert each key matches a regex such as `^ETHEREUM:alchemy:degraded:2026-09-03T12:00:00Z:[0-9a-f\-]{36}$`.

---

## Gap 9 -- No tracker-level test verifies `lastOkAt` and `lastDisagreementAt` behavior end-to-end

**Why it matters:** `ProviderHealthTest` verifies the mutators in isolation, but no tracker-level test proves that `recordHealthy` sets `lastOkAt`, that `recordDisagreement` sets `lastDisagreementAt`, or that `recordUnhealthy` preserves `lastOkAt`.

**Suggested test:** Add `trackerUpdatesLastOkAtAndLastDisagreementAt` that calls `recordHealthy`, `recordDisagreement`, and `recordUnhealthy` in sequence against the mocked/map-backed repository and asserts the persisted row's `lastOkAt`/`lastDisagreementAt` values match the fixed clock instants.

---

## Gap 10 -- No test verifies distinct chains isolate the same provider name

**Why it matters:** The tracker keys state by `(chain, provider)`. The existing test verifies distinct providers on the same chain are isolated, but not the inverse: the same provider name on two different chains should be tracked independently.

**Suggested test:** Add `distinctChainsTrackTheSameProviderNameIndependently` that calls `recordUnhealthy("ETHEREUM", "alchemy", ...)` and `recordUnhealthy("TRON", "alchemy", ...)` and asserts two separate health rows and two separate degraded events.

---

## Gap 11 -- No test verifies `recordUnhealthy(..., REPEATED_DISAGREEMENT)` when called directly

**Why it matters:** `REPEATED_DISAGREEMENT` can be passed directly to `recordUnhealthy` (not just reached via the disagreement threshold). The current tests only exercise it indirectly through `recordDisagreement`.

**Suggested test:** Add `recordUnhealthyWithRepeatedDisagreementReasonTransitionsAndEmitsEvent` to `ProviderHealthTrackerTest`.

---

## Gap 12 -- No test verifies `ProviderDegradedPublisher` rejects null arguments directly

**Why it matters:** `ProviderDegradedPublisher.publish` delegates to `OutboxPublisher`, which itself rejects nulls. However, a null `chain` or `provider` would still build a malformed `aggregateId` before reaching `OutboxPublisher`. The production code does not guard against nulls in `publish`.

**Suggested test:** Add parameterized null-guard tests for `ProviderDegradedPublisher.publish` and consider adding `Objects.requireNonNull` guards in production code.

---

## Summary table

| # | Gap | Risk | Suggested test approach |
|---|-----|------|-------------------------|
| 1 | Missing module-boundary test (AC5) | Cross-feature import regression | ArchUnit or reflection scan |
| 2 | First-write INSERT race untested | Raw constraint violation leaks | Concurrent first-call test |
| 3 | Lost-update race untested | Silent state corruption | Concurrent RMW test or `@Version` absence guard |
| 4 | `@Transactional` status unguarded | Outbox/DB atomicity regression | Reflection annotation test |
| 5 | Payload JSON serialization untested | Contract mismatch in task 23 | Serialize and inspect JSON |
| 6 | Unique constraint untested | Duplicate health rows | DB-level unique-constraint test |
| 7 | Concurrent disagreement counting untested | Counter thread-safety regression | Concurrent disagreement test |
| 8 | Idempotency-key exact format unasserted | Key-format regression | Regex assertion |
| 9 | Tracker-level `lastOkAt`/`lastDisagreementAt` unasserted | Mutator wiring regression | End-to-end timestamp assertions |
| 10 | Same provider name across chains untested | Key-isolation regression | Two-chain test |
| 11 | Direct `REPEATED_DISAGREEMENT` reason untested | Reason-coverage gap | Direct `recordUnhealthy` test |
| 12 | `ProviderDegradedPublisher` null guards untested | Malformed aggregateId before OutboxPublisher | Parameterized null tests |

(End of test review.)
