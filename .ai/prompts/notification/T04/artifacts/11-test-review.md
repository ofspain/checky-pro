<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review). -->

# notification · T04 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T04 — Idempotency ledger |
| **Spec section** | Consumers & idempotency |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` + T04 test files |
| **Produces** | `artifacts/11-test-review.md` |

Review of the T04 regression-guard tests against the acceptance criteria and task statement.

---

## Gap 1 · `processedAt` round-trip test does not prove the value comes from the injected `Clock`

**Why it matters:** AC4 requires `processedAt` to be set from an injected `Clock`, not `Instant.now()`. The current integration test `processedAtRoundTripsFromTheInjectedClockThroughToAReadableRow` only asserts the stored instant is within 1 second of `Instant.now()` before and after the call. A bug that caused `IdempotencyGuard` to use `Clock.systemUTC()` or even `Instant.now()` directly would pass this test because the value would still be "recent." The unit test verifies the *repository* is called with the fixed clock instant, but it is a mock-interaction test, not a persisted-value test.

**Suggested test:** Replace the wall-clock range assertion with a fixed-clock integration test. Provide a `@TestConfiguration` that declares a fixed `Clock` bean (e.g., `Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC)`), autowire that bean in the test, call `recordIfNew`, then assert `repository.findById(eventKey).orElseThrow().getProcessedAt()` is exactly the fixed instant.

---

## Gap 2 · No automated guard that the native query uses `ON CONFLICT DO NOTHING`

**Why it matters:** The entire concurrent-deduplication guarantee rests on `ProcessedEventRepository.insertIfNew` using `INSERT ... ON CONFLICT (event_key) DO NOTHING`. The Phase 10 artifact documents a one-time manual mutation test, but if a future refactor accidentally reverts the SQL to a plain `INSERT`, the build will not fail until someone re-runs that manual check.

**Suggested test:** Add a plain JUnit test that reads `ProcessedEventRepository.java` as text and asserts the `@Query` value contains `"ON CONFLICT"` and `"DO NOTHING"`. This is a cheap, permanent regression guard for the specific property the concurrent test depends on.

---

## Gap 3 · Concurrent test does not verify the database row count

**Why it matters:** `concurrentCallsWithSameKeyResolveToExactlyOneTrue` verifies that exactly one thread receives `true` and that no exceptions escape. It does not explicitly assert that the database contains exactly one row for that key after the test. A pathological bug where the method returned `true`/`false` correctly but somehow wrote zero or multiple rows would not be caught.

**Suggested test:** After the concurrent test's assertions on the `results` list, add:

```java
assertThat(repository.count()).as("exactly one processed_events row must exist").isEqualTo(1L);
// or, if tests accumulate rows:
assertThat(repository.findById(eventKey)).isPresent();
```

Use a fresh key per test and assert `count()` for that key is 1, or query by the specific `eventKey`.

---

## Gap 4 · No explicit verification of `@Transactional` propagation

**Why it matters:** AC3 requires `recordIfNew` to use default `REQUIRED` propagation. The integration tests prove the *behavior* (joins/rolls back with caller transaction), but they do not prove the *mechanism*. A future refactor could move transaction demarcation to a wrapper and the tests might still pass, even though the brief's constraint is about this specific method.

**Suggested test:** Add a reflection-based test in `IdempotencyGuardUnitTest` that inspects `IdempotencyGuard.recordIfNew` and asserts:
- the method is annotated with `@Transactional`;
- the annotation does not specify `propagation = REQUIRES_NEW` or `propagation = NOT_SUPPORTED`.

This is a permanent, fast guard against the exact constraint in the brief.

---

## Gap 5 · Unit tests do not exercise the `eventType` boundary

**Why it matters:** The unit test `shouldUseTheInjectedClockNotWallClockTime` verifies all three parameters passed to the repository (`eventKey`, `eventType`, `processedAt`), but the two named-package tests (`shouldDedupeDuplicateEventDeliveryByEventKey`, `shouldNotDoubleSendWhenSameEventRedelivered`) use the same `eventKey` and `eventType` in both stubbing and assertion. A bug where `IdempotencyGuard` ignored the `eventType` parameter and passed a hardcoded string would not be caught by the dedupe-return-value tests.

**Suggested test:** Add a unit test that stubs `insertIfNew` with one `eventType` and calls `recordIfNew` with a *different* `eventType`, asserting the stub is not matched and the result is whatever the unmatched mock returns (or use `verify` to assert the passed `eventType` is the one supplied). Alternatively, parameterize the existing clock-verification test to cover multiple eventType values.

---

## Gap 6 · No test verifies `ProcessedEvent` entity mapping directly

**Why it matters:** The integration tests rely on `repository.findById(...)` returning a populated `ProcessedEvent`, which implicitly tests the entity mapping. However, there is no direct test that the entity maps to the correct table/schema/columns. A drift in the `@Table` annotation or a column-name mismatch would only surface in tests that happen to exercise the read path.

**Suggested test:** Add a small test (could be in `IdempotencyGuardIntegrationTest`) that, after inserting a row, asserts the returned `ProcessedEvent` has the expected `eventKey`, `eventType`, and non-null `processedAt`. This is partially covered by the round-trip test, but the schema/table mapping itself is not explicitly asserted.

---

## Gap 7 · No negative-proof automation for the concurrent test

**Why it matters:** The manual mutation test documented in Phase 10 proves the concurrent test catches a reverted SQL. That proof is valuable institutional knowledge, but it is not encoded in the test suite. A future reviewer cannot tell from the code alone that the test was validated against a broken variant.

**Suggested test:** This is a repeat of Gap 2 in a different form. Either (a) add the static SQL scan test recommended in Gap 2, or (b) add a comment in `IdempotencyGuardIntegrationTest` referencing the Phase 10 mutation-test record so the negative-proof is discoverable. Option (a) is preferred.

---

## Summary

The T04 test suite now covers the two `package.md` named tests, transaction join/rollback, serial deduplication, concurrent deduplication, and the `processedAt` round-trip. The strongest remaining gap is Gap 1: the round-trip test uses a wall-clock range instead of a fixed clock, so it does not actually verify AC4's "set from an injected `Clock`" requirement at the persistence layer. Gaps 2–4 tighten the regression guards around the SQL shape, row count, and transaction propagation. Gaps 5–7 are smaller precision/discoverability items.
