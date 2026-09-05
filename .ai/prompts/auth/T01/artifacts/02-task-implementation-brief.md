# auth · T01 · Phase 2 — Task Implementation Brief (TIB)

## Task

**Schema V5.** Add `V5__lockout_cleanup_and_shedlock.sql` with the ShedLock table and lockout index.
Run `mvn -pl services/auth flyway:migrate` against the local Docker Compose Postgres.

## Purpose

Provision two pieces of schema ahead of the tasks that need them: an index that makes scanning for
currently-locked accounts efficient (feeds the lockout state machine, task #11), and a `shedlock`
table sized to ShedLock's standard JDBC schema (feeds the multi-replica scheduled cleanup job, task
#30). This is a pure, additive migration — no application code changes.

## Scope

**In:**
- New Flyway migration `V5__lockout_cleanup_and_shedlock.sql` containing exactly the SQL given
  verbatim in `design.md` §4c: `idx_lockout_state_locked_until` (partial index) and `shedlock`
  (table).
- Running the migration against the local Docker Compose Postgres and confirming it applies
  cleanly.

**Out:**
- Any lockout logic, `LockoutStateMachine`, `LockoutService`, or scheduled-job code — those are
  tasks #11 and #30.
- Any change to `V1`–`V4` (immutable, L1).
- Adding a ShedLock library dependency or `@SchedulerLock`-annotated code — table provisioning only.
- Adding the `flyway-maven-plugin` — open question below; not assumed in scope.

## Business Rules

- **R17.** 5 failed attempts within a rolling 30-minute window → `LOCKED` for 15 minutes,
  `lock_count` incremented, `account.locked` audited. *(This task only supports the efficient query
  this rule needs; it does not implement the rule.)*
- **R40.** Scheduled cleanup job hard-deletes expired verification tokens, old refresh-token
  families/archives, and stale ShedLock rows. *(This task only provisions the `shedlock` table the
  job — task #30 — will coordinate through.)*

## Locked Decisions

- **L1. Immutability of existing migrations.** `V1`–`V4` are immutable; this task's only schema
  change is the new `V5` file.

## Dependencies

- `flyway-core`, `flyway-database-postgresql` (already in `services/auth/pom.xml`).
- PostgreSQL 16 via `services/auth/compose.local.yaml` (db/user `checky`, port 5432 — matches
  `application.properties` defaults).
- **Not currently present:** `flyway-maven-plugin`, required for the task statement's literal
  `mvn -pl services/auth flyway:migrate` goal. See Open Questions.

## Inputs

- `design.md` §4c — verbatim SQL for the migration (authoritative; copy exactly, do not
  paraphrase).
- `V1__auth_baseline_schema.sql` — defines `lockout_state` (read-only reference for the new index's
  target table/column).

## Outputs

- `services/auth/src/main/resources/db/migration/V5__lockout_cleanup_and_shedlock.sql`.
- Applied schema in the local Postgres instance: one new index, one new table.

## State Changes

None to existing data. Additive only: one new index on `lockout_state`, one new empty table
(`shedlock`). No row in any existing table is modified.

## Files to Create

- `services/auth/src/main/resources/db/migration/V5__lockout_cleanup_and_shedlock.sql`

## Files to Modify

None.

## Files NOT to Modify

- `services/auth/src/main/resources/db/migration/V1__auth_baseline_schema.sql`
- `services/auth/src/main/resources/db/migration/V2__refresh_token_family_tracking.sql`
- `services/auth/src/main/resources/db/migration/V3__rbac_key_on_account_uuid.sql`
- `services/auth/src/main/resources/db/migration/V4__audit_key_on_account_uuid.sql`
- Any Java source file (this task has no code component).

## Acceptance Criteria

- **AC1 (R40-supporting).** `shedlock` table exists with columns `name` (PK), `lock_until`,
  `locked_at`, `locked_by`, matching ShedLock's default JDBC schema.
- **AC2 (R17-supporting).** `idx_lockout_state_locked_until` exists as a partial index on
  `lockout_state(locked_until)` `WHERE locked_until IS NOT NULL`.
- **AC3 (L1).** `V1`–`V4` are unchanged; the only new migration file is `V5`.
- **AC4 (task statement).** The migration applies cleanly against the local Docker Compose Postgres
  with no data loss and no alteration of existing tables.

## Required Tests

None named in `package.md` §8 map to this task. Verify by running the migration and confirming
`mvn -pl services/auth verify` stays green (existing suite unchanged — this task adds no tests of
its own; the schema's consumers author their tests in tasks #11 and #30).

## Constraints

- **Migration immutability (L1):** additive-only; no edits to `V1`–`V4`.
- **Naming/style:** follow the house style already in `V2`–`V4` (short header comment stating intent
  and additivity, `TIMESTAMPTZ` types, explicit constraints).
- **No transaction/thread-safety/null-handling concerns** — this task has no runtime code.
- **Module boundaries:** N/A — no Java module touched.

## Open Questions (blockers only; else "No blockers")

- **Potential blocker:** `flyway-maven-plugin` is not configured in `services/auth/pom.xml` or the
  root `pom.xml`, so the task statement's literal `mvn -pl services/auth flyway:migrate` command has
  nothing to invoke today. Needs a decision at the Phase 4 human freeze gate: (a) add the plugin as
  part of this task, or (b) treat Spring Boot's autoconfigured migrate-on-boot as satisfying the
  task's intent and treat the stated command as illustrative. Not assumed here.
