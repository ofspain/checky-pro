# notification · T04 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Proceeding to merge preparation.

## Commit title

`notification-service T04: idempotency ledger (ProcessedEvent, IdempotencyGuard)`

## Commit message

```
notification-service T04: idempotency ledger

Add ProcessedEvent (maps onto T02's already-migrated processed_events
table, client-assigned @Id on eventKey - the source event's own stable
key IS the primary key) and ProcessedEventRepository. Add
IdempotencyGuard.recordIfNew(eventKey, eventType), the single place every
future consumer will call to dedupe (L1, R7/R8): @Transactional with
Spring's default REQUIRED propagation, so it always joins whatever
transaction its caller already has open rather than committing
independently - a future consumer can call this and then append to
delivery_log in the same transaction without this class needing to know
delivery_log exists. Add ClockConfig (this module's first injectable
Clock bean). Add V4__notification_app_processed_events_grant.sql
(INSERT+SELECT only, append-only philosophy mirrors delivery_log's own
T02 grant).

The dedupe mechanism itself went through a real, empirically-driven
design correction during implementation: an initial existsById-then-save
approach had a genuine TOCTOU race under concurrent duplicate calls;
catching the resulting DataIntegrityViolationException under default
REQUIRED propagation did not fix it, because Hibernate marks the physical
transaction rollback-only on flush failure regardless of the catch, so
Spring's own transactional proxy threw UnexpectedRollbackException back
to the caller anyway (verified with 8 real concurrent threads - 7 of 8
failed). Propagation.NESTED was tried next and also failed
(JpaTransactionManager doesn't support savepoints). The final design uses
a native INSERT ... ON CONFLICT (event_key) DO NOTHING query
(ProcessedEventRepository.insertIfNew), which never raises a constraint
violation for the conflicting row at all - verified 8/8 threads resolve
correctly, and the transaction-join/rollback guarantee holds.

Updates two T01/T02 test files per the frozen brief's own explicit
authorization: T01SkeletonRegressionTest's 8-file authorized production
list becomes 12; NotificationBaselineMigrationIntegrationTest moves
processed_events from UNGRANTED_TABLES to its own dedicated grant-proof
test (its schema has no source_event_key/outcome columns, so it doesn't
fit the existing delivery_log-shaped shared helper).

79 tests total (68 T01-T03 unaffected + 2 unit + 6 integration, including
a real 8-thread concurrent-call proof, a real transaction-join/rollback
proof via TransactionTemplate, and a processedAt round-trip proof against
a fixed-clock test override). Two of the most novel checks were
independently mutation-tested: reverting ON CONFLICT DO NOTHING to a
plain INSERT, and changing @Transactional's propagation to REQUIRES_NEW -
both confirmed caught by the relevant tests, then reverted.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Created**
- `services/notification/src/main/java/com/themistra/notification/consumer/{ProcessedEvent,ProcessedEventRepository,IdempotencyGuard}.java`
- `services/notification/src/main/java/com/themistra/notification/common/ClockConfig.java`
- `services/notification/src/main/resources/db/migration/V4__notification_app_processed_events_grant.sql`
- `services/notification/src/test/java/com/themistra/notification/consumer/{IdempotencyGuardUnitTest,IdempotencyGuardIntegrationTest}.java`

**Modified**
- `services/notification/src/test/java/com/themistra/notification/T01SkeletonRegressionTest.java`
  (12-file authorized production list)
- `services/notification/src/test/java/com/themistra/notification/NotificationBaselineMigrationIntegrationTest.java`
  (`processed_events` grant-proof coverage; Flyway-history expectation widened to `"1","2","3","4"`)

**Process artifacts**
- `.ai/prompts/notification/T04/artifacts/00-12-*.md` — full 14-phase pipeline record (this file
  completes it).

## Summary

Establishes the idempotency ledger every later consumer task depends on (L1, R7/R8) — not just the
schema mapping, but a genuinely correct, concurrency-safe, transaction-compatible dedupe mechanism,
arrived at only after two reasonable-looking designs were tried and empirically disproven within
this task's own pipeline, before ever reaching a review gate.

## Testing performed

- `mvn -pl services/notification clean verify` — 79 tests, 0 failures, `BUILD SUCCESS`.
- Real `ProcessedEvent` entity mapping validated by Hibernate's own `ddl-auto=validate` against the
  real, T02-migrated schema in every Testcontainers run.
- Three real mutation tests across this task's pipeline, all reverted clean (`git status -s` empty
  afterward each time):
  1. (Phase 6, uncommitted scratch test) the original `existsById`-then-`saveAndFlush`-with-catch
     design — 7 of 8 concurrent threads threw `UnexpectedRollbackException`, leading to the
     `ON CONFLICT DO NOTHING` redesign.
  2. (Phase 10) reverted `ON CONFLICT DO NOTHING` to a plain `INSERT` — confirmed both the
     concurrent-call and serial-dedupe tests fail with the exact original defect.
  3. (Phase 11) changed `@Transactional`'s propagation to `REQUIRES_NEW` — confirmed both the new
     fast reflection test and the real transaction-join integration test independently catch it.
- `git status -s services/auth services/crypto` — empty throughout; no sibling service touched.

## Specification references

- **Task:** `spec/notification-service/tasks.md`, task 4 ("Idempotency ledger").
- **Requirements:** R7 (record the key so an event is processed at most once), R8 (redelivery must
  not produce a second delivery).
- **LOCKED decision:** L1 (idempotent by event key; `processed_events` PK + same-transaction
  compatibility with a future `delivery_log` append).

## Known, deliberate gaps (not this task's scope)

- No real consumer exists yet to exercise `IdempotencyGuard` end-to-end with an actual
  `delivery_log` write in the same transaction — task 6's own scope, per `design.md` §6's package
  map (`ProcessedEvent` in `consumer/`, `DeliveryLog` in a separate `delivery/` module).
- `ProcessedEvent.create(...)` was removed (not kept with a "future use" note) once the write path
  moved to a native query — the entity is read-only in practice now
  (`existsById`/`findById`, inherited from `JpaRepository`).

## Reviewer notes

- Kimi's Phase 3 (design), Phase 8 (implementation), and Phase 11 (test) reviews raised 8, 6, and 7
  findings respectively — all 21 verified against actual source before disposition. One finding
  (Phase 3's own Finding #2) was verified **false** against source: Kimi conflated the TIB's own
  internally-numbered "Phase 1 Open Question #1/#2/#3" with `design.md` §11's unrelated, globally-
  numbered Q1/Q2/Q3 (recipient resolution / email transport / SSE) — no content was actually
  copy-pasted or wrong; a small courtesy rename was still applied to prevent the same numbering
  collision confusing a future reader.
- `IdempotencyGuard`'s own class Javadoc is worth a reviewer's direct read: it documents the two
  dead-end designs (catch-under-`REQUIRED`, `Propagation.NESTED`) and exactly why each failed, so
  nobody rediscovers either from scratch when writing similar transactional dedupe/upsert logic
  elsewhere in this codebase.
- The bean-name collision hit while wiring the fixed-clock test override
  (`BeanDefinitionOverrideException`, Spring Boot's own bean-override-disabled-by-default policy) is
  a useful, generalizable lesson for any future test that needs to override an existing `@Bean`:
  give the override a different method/bean name and rely on `@Primary`, don't reuse the same name.

---

**Phase 13 complete — PR description drafted, all phases 0-12 closed for notification-service T04.**
