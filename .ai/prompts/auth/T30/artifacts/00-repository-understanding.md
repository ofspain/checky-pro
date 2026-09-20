<!-- MODEL: Claude Sonnet — Phase 0 (Repository Understanding). -->

# auth · T30 · Phase 0 — Repository Understanding

## 1. Architecture summary

Same `auth-service` stack as every prior task (Spring Boot 3.5.4, Java 21, Spring Data JPA,
Postgres, Flyway). Scheduling infrastructure already exists at a basic level:
`AuthServiceApplication` (the `@SpringBootApplication` class) already carries `@EnableScheduling`,
and `events/OutboxRelay.java` already runs a `@Scheduled(fixedDelayString = "...")` method today —
but **without any ShedLock guard**, since the outbox relay's own idempotency (published-flag check)
makes concurrent execution across replicas harmless for that specific job. T30's job has no such
built-in idempotency (a delete is a delete), which is exactly why the task statement requires a
ShedLock-annotated job specifically.

## 2. Existing code this task touches

- **`shedlock` table already exists** (`V5__lockout_cleanup_and_shedlock.sql`, created back at T01
  per this table's own migration comment: "ShedLock for multi-replica scheduled cleanup ...
  referenced in target-design.md §7 must not run concurrently across pods"). Exact shape:
  `name VARCHAR(64) PRIMARY KEY, lock_until TIMESTAMPTZ NOT NULL, locked_at TIMESTAMPTZ NOT NULL,
  locked_by VARCHAR(255) NOT NULL` — the standard ShedLock JDBC-provider table shape.
- **The ShedLock *library* itself is NOT on the classpath.** Grepped `pom.xml` for
  `net.javacrumbs`/`shedlock` — no hits. The table exists; the dependency and the
  `@EnableSchedulerLock`/`@SchedulerLock` wiring do not. This task will need to add
  `shedlock-spring` + a JDBC-template provider artifact as new Maven dependencies.
- **`verification_tokens` table** (V1, `account.VerificationToken`/`VerificationTokenRepository`)
  — has `expires_at TIMESTAMPTZ NOT NULL`, exactly the column the cleanup job needs to hard-delete
  against.
- **`refresh_token_family` / `refresh_token_archive`** (V2, `token` package) — `refresh_token_family.revoked_at`
  is the column to filter "old revoked families" by age; `refresh_token_archive` has
  `family_id UUID NOT NULL REFERENCES refresh_token_family(family_id) ON DELETE CASCADE` — so
  deleting an old revoked family row already cascades to delete its own archive rows
  automatically. Whether "archives" in the task statement means only this cascade, or *also*
  independently pruning archive rows for families that are still active but have very old
  `superseded_at` entries, is not resolved by the schema alone — flagged as an open question.
- **`Awaitility` is already a test dependency** (`pom.xml`, `org.awaitility:awaitility`) — the task
  statement's "Integration-test with Awaitility" has its dependency already satisfied, no new test
  dependency needed.

## 3. Established patterns to follow

- **Configuration is exact and already specified in `design.md`**, not left to this task to invent:
  ```
  themistra.auth.cleanup.cron=0 2 * * *
  themistra.auth.cleanup.token-retention-days=7
  themistra.auth.cleanup.family-retention-days=90
  ```
  (flat `application.properties` keys, matching this service's universal convention — never YAML).
  `target-design.md` §7 independently confirms "Nightly cleanup job (ShedLock-guarded — multi-replica
  EKS...)", consistent with the `0 2 * * *` cron.
- **L1 (LOCKED)**: the V1–V4 migrations are immutable; any new schema work is delivered "only as a
  follow-up migration named `V5__...`" — and `V5__lockout_cleanup_and_shedlock.sql` **already
  exists** with exactly the `shedlock` table this task needs. Since nothing this task requires is
  missing from the schema, the correct reading of L1 for T30 is straightforward: **no new
  migration should be added at all** — adding a `V6` would not violate L1's letter (L1 only
  constrains V1-V4's immutability) but a fresh migration isn't needed since V5 already covers it.
- **Existing scheduled-job precedent**: `OutboxRelay` shows the established shape for a
  `@Component`-annotated class with a single `@Scheduled` method reading its interval from a
  `themistra.auth.*` property with a sensible default. T30's job should follow the same
  `@Component` + externally-configured-cron shape, adding `@SchedulerLock(name = "...")` on top.
- **`@ConfigurationProperties` pattern**: every other feature area with tunable settings
  (`VerificationTokenProperties`, `ApiKeyProperties`, lockout config) uses a validated
  `@ConfigurationProperties` class rather than raw `@Value` injection — likely the expected shape
  for the three new `themistra.auth.cleanup.*` properties too, though `OutboxRelay`'s own interval
  uses a bare `@Value`-style `${...:default}` SpEL string directly in the `@Scheduled` annotation,
  so there's already at least one precedent for the lighter-weight approach too. Not decided here.
- **Repositories already expose (or can trivially expose) the needed delete/count queries**:
  `VerificationTokenRepository`, `RefreshTokenFamilyRepository` (package-private, `token` package)
  would each need a new derived-delete method (e.g. `deleteByExpiresAtBefore`,
  `deleteByRevokedAtBefore`) — none currently exist, confirmed by grep.

## 4. Testing conventions

- Unit tests: plain JUnit + Mockito, fixed `Clock`, matching every prior task.
- Integration tests: `@SpringBootTest` + Testcontainers Postgres, same as
  `RefreshTokenFamilyIntegrationTest`/`ApiKeyServiceIntegrationTest`. The task statement's own
  instruction to use **Awaitility** is new relative to every prior task in this stretch —
  appropriate here because a scheduled job's execution is asynchronous relative to the test thread
  (unlike every prior task's synchronous HTTP-call-and-assert shape), so the test needs to poll
  until the job has run rather than asserting immediately.
- ArchUnit (`ArchitectureTest`) — no expected new exposure; the new job class would live in
  whichever package owns cross-cutting cleanup (likely a new package or an existing one like
  `common`), touching `account`/`token` only via their public repository interfaces (or, for
  `RefreshTokenFamilyRepository`, needing to live inside `token` itself since that repository is
  package-private — same constraint T28/T29 already navigated).
- **Environmental note carried forward from T25-T29**: Docker has been unavailable this entire
  session. A ShedLock-guarded job's own correctness (multi-instance mutual exclusion) is
  specifically the kind of behavior that's very hard to prove meaningfully without a real
  Postgres-backed integration test — this task's Awaitility-based integration test will very likely
  join the now five-deep backlog of written-but-unexecuted Testcontainers suites.

## 5. Known gaps / unknowns

- **I do not know** the exact deletion semantics the spec author intends for "old revoked
  families/archives" — specifically whether archive-row pruning is purely a side effect of
  cascading family deletes, or an independent age-based prune of `refresh_token_archive.superseded_at`
  regardless of the owning family's revocation status. `design.md`/`requirements.md`/`target-design.md`
  don't spell this out beyond the single retention property
  `themistra.auth.cleanup.family-retention-days`. Flagged for Phase 1/2.
- **I do not know** whether "stale ShedLock rows" means rows whose `lock_until` has passed by some
  margin (a genuine cleanup of an operational table), or something else — ShedLock's own JDBC
  provider does not auto-delete rows on its own, so this is a real, first-class piece of this
  task's own job logic, not something a library handles for free. No specific retention period is
  given anywhere in the spec package for this specific sub-task; likely reuses
  `token-retention-days` or needs its own threshold — flagged for Phase 1/2.
- **Confirmed, not just assumed**: grepped the parent `checky-pro` POM and every sibling service
  module's `pom.xml` (crypto, notification, payment) — no ShedLock reference exists anywhere in the
  monorepo. This will be a genuinely new dependency addition with no existing version pin to
  inherit; Phase 2 will need to pick both the exact artifacts (`shedlock-spring` +
  `shedlock-provider-jdbc-template`, the standard pairing for a plain `JdbcTemplate`-backed lock
  provider) and an explicit version, since nothing upstream constrains the choice.
- **No named test conflict this time**: `package.md` §8's `shouldCleanupExpiredTokensAndFamilies`
  row is mapped to R36 in the spec file text, but T30's own header correctly cites R40 as this
  task's scoped requirement — the now-familiar §8 numbering bug (recurring since early tasks),
  re-confirmed rather than assumed.

Do not design and do not extract requirements yet — that is Phase 1.

---

**Phase 0 complete — repository understanding written.** Proceed to Phase 1 (Specification
Extraction) on approval.
