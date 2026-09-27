<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Code Review). -->

# notification · T04 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T04 — Idempotency ledger |
| **Spec section** | Consumers & idempotency |
| **Model** | Kimi 2.7 |
| **Consumes** | Phase 6 implementation + `artifacts/07-self-review.md` |
| **Produces** | `artifacts/08-independent-review.md` |

Fresh adversarial review of the completed T04 implementation. Findings only.

---

## Finding 1 · Required T04 tests are entirely missing

**Issue:** The TIB's Required Tests section and `package.md` §8 name two specific tests (`shouldDedupeDuplicateEventDeliveryByEventKey`, `shouldNotDoubleSendWhenSameEventRedelivered`) plus a unit test and a Testcontainers integration test for `IdempotencyGuard`. The current codebase contains no T04-specific test files. There is no unit test for `IdempotencyGuard`, no integration test for transaction join/rollback, and no integration test for concurrent duplicate calls resolving to a single `true`.

**Evidence:**
- `services/notification/src/test/java/com/themistra/notification/` contains only the T01, T02, and T03 test files.
- `grep` for `IdempotencyGuard`, `ProcessedEvent`, `recordIfNew`, `shouldDedupe`, or `shouldNotDoubleSend` in `src/test` returns only references in `T01SkeletonRegressionTest.java` (production-file list) and `NotificationBaselineMigrationIntegrationTest.java` (grant test for `processed_events`).
- No file such as `IdempotencyGuardTest.java` or `IdempotencyGuardIntegrationTest.java` exists.

**Recommendation:** Add the missing tests before considering T04 complete:
- `IdempotencyGuardTest` (unit): mock `ProcessedEventRepository` and fixed `Clock`, assert `recordIfNew` returns `true` on first call and `false` on duplicate, and assert it passes `clock.instant()` as `processedAt`.
- `IdempotencyGuardIntegrationTest` (Testcontainers):
  - open a test-managed transaction, call `recordIfNew`, roll back, assert the row is gone (transaction join);
  - open a transaction, call `recordIfNew`, commit, assert the row persists;
  - call `recordIfNew` twice with the same key serially, assert first returns `true` and second returns `false`;
  - call `recordIfNew` concurrently from two threads with the same key, assert exactly one returns `true` and no exception escapes;
  - retrieve the inserted row via `repository.findById(eventKey)` and assert `getProcessedAt()` equals the fixed clock instant (round-trip).

**Confidence:** High

---

## Finding 2 · `ProcessedEvent.create(...)` is dead code

**Issue:** The write path bypasses the `ProcessedEvent` entity entirely: `IdempotencyGuard.recordIfNew` calls `ProcessedEventRepository.insertIfNew` with native query parameters. `ProcessedEvent.create(...)` is never invoked by any production code. It is misleading because the class Javadoc says "`IdempotencyGuard` is the only writer," which a reader would naturally interpret as `IdempotencyGuard` constructing and persisting `ProcessedEvent` instances.

**Evidence:**
- `services/notification/src/main/java/com/themistra/notification/consumer/ProcessedEvent.java` lines 35–41: `create(...)` exists.
- `grep -n "ProcessedEvent.create"` across `src/main` finds only the declaration (self-review Finding 1 confirmed).

**Recommendation:** Remove `ProcessedEvent.create(...)` now that the write path is native-query-based. If a plausible future use case (e.g., batch backfill) justifies keeping it, add an explicit Javadoc note that it is currently unused and exists only for that hypothetical future path. Leaving dead factory code with no note risks a future refactor assuming it is the active construction path.

**Confidence:** Medium

---

## Finding 3 · Native modifying query does not clear the first-level cache

**Issue:** `ProcessedEventRepository.insertIfNew` is a `@Modifying` native query with no `clearAutomatically` or `flushAutomatically` attributes. Native modifying queries bypass Hibernate's persistence context. If a future caller reads `processed_events` earlier in the same session (caching a "not present" result), then calls `recordIfNew`, then reads again, the second read could be served from the stale first-level cache rather than the newly inserted row.

**Evidence:**
- `ProcessedEventRepository.java` line 21: `@Modifying` with no cache-clear attribute.
- Spring Data JPA documentation recommends `clearAutomatically = true` for native modifying queries that share a persistence context with subsequent reads.

**Recommendation:** Add `@Modifying(clearAutomatically = true, flushAutomatically = true)` to `insertIfNew`. This is a cheap defensive change with no behavioral impact on the current single caller.

**Confidence:** Low

---

## Finding 4 · No automated proof that `processedAt` round-trips correctly

**Issue:** AC4 requires `processedAt` to be set from an injected `Clock`. The code satisfies this by passing `clock.instant()` into the native query, but no test retrieves the persisted row and asserts the stored `Instant` equals the clock value exactly. A binding or precision bug (e.g., timezone truncation, driver-specific `TIMESTAMPTZ` handling) would not be caught.

**Evidence:**
- `IdempotencyGuard.java` line 54: passes `clock.instant()` as the `processedAt` native query parameter.
- No test calls `repository.findById(eventKey).get().getProcessedAt()` and compares it to the expected instant.

**Recommendation:** Add a round-trip assertion to the integration test: after a successful `recordIfNew`, fetch the `ProcessedEvent` by `eventKey` and assert `getProcessedAt()` is exactly the fixed clock instant used by the test.

**Confidence:** Medium

---

## Finding 5 · No negative-proof that mutation of the `ON CONFLICT DO NOTHING` query fails the tests

**Why it matters:** The core correctness property of T04 — that duplicate keys resolve cleanly to `false` without exception — rests entirely on the native query using `ON CONFLICT DO NOTHING`. If a future edit accidentally reverts it to a plain `INSERT` or an exists-then-save pattern, the build should fail. There is no documented mutation test for this specific property.

**Suggested test:** Perform a one-time mutation check (documented in the test file or Phase 10 notes): temporarily change `insertIfNew`'s SQL to `INSERT INTO ... VALUES (...)` without `ON CONFLICT`, run the duplicate-call integration test, and confirm it fails with a constraint-violation-related error. Revert the change. This proves the tests guard the concurrency property, not just the happy path.

**Confidence:** Low

---

## Finding 6 · `eventKey` column mapping does not assert `nullable=false`

**Issue:** `ProcessedEvent.eventKey` is the `@Id` and the table's primary key, but the `@Column` annotation omits `nullable=false`. Hibernate infers non-nullability from `@Id`, so the mapping is functionally correct. However, the entity is less explicit than it could be, and a future reader might wonder whether a null key is permitted.

**Evidence:**
- `ProcessedEvent.java` line 22: `@Column(name = "event_key", length = 200)` without `nullable = false`.

**Recommendation:** Add `nullable = false` to the `@Column` annotation on `eventKey` for consistency with the other two columns and explicit documentation of the NOT NULL constraint.

**Confidence:** Low

---

## Summary

The T04 production code is well-designed: the `INSERT ... ON CONFLICT DO NOTHING` approach correctly solves the concurrent duplicate-call problem that a naive exists-then-save would have, and the `@Transactional(REQUIRED)` propagation preserves L1's transaction-join requirement. The `V4` grant and the updates to the T01/T02 regression tests are correct. The dominant issue is the complete absence of T04-specific automated tests (Finding 1). Findings 2–4 are quality/reliability issues noted in the self-review that remain unaddressed. Findings 5–6 are minor precision items.
