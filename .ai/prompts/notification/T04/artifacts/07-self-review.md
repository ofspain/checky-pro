# notification · T04 · Phase 7 — Self-Review

## Files reviewed

- `consumer/{ProcessedEvent,ProcessedEventRepository,IdempotencyGuard}.java`
- `common/ClockConfig.java`
- `db/migration/V4__notification_app_processed_events_grant.sql`
- `T01SkeletonRegressionTest.java`, `NotificationBaselineMigrationIntegrationTest.java` (both diffs)

## Findings

### Finding 1 — `ProcessedEvent.create(...)` is now dead code

**Severity:** Medium

**Evidence:** `ProcessedEvent.java:35`. The Phase 6 mid-implementation redesign (native
`INSERT ... ON CONFLICT DO NOTHING` via `ProcessedEventRepository.insertIfNew`) means
`IdempotencyGuard.recordIfNew` never constructs a `ProcessedEvent` instance at all — the write path
bypasses the entity object entirely, binding `eventKey`/`eventType`/`processedAt` directly as native
query parameters. `grep -n "ProcessedEvent.create"` across `src/main` finds only the method's own
declaration — nothing calls it. `ProcessedEvent`'s own class Javadoc still says
"`IdempotencyGuard` is the only writer," which remains true in spirit (nothing else writes this
table) but is misleading about *how* — a reader would reasonably expect `create(...)` to be that
writer's own construction path, and it no longer is.

**Recommendation:** Either (a) remove `create(...)` entirely — the entity is now read-only in
practice (`existsById`/`findById`, inherited from `JpaRepository`, are the only methods actually
exercised against it), or (b) keep it with an explicit Javadoc note explaining it exists for a
plausible future direct-construction use case (e.g., a batch-backfill tool) that doesn't exist yet,
so a future reader doesn't mistake it for currently-dead, forgotten code. Not fixed here — Phase 9's
own call, since it's a judgment between two reasonable options, not an unambiguous bug.

### Finding 2 — no defense against first-level-cache staleness after the native write

**Severity:** Low

**Evidence:** `ProcessedEventRepository.java` — `insertIfNew` is a native, `@Modifying` query with no
`clearAutomatically`/`flushAutomatically` attribute. Native modifying queries bypass Hibernate's
persistence context entirely; if a future caller reads via `existsById`/`findById` on the *same*
`event_key` earlier in the same transaction/session (caching a "not present" result), then calls
`recordIfNew`, then reads again in the same transaction, the second read could theoretically be
served from the stale first-level cache rather than reflecting the just-inserted row. Low
probability given `IdempotencyGuard`'s own intended usage (call `recordIfNew` first, don't
pre-check), but not structurally prevented.

**Recommendation:** Add `@Modifying(clearAutomatically = true)` to `insertIfNew`, matching Spring
Data JPA's own recommended defensive pattern for native modifying queries that share a persistence
context with other reads. Cheap, no behavior change for the current (only) caller.

### Finding 3 — `processedAt`'s stored value was never actually asserted, only its presence

**Severity:** Low

**Evidence:** The Phase 6 scratch verification (deleted, not committed) proved `recordIfNew`'s
boolean-return and transaction-join behavior, but never retrieved a row afterward to confirm the
`Instant` passed in as `:processedAt` round-trips correctly through native-query parameter binding
into `TIMESTAMPTZ` and back out unchanged (precision, timezone). AC4 ("set from an injected `Clock`")
is satisfied by inspection of the code (the value passed IS `clock.instant()`), but the persisted
value's fidelity is unverified.

**Recommendation:** Phase 10's integration test should retrieve the row after a successful
`recordIfNew` (via `repository.findById(eventKey)`) and assert `getProcessedAt()` equals the fixed
clock's instant exactly, closing this gap with an automated test rather than leaving it as an
inspection-only claim.

## Verification performed

- `mvn -pl services/notification clean verify` — 68 tests, 0 failures, unchanged from Phase 6's own
  final record.
- Re-confirmed via `grep` that `ProcessedEvent.create` has exactly one match in `src/main` (its own
  declaration) — Finding 1's own dead-code claim is verified, not assumed.
