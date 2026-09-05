# auth · T01 · Phase 1 — Specification Extraction

Consumes `artifacts/00-repository-understanding.md`. Task: add
`V5__lockout_cleanup_and_shedlock.sql` (ShedLock table + lockout index), run
`mvn -pl services/auth flyway:migrate` against the local Docker Compose Postgres. No design, no
implementation in this phase.

## Business Rules

- **R17.** When the failed-attempt counter reaches 5 failed attempts within a rolling 30-minute
  window, the system transitions the account to `LOCKED` for 15 minutes, increments `lock_count`,
  and records an `account.locked` audit event. *(T01's relevance: the new
  `idx_lockout_state_locked_until` partial index exists to make the "is this account currently
  locked" scan this rule depends on efficient — the rule itself is implemented later, in task #11.)*
- **R40.** When the scheduled cleanup job runs, the system hard-deletes expired verification
  tokens, refresh-token families/archives older than the configured retention, and stale ShedLock
  rows. *(T01's relevance: the new `shedlock` table is the multi-replica coordination primitive this
  rule's job — task #30 — will run under; T01 only provisions the table, not the job.)*

## Locked Decisions

- **L1. Immutability of existing migrations.** `V1`–`V4` under
  `services/auth/src/main/resources/db/migration/` are immutable. New schema work is delivered only
  as a new `V<n>__...` file — here, `V5`. This directly constrains T01: the task must not edit
  `lockout_state`'s existing definition (V1), only add an index on it.

## Files involved

**New file (this task creates it):**
- `services/auth/src/main/resources/db/migration/V5__lockout_cleanup_and_shedlock.sql` — content is
  given VERBATIM in `design.md` §4c: the `idx_lockout_state_locked_until` partial index
  (`WHERE locked_until IS NOT NULL`) and the `shedlock` table (`name` PK, `lock_until`, `locked_at`,
  `locked_by`).

**Existing files to read only, not modify:**
- `V1__auth_baseline_schema.sql` — defines `lockout_state(account_id, failed_attempts,
  last_failed_at, locked_until, lock_count)`, the table the new index targets. Confirmed unchanged
  by this task.
- `services/auth/src/main/resources/application.properties` — `spring.flyway.default-schema=auth`,
  `spring.flyway.schemas=auth`, `spring.flyway.create-schemas=true`; datasource points at
  `jdbc:postgresql://localhost:5432/checky`.
- `services/auth/compose.local.yaml` — local Postgres 16-alpine, db/user `checky`, port 5432;
  matches the `application.properties` defaults, so "the local Docker Compose Postgres" in the task
  statement resolves unambiguously to this file.

**No new Java files.** `design.md` §6's package/file map lists no `account`/`authn`/`mfa` classes for
T01 — those belong to later tasks that consume this schema.

## Dependencies

- Flyway (`flyway-core`, `flyway-database-postgresql`) — already present in
  `services/auth/pom.xml` as regular dependencies (drive Spring Boot's migrate-on-boot behavior).
- PostgreSQL 16 — via `compose.local.yaml`.
- **Gap carried from Phase 0:** no `flyway-maven-plugin` is configured in `services/auth/pom.xml` or
  the root `pom.xml`. The task statement's literal command,
  `mvn -pl services/auth flyway:migrate`, invokes that plugin's goal, not Spring Boot's
  autoconfigured migration. This is a real dependency of the task-as-written that does not currently
  exist in the build. Not resolved here — see Open Questions.

## Acceptance Criteria

- **AC1 (R40-supporting).** `shedlock` table is created with exactly the columns ShedLock's standard
  JDBC schema expects (`name` PK, `lock_until`, `locked_at`, `locked_by`), so task #30's scheduled
  job can use it unmodified later.
- **AC2 (R17-supporting).** `idx_lockout_state_locked_until` is created as a partial index
  (`WHERE locked_until IS NOT NULL`) on `lockout_state.locked_until`.
- **AC3 (L1).** The change lands only in a new `V5__lockout_cleanup_and_shedlock.sql`; `V1`–`V4` are
  byte-for-byte unchanged.
- **AC4 (task statement, operational).** The migration runs cleanly against the local Docker Compose
  Postgres and is additive — no existing table is altered, no data loss, no downtime implication
  (matches `package.md` §10 "Schema" rollback notes, which already describe V5 as index + ShedLock
  table only).

## Tests required

None named in `package.md` §8 map to T01 (confirmed in the task header and in Phase 0). No new
unit/integration test is required by this task; its own acceptance bar is operational — the
migration applies without error and `mvn -pl services/auth verify` stays green afterward (existing
suite, unchanged). Later tasks (#11–#13 for the lockout index's consumer, #30 for the ShedLock
table's consumer) carry their own named tests against this schema.

## Open Questions

- **Blocker candidate:** is the missing `flyway-maven-plugin` configuration something T01 is
  expected to add (so the literal command in the task statement becomes runnable), or was the task
  statement written assuming Spring Boot's autoconfigured migrate-on-boot is an acceptable
  substitute for the stated Maven goal? The spec package does not say either way. Flagging for the
  Phase 4 human freeze gate rather than assuming.
- Not a blocker, noted for completeness: the four contracts in this task's header
  (`contracts/api/auth.yaml`, `contracts/api/token-claims.md`,
  `contracts/events/auth/email-requested.v1.schema.json`,
  `contracts/events/auth/security-audit.v1.schema.json`) are not dependencies of T01 — they are
  outputs of tasks #33/#34, attached to every task's header by the generator's service-wide (not
  per-task) contract scan. See `AI_CONTEXT_ANALYSIS.md` at the repo root. No action needed in this
  task.
