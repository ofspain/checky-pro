# notification · T04 · Phase 12 — Specification Verification

## Traceability matrix

| Requirement / Decision | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **AC1** — `ProcessedEvent` maps exactly onto the 3 existing columns, client-assigned `@Id`, explicit schema | Yes | `ProcessedEvent.java:27-32` (`@Table(name = "processed_events", schema = "notifications")`, `@Id` on `eventKey`, no `@GeneratedValue`) | Validated by Hibernate's own `ddl-auto=validate` against the real T02-migrated schema in every Testcontainers run; `IdempotencyGuardIntegrationTest.processedAtRoundTripsFromTheInjectedClockThroughToAReadableRow` also asserts `eventKey`/`eventType` round-trip | No | No |
| **AC2** — `recordIfNew` returns `true` exactly once per key, `false` thereafter, including under real concurrency | Yes | `ProcessedEventRepository.java:25-29` (native `INSERT ... ON CONFLICT (event_key) DO NOTHING`), `IdempotencyGuard.java:52-56` | `IdempotencyGuardUnitTest` (2 named tests), `IdempotencyGuardIntegrationTest.secondCallWithSameKeyReturnsFalseSerially`, `.concurrentCallsWithSameKeyResolveToExactlyOneTrue` (8 real threads + DB row-count proof) | No | No |
| **AC3** — `@Transactional` default `REQUIRED` propagation, provably joins an already-open caller transaction | Yes | `IdempotencyGuard.java:52` (`@Transactional`, no explicit propagation — resolves to `REQUIRED`) | `IdempotencyGuardUnitTest.recordIfNewUsesDefaultRequiredPropagation` (reflection), `IdempotencyGuardIntegrationTest.recordIfNewJoinsAnExternallyOpenedTransactionAndRollsBackWithIt` + `.recordIfNewCommitsWhenAnExternallyOpenedTransactionCommits` (real transaction-join/rollback and commit proof) | No | No |
| **AC4** — `processedAt` from an injected `Clock`, never `Instant.now()` inline | Yes | `IdempotencyGuard.java:54` (`clock.instant()`), `ClockConfig.java` (`Clock.systemUTC()` bean) | `IdempotencyGuardUnitTest.shouldUseTheInjectedClockNotWallClockTime` (mock-interaction proof), `IdempotencyGuardIntegrationTest.processedAtRoundTripsFromTheInjectedClockThroughToAReadableRow` (real DB round-trip against a fixed-clock override, exact-equality) | No | No |
| **AC5** — `notification_app` can `INSERT`/`SELECT` on `processed_events`; still cannot `UPDATE`/`DELETE` | Yes | `V4__notification_app_processed_events_grant.sql` (whole file) | `NotificationBaselineMigrationIntegrationTest.notificationAppCanInsertAndSelectButNotUpdateOrDeleteOnProcessedEvents` | No | No |
| **AC6** — both a unit test and a Testcontainers integration test pass | Yes | — | `IdempotencyGuardUnitTest` (5 tests), `IdempotencyGuardIntegrationTest` (6 tests) | No | No |
| L1 (idempotent by event key) | Fully realized for this task's own scope | `processed_events` (T02 schema) + `insertIfNew`'s single-source-of-truth constraint + `REQUIRED` propagation together | All of the above | The actual joint transaction with a real `delivery_log` append (a later consumer task) | No — this task's own scope is the ledger and its transaction-*compatibility*, not the joint write itself, per Phase 1/2's own explicit boundary |

## Principal-engineer review

**(1) Is the task fully complete?** Yes, against T04's own literal scope (`tasks.md` task 4:
`ProcessedEvent` + repository + a dedupe helper recording the key in the same transaction as a
future delivery-log append; unit-test the dedupe). All 5 production files delivered
(`ProcessedEvent`, `ProcessedEventRepository`, `IdempotencyGuard`, `ClockConfig`, `V4`) plus 2 test
files (79 tests total). `T01SkeletonRegressionTest.java` and
`NotificationBaselineMigrationIntegrationTest.java` were modified exactly as the frozen brief
explicitly authorized, both disclosed changes verified still passing.

**(2) Does it satisfy every acceptance criterion?** Yes — AC1 through AC6 all hold, each with direct
evidence and automated coverage. AC2/AC3 in particular are proven more rigorously than most tasks'
own equivalents: a real design flaw (the original catch-`DataIntegrityViolationException`-under-
`REQUIRED`-propagation approach) was caught by empirical testing during Phase 6 itself, before ever
reaching a review gate, and the final design's own two most novel properties (concurrent-call
correctness, transaction-join/rollback behavior) were each independently mutation-tested for real
(Phase 10's `ON CONFLICT` reversion, Phase 11's `REQUIRES_NEW` reversion) — not merely asserted.

**(3) Does it violate any LOCKED decision?** No. L1 is fully realized at this task's own schema+code
level; the "same transaction as the delivery-log append" requirement is satisfied by
*compatibility* (default `REQUIRED` propagation, proven to genuinely join a caller's transaction) — a
real joint write awaits the consumer task that first calls both `IdempotencyGuard` and a future
`DeliveryLog` write together, exactly as Phase 1/2 scoped it.

**(4) Remaining risks?**
- `ProcessedEvent.create(...)` was removed entirely (Phase 9) rather than kept with a documented
  future-use note — the more conservative of the two options Phase 7/8 both offered, but means any
  future task needing to construct a `ProcessedEvent` directly (unlikely, given the entity is
  read-only in practice) would need to re-add it.
- The real design pivot (native query replacing JPA `save`) was discovered and fixed entirely within
  this task's own pipeline, but it is a genuinely non-obvious Spring/JPA interaction
  (`UnexpectedRollbackException` after a caught, translated exception under `REQUIRED` propagation;
  `Propagation.NESTED`'s lack of savepoint support under the default `JpaTransactionManager`) — worth
  a mental note for whoever writes the next transactional dedupe/upsert logic in this codebase, so
  the same two dead ends aren't rediscovered from scratch. `IdempotencyGuard`'s own Javadoc documents
  both, which should suffice.
- No real consumer exists yet to prove L1's own full, joint-transaction guarantee end-to-end — by
  design, correctly deferred to task 6+.

## `package.md` §9 whole-service checklist — items relevant to T04

- "Every consumer is idempotent: replaying any consumed event twice results in exactly one
  delivery" — the ledger and its dedupe mechanism this checklist item depends on are now real and
  proven; the actual "replaying a consumed event" behavior requires a real consumer (task 6+),
  correctly out of scope here.
- Every other item (delivery log, secrets in messages, preference resolution, in-app stream,
  retry/dead-letter, contract conformance) requires feature code that doesn't exist yet — correctly
  out of scope.

## Cross-task regression check

Full `services/notification` suite: `T01SkeletonRegressionTest` (9 tests, its own 12-file authorized
list) and `NotificationBaselineMigrationIntegrationTest` (14 tests, `processed_events` now in
grant-proof coverage) both still pass alongside T03's own 45 tests and T04's own 13 — confirms T04's
changes didn't regress anything the prior three tasks established.

## Spec status

`spec/notification-service/package.md`'s header is unchanged — the version/status bump is task 20,
matching the established `auth-service`/`crypto-service` precedent. Not touched here.

---

**PASS** — all 6 acceptance criteria satisfied with direct evidence and automated coverage (79
tests, two of them independently mutation-tested), no LOCKED decision violated, task boundary held
throughout, and a real mid-implementation design flaw was caught and fixed via empirical testing
rather than assumption.
