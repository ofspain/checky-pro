# notification · T14 · Phase 6 — Implementation Notes

Implemented close to the Phase 5 plan, with one small, necessary file addition disclosed up front,
plus three real deviations forced by reality — all disclosed in full below, not hidden. ShedLock's
own real JDBC-provider SQL (`net.javacrumbs.shedlock:shedlock-sql-support`'s own source) and the
exact Maven coordinates actually available (`7.9.0`, confirmed against the local Maven repository,
not guessed) were verified directly before writing any migration or dependency, matching the
discipline already established in T12/T13.

## Files created

- `delivery/DeliveryRetry.java` — entity over `delivery_retry`; the one genuinely mutable entity in
  this module (`reschedule`); `toString()` deliberately left as `Object`'s own default so
  `eventDataJson` (which carries the same raw token the original event did) can never leak via a
  careless log line.
- `delivery/DeliveryRetryRepository.java` — package-private, ordered
  `findByNextAttemptAtLessThanEqualOrderByNextAttemptAtAscIdAsc`.
- `delivery/RetryScheduler.java` — `@Scheduled` + `@SchedulerLock("retry-scheduler")`; `sweep()`
  itself is not `@Transactional` (one transaction per row, per the frozen brief's own constraint);
  `processOne` is.
- `common/config/ShedLockConfig.java` — **a small, necessary addition beyond the Phase 5 plan's own
  named files, disclosed here, not silently added.** The plan's own "Dependencies" section already
  anticipated "exact Maven coordinates confirmed during implementation" for the ShedLock starter
  itself, but did not spell out that `@SchedulerLock` also needs a real `LockProvider` bean
  somewhere in the context. One new, minimal `@Configuration` class (mirrors `SesClientConfig`'s own
  exact T12 precedent: one class, one `@Bean`) was the natural, consistent place for it — not
  `NotificationServiceApplication` itself, which has never held a `@Bean` method in this codebase.

## Files modified

- `delivery/DeliveryLog.java` — constructor gains `short attempt` (Finding #1), replacing the
  hardcoded `1`.
- `delivery/DeliveryOrchestrator.java` — the largest change: new `DeliveryOutcome` enum (5 values);
  new private `attemptSend` (the one shared classification helper both the original dispatch path
  and `replay` call); new private `scheduleFirstRetry`; new package-visible `replay` and
  `recordUnrecoverableFailure`; every existing `save` call site threaded an explicit `attempt`
  argument.
- `common/config/RetryProperties.java` — gains `schedulerIntervalSeconds`.
- `application.properties` — new property; the existing three retry values' own comment now says
  "CONFIRMED... no longer placeholders" (AC7/Q6).
- `NotificationServiceApplication.java` — `@EnableScheduling` + `@EnableSchedulerLock`.
- `pom.xml` — `shedlock-spring`/`shedlock-provider-jdbc-template` `7.9.0`.
- `NotificationBaselineMigrationIntegrationTest.java` — see the UNGRANTED_TABLES deviation below.
- `T01SkeletonRegressionTest.java` — authorized file-inventory list (46 → 50); the pre-existing
  `doesNotContain("shedlock-...")`/`doesNotContain("@EnableScheduling"/"@EnableSchedulerLock")`
  assertions (both left over from T03, written when no scheduled job existed yet) flipped to
  `contains` — see the deviation below, these were genuinely stale, not newly broken by a mistake.
- `delivery/DeliveryOrchestratorTest.java` — mechanical constructor-signature updates at 2 call
  sites, plus new tests for the new classification/retry-scheduling/replay behavior.
- `delivery/DeliveryOrchestratorIntegrationTest.java` — new `ControllableEmailTransport`
  (`@Primary`-overrides `FakeEmailTransport` for a single-injection-point, delegates to it on
  success so every pre-existing test's own assertions against `fakeEmailTransport.sentMessages()`
  stay completely unaffected) plus the two named tests.

## Deviation #1 — two pre-existing T01 assertions were stale, not broken by this task

`T01SkeletonRegressionTest.applicationClassIsBareWithOnlyTheMainMethod` and
`finalNameAndFlywayPluginMirrorTheSiblingConvention`'s own sibling assertion block both asserted
`doesNotContain("@EnableScheduling")`/`doesNotContain("shedlock-...")` — written at T03, correctly
describing *that* task's own state, with their own comments explicitly saying "until task 14." This
task is exactly the one named in those comments. Both flipped to `contains` once this task's own
annotations/dependencies landed - not a new finding, the comments had already predicted this exact
moment.

## Deviation #2 — the last two ungranted tables closed the whole `UNGRANTED_TABLES` mechanism

Once `V10`/`V11` granted `delivery_retry`/`shedlock`, **no table in this schema remains fully
ungranted** - `UNGRANTED_TABLES` would have been an empty list. Rather than leave
`notificationAppHasNoAccessAtAllToTablesOutsideAc2Scope` and its own three now-fully-dead helper
methods (`noWhereUpdateStatementFor`/`noWhereDeleteStatementFor`/`minimalInsertFixtureFor`, each
left with only a `default -> throw` branch) testing an empty set, they were removed entirely,
replaced by two new dedicated grant-shape tests
(`notificationAppCanInsertSelectUpdateAndDeleteOnDeliveryRetry`,
`notificationAppCanInsertSelectAndUpdateButNotDeleteOnShedlock`) — mirroring exactly how T13 removed
`inapp_notifications`' own three stale switch branches when its own grant landed, just completing
the pattern for the last two tables rather than one.

## Deviation #3 — a real grant mistake, found empirically: ShedLock needs SELECT too

**Not anticipated by Phase 4 or Phase 5.** The frozen brief's own reasoning ("the provider never
runs a standalone SELECT") was correct about the provider's own code, but incomplete about Postgres's
own privilege model: both SQL statements the provider issues
(`INSERT ... ON CONFLICT (name) DO UPDATE ... WHERE lock_until <= :now` and
`UPDATE ... WHERE name = :name AND lock_until <= :now`) have a `WHERE` clause referencing table
columns, and Postgres requires `SELECT` on any column an `UPDATE`'s own `WHERE` predicate reads -
regardless of whether anything in the application code ever issues a literal `SELECT` statement.

**Found** by the real integration test itself: `V11`'s first version (`GRANT INSERT, UPDATE`, no
`SELECT`) made `notificationAppCanInsertAndUpdateButNotSelectOrDeleteOnShedlock`'s own UPDATE
assertion fail with a real `permission denied for table shedlock` error - not a passing test with a
wrong assumption baked in, a real, caught, empirical contradiction of the original design.

**Fixed** by widening `V11` to `GRANT SELECT, INSERT, UPDATE` and renaming the test to
`notificationAppCanInsertSelectAndUpdateButNotDeleteOnShedlock`, with the migration's own comment
updated to explain *why* SELECT is required despite the provider never issuing one directly.

## Deviation #4 — a real production bug, found empirically: a detached-entity mutation was silently lost

**Not anticipated by Phase 4 or Phase 5, and a more serious class of bug than Deviation #3** - this
one would have silently broken every real retry-reschedule in production, not merely failed a grant
test. `RetryScheduler.processOne`'s own `reschedule(...)` call mutates the `DeliveryRetry` instance
`sweep()` passes in - but `sweep()` itself is deliberately NOT `@Transactional` (the frozen brief's
own explicit constraint), so the row it queries is never a JPA-managed entity by the time
`processOne`'s own (freshly-started) transaction begins. Mutating a detached entity's fields and
relying on dirty-checking to persist the change on commit - the original Javadoc's own claim - simply
does not work; JPA only tracks changes to entities managed within the *current* persistence context.

**Found** by `DeliveryOrchestratorIntegrationTest.shouldStopRetryingAndDeadLetterAfterMaxAttempts`:
a real, refetched `DeliveryRetry` row showed `attempt=1` where the test expected `attempt=2` after
one real `processOne` call - a genuine contradiction between what the code claimed and what a real
Postgres round-trip showed, not a flaky or misconfigured test.

**Fixed** by adding an explicit `retryRepository.save(retry)` call (a merge, since the detached
entity already has a real id) immediately after `reschedule(...)` inside the `TRANSIENT_FAILURE`
branch of `processOne`. `DeliveryRetry`'s own Javadoc on `reschedule` was corrected to state the real
requirement (the caller must explicitly save) rather than the disproven dirty-checking claim.

## Mapping to acceptance criteria

- **AC1/AC10/AC13**: `attemptSend`'s own `IllegalArgumentException`-is-permanent rule; `DeliveryLog`
  now carries a real `attempt`; the exhaustion check (`nextAttemptNumber > maxAttempts`) is evaluated
  identically on the original attempt and every replay.
- **AC2/AC3/AC14**: `scheduleFirstRetry` inserts the very first row only on `TRANSIENT_FAILURE`;
  `maxAttempts=1` dead-letters on the first attempt with no row ever inserted (verified by a
  dedicated unit test with a real, separately-constructed single-`maxAttempts` orchestrator).
- **AC4**: every outcome - success, failure, dead-letter - appends a new `DeliveryLog` row; nothing
  is ever updated.
- **AC5**: `RetryScheduler`/`DeliveryRetry` never call a channel bean directly; `replay` is the only
  path back to `NotificationChannel`, and it reuses `attemptSend`.
- **AC6**: `@SchedulerLock(name = "retry-scheduler", ...)` on `sweep()`.
- **AC7**: `application.properties`'s own retry block is now explicitly marked confirmed, not
  placeholder.
- **AC8**: every new `save` call site routes through the one existing `SecretSafeLogging.redact()`-
  wrapping helper - no new, parallel redaction path.
- **AC9**: `sweep()`'s own per-row `try/catch` (verified by a dedicated unit test with a
  deliberately-throwing first row).
- **AC11**: `replay` re-checks `preferenceResolver.resolve` before re-rendering/re-sending.
- **AC12**: `processOne`'s own poison-pill `catch (JsonProcessingException e)` branch.

## Verification

- `mvn -pl services/notification compile` / `test-compile` — clean.
- `mvn -pl services/notification clean verify` — 341 tests, 0 failures, 0 errors. Two genuinely
  wrong test expectations (an exponential-backoff exponent mix-up against the already-correct
  production code) and two `verifyNoInteractions`-on-a-constructed-mock mistakes (the orchestrator's
  own constructor already calls `.channel()` on every channel mock to build `channelsByName`, a
  pre-existing, established reason this file always uses `verify(x, never()).send(...)` instead of
  `verifyNoInteractions` for the two channel mocks specifically) were caught and fixed before this
  count was reached - both were test-authoring mistakes, not production bugs, found by actually
  running the suite rather than assumed correct from inspection.
