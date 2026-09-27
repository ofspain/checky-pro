# notification · T04 · Phase 6 — Implementation Notes

Implements the frozen brief (`artifacts/04-frozen-task-brief.md`) per the Phase 5 plan
(`artifacts/05-implementation-plan.md`). `src/main` files created, plus the two justified,
frozen-brief-authorized test-file updates (T01/T02's own files) — no new test file authored (Phase
10 scope, per this phase's own rule).

## Files created

- `consumer/ProcessedEvent.java` — `@Table(name = "processed_events", schema = "notifications")`
  (Kimi Finding #5), client-assigned `@Id` on `eventKey` (no `@GeneratedValue` — the natural key IS
  the primary key), read-only getters for all 3 fields (Kimi Finding #7).
- `consumer/ProcessedEventRepository.java`.
- `consumer/IdempotencyGuard.java`.
- `common/ClockConfig.java` — mirrors `crypto-service`'s own identical class.
- `db/migration/V4__notification_app_processed_events_grant.sql` — `INSERT, SELECT` only, with the
  append-only rationale comment Kimi's Finding #8 required.

## Files modified

- `T01SkeletonRegressionTest.java` — `noExtraProductionClassesExistBeyondT03sOwnAuthorizedSet`
  renamed to `noExtraProductionClassesExistBeyondT04sOwnAuthorizedSet`; 8-file list widened to the
  12 files T04 authorizes (Kimi Finding #3, frozen-brief-authorized).
- `NotificationBaselineMigrationIntegrationTest.java` — `processed_events` removed from
  `UNGRANTED_TABLES`; a new, self-contained
  `notificationAppCanInsertAndSelectButNotUpdateOrDeleteOnProcessedEvents` test added rather than
  folded into the shared `GRANTED_TABLES` helper (see Deviation below); `processed_events`' 3 cases
  removed from the now-unused-for-it `minimalInsertFixtureFor`/`noWhereUpdateStatementFor`/
  `noWhereDeleteStatementFor` switches; Flyway-history expectation widened to `"1","2","3","4"`.

## Deviation from the plan, flagged not hidden (structural)

Phase 5's own plan said the grant-proof update would be "its own `insertStatementFor`/
`noWhereUpdateStatementFor`/`noWhereDeleteStatementFor` switch statements each gain a
`processed_events` case" — this was wrong once actually attempted: those three switches belong to
the *ungranted*-table checks and the shared `GRANTED_TABLES` positive-proof helper
(`assertInsertAndSelectSucceedUpdateAndDeleteAreDenied`) hardcodes `source_event_key`/`outcome`
column names that only exist on `delivery_log`. `processed_events`' own schema (`event_key` as the
row identifier, no separate correlator column) doesn't fit that helper without a larger, riskier
generalization than this task's own scope justifies for a single new table. Fixed by writing a
self-contained test method using `processed_events`' own real columns instead, and *removing* (not
adding) its cases from the three ungranted-side switches, since it's no longer in `UNGRANTED_TABLES`.

## Deviation from the plan, flagged not hidden (major — design correction found by testing)

Phase 4/5 committed to `IdempotencyGuard.recordIfNew` catching `DataIntegrityViolationException`
around a plain `@Transactional` (default `REQUIRED`) `saveAndFlush` call. Before treating this as
final, a temporary, uncommitted scratch test
(`ScratchIdempotencyGuardVerificationTest` — written, run, and deleted entirely within this phase,
never part of any commit) drove 8 real concurrent threads at the same event key against a real
Testcontainers Postgres. **7 of 8 threads' calls threw instead of returning `false`** — the design
did not work as specified:

1. Hibernate marks the *physical* transaction rollback-only the instant a flush fails, independent
   of whether application code catches the translated `DataIntegrityViolationException`. Spring's
   own transactional proxy, seeing the method return normally but the underlying transaction marked
   rollback-only, throws `UnexpectedRollbackException` back to the caller anyway — a well-documented
   but easy-to-miss Spring/JPA interaction, confirmed here empirically, not assumed.
2. `Propagation.NESTED` (a real `SAVEPOINT`, which would have isolated just the failed insert
   without poisoning the caller's transaction) was tried next — also failed empirically:
   `NestedTransactionNotSupportedException: JpaDialect does not support savepoints`. Spring's
   default `JpaTransactionManager`/`HibernateJpaDialect` combination does not support this.
3. **Fixed** by replacing the JPA `save`/`saveAndFlush` path entirely with a native
   `INSERT ... ON CONFLICT (event_key) DO NOTHING` query
   (`ProcessedEventRepository.insertIfNew`), returning the affected-row count (1 = new, 0 =
   duplicate). Postgres never raises a constraint violation for the conflicting row at all under
   this form, so there is nothing to catch and nothing that can mark any transaction rollback-only.
   `@Transactional` reverts to plain default `REQUIRED` propagation, now genuinely correct.
   Re-ran the same scratch test: **8 of 8 threads resolved correctly (exactly one `true`, seven
   `false`, zero exceptions)**, and the external-transaction-join/rollback proof also passed.

This is disclosed in full because it directly contradicts the frozen brief's own Finding #1
resolution and Finding #6's stated propagation choice — both are superseded by this design, not
silently replaced. `IdempotencyGuard`'s own Javadoc documents all three attempts and why the first
two were ruled out, so a future reader doesn't rediscover the same dead ends.

## Verification performed

- `mvn -pl services/notification clean verify` — 68 tests, 0 failures, clean `package`/`repackage`
  (unchanged from Phase 11's own final T01-T03 count; T04 adds no new committed test yet).
- The real `ProcessedEvent` entity was validated by Hibernate's own `ddl-auto=validate` against the
  real, T02-migrated `processed_events` table during every Testcontainers-backed test run in this
  suite — a column-name or type mismatch would have failed loudly at context startup, not silently.
- `V4` migrated successfully in every Testcontainers run (`Successfully applied 4 migrations ...
  now at version v4`).
- The scratch verification (concurrent-call correctness, external-transaction-join/rollback
  correctness) — described above — passed on the final design; the file itself was deleted before
  this commit, per `git status -s` showing no trace of it.

## Acceptance criteria mapping

- **AC1** — `ProcessedEvent` maps exactly onto the 3 existing columns, explicit schema, client-
  assigned `@Id`. ✅ (validated against the real schema by Hibernate itself, not just by inspection.)
- **AC2** — `recordIfNew` returns `true` exactly once per key, `false` thereafter — now genuinely
  proven under real concurrency, not just claimed. ✅
- **AC3** — `@Transactional` (default `REQUIRED`) — provably joins an already-open caller
  transaction (scratch test's own rollback proof). ✅
- **AC4** — `processedAt` from the injected `Clock`. ✅
- **AC5** — `V4` grants `notification_app` `INSERT, SELECT` on `processed_events`; `UPDATE`/`DELETE`
  still denied — proven by the new dedicated test. ✅
- **AC6** — both a unit test and a Testcontainers integration test are still planned for Phase 10, per
  this phase's own "no tests" rule; their exact shape is now grounded in a working design rather than
  the one Phase 5 planned around (Phase 10 must test `insertIfNew`'s own row-count contract, not a
  caught exception).
