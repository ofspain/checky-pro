STATUS: FROZEN

# auth · T01 · Phase 4 — Frozen Task Brief

Approved by: femi (human approval gate, this session). Consumes `artifacts/02-task-implementation-brief.md`
and `artifacts/03-design-challenge.md`. This is the terminal specification for T01 — downstream
phases (5–13) implement against this document only and may not renegotiate it.

## Task

**Schema V5.** Add `V5__lockout_cleanup_and_shedlock.sql` with the ShedLock table and lockout index.
Run `mvn -pl services/auth flyway:migrate` against the local Docker Compose Postgres.

## Purpose

Provision two pieces of schema ahead of the tasks that need them: a partial index that supports
efficient expired-lock scans (`design.md` §5, "for efficient expired-lock scans"; no task currently
runs such a scan — see Constraints), and a `shedlock` table sized to ShedLock's standard JDBC schema
for the multi-replica scheduled cleanup job (task #30). Additionally, this task now provisions the
`flyway-maven-plugin` so the task's own stated verification command is runnable (Finding 1,
resolved below).

## Phase 3 findings — dispositions

| # | Finding | Severity | Disposition | Reason |
|---|---|---|---|---|
| 1 | `flyway-maven-plugin` missing; literal command unrunnable | Blocker | **ACCEPTED — Option A** | Human decision (this session): add the plugin rather than redefine AC4. |
| 2 | Verification method manual/untestable | Medium | **ACCEPTED** | Concrete, low-risk addition; folded into Acceptance Criteria. |
| 3 | Index rationale doesn't match any stated query pattern | Medium | **ACCEPTED (documentation only)** | `design.md` §5 does name a rationale ("efficient expired-lock scans") even though no task consumes it yet; `package.md` §10 confirms "no application dependency on the index at runtime." Documented as forward-provisioned, not removed — the index is part of the LOCKED verbatim SQL (L1/§4c) and stays. |
| 4 | Plugin datasource binding unspecified | Medium | **ACCEPTED** | Folded into Finding 1's Option A resolution below. |
| 5 | `IF NOT EXISTS` deviates from V1–V4 house style | Low | **REJECTED (as a change); observation kept as a note** | `design.md` §4c is titled "VERBATIM artifacts — copy exactly, do not paraphrase." The migration SQL is LOCKED verbatim; removing `IF NOT EXISTS` would violate that instruction. The deviation is intentional per the spec and is now documented rather than "fixed." |
| 6 | Compose startup prerequisite unstated | Low | **ACCEPTED** | Trivial addition to Dependencies. |
| 7 | ShedLock `TIMESTAMPTZ` assumption unverified | Low | **ACCEPTED — deferred to task #30** | SQL type is fixed by the LOCKED verbatim artifact; not a T01 decision. Logged as an open item with an owner (task #30 implementer must confirm the chosen `LockProvider` handles `TIMESTAMPTZ` correctly). |
| 8 | No failure-mode/rollback guidance | Low | **ACCEPTED** | `package.md` §10 already documents the manual rollback; cited directly rather than re-authored. |

## Scope

**In:**
- New Flyway migration `V5__lockout_cleanup_and_shedlock.sql`, content copied verbatim from
  `design.md` §4c (`idx_lockout_state_locked_until` partial index; `shedlock` table). `IF NOT EXISTS`
  in both statements is kept exactly as specified — do not remove (Finding 5, rejected).
- Add `flyway-maven-plugin` to `services/auth/pom.xml`, configured against the local Docker Compose
  Postgres, so `mvn -pl services/auth flyway:migrate` runs as the task statement literally describes
  (Finding 1, Option A).
- Start local dependencies (`docker compose -f services/auth/compose.local.yaml up -d --wait`) before
  running the migration (Finding 6).
- Run the migration and verify with the introspection queries below (Finding 2).

**Out:**
- Any lockout logic, `LockoutStateMachine`, `LockoutService`, or scheduled-job/ShedLock-annotated
  code — tasks #11 and #30.
- Any change to `V1`–`V4` (immutable, L1).
- Any ShedLock library dependency (`shedlock-provider-jdbc-template` or similar) — table
  provisioning only; task #30's concern.
- Removing `IF NOT EXISTS` from the V5 SQL (Finding 5, rejected — would violate the VERBATIM
  instruction in `design.md` §4c).

## Business Rules

- **R17.** 5 failed attempts within a rolling 30-minute window → `LOCKED` for 15 minutes,
  `lock_count` incremented, `account.locked` audited. T01 only provisions the supporting index;
  it does not implement this rule (implemented in task #11).
- **R40.** Scheduled cleanup job hard-deletes expired verification tokens, old refresh-token
  families/archives, and stale ShedLock rows. T01 only provisions the `shedlock` table (implemented
  in task #30).

## Locked Decisions

- **L1. Immutability of existing migrations.** `V1`–`V4` are immutable; this task's only schema
  change is the new `V5` file, copied verbatim from `design.md` §4c (including `IF NOT EXISTS`,
  per Finding 5's disposition).

## Dependencies

- `flyway-core`, `flyway-database-postgresql` (already present).
- **New:** `flyway-maven-plugin` in `services/auth/pom.xml`, configured with:
  ```xml
  <plugin>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-maven-plugin</artifactId>
    <configuration>
      <url>jdbc:postgresql://localhost:5432/checky</url>
      <user>checky</user>
      <password>checky-local-only</password>
      <schemas>auth</schemas>
    </configuration>
  </plugin>
  ```
  (Values match `compose.local.yaml` / `application.properties` local defaults exactly — Finding 4.)
- Local Postgres must be running before migration: `docker compose -f services/auth/compose.local.yaml up -d --wait` (Finding 6).

## Inputs

- `design.md` §4c — verbatim SQL for the migration (authoritative; copy exactly).
- `V1__auth_baseline_schema.sql` — defines `lockout_state` (read-only reference).
- `package.md` §10 — existing rollback guidance for V5 (cited, not re-authored — Finding 8).

## Outputs

- `services/auth/src/main/resources/db/migration/V5__lockout_cleanup_and_shedlock.sql`
- Modified `services/auth/pom.xml` (flyway-maven-plugin block).
- Applied schema in the local Postgres instance: one new index, one new table.

## State Changes

None to existing data. Additive only: one new partial index on `lockout_state`, one new empty table
(`shedlock`). No row in any existing table is modified.

## Files to Create

- `services/auth/src/main/resources/db/migration/V5__lockout_cleanup_and_shedlock.sql`

## Files to Modify

- `services/auth/pom.xml` — add `flyway-maven-plugin` with the configuration above. (Changed from
  the Phase 2 TIB's "None," per Finding 1's accepted Option A.)

## Files NOT to Modify

- `V1__auth_baseline_schema.sql`, `V2__refresh_token_family_tracking.sql`,
  `V3__rbac_key_on_account_uuid.sql`, `V4__audit_key_on_account_uuid.sql`
- The root `pom.xml`
- Any Java source file (this task has no code component)

## Acceptance Criteria

- **AC1 (R40-supporting).** `shedlock` table exists with columns `name` (PK), `lock_until`,
  `locked_at`, `locked_by`.
- **AC2 (R17-supporting).** `idx_lockout_state_locked_until` exists as a partial index on
  `lockout_state(locked_until)` `WHERE locked_until IS NOT NULL`.
- **AC3 (L1).** `V1`–`V4` are unchanged; the only new migration file is `V5`, and its SQL matches
  `design.md` §4c verbatim, including `IF NOT EXISTS`.
- **AC4 (task statement + Finding 1/2).** `mvn -pl services/auth flyway:migrate` runs successfully
  against the local Docker Compose Postgres via the newly configured plugin. Verify with:
  ```sql
  SELECT indexname FROM pg_indexes
   WHERE schemaname = 'auth' AND indexname = 'idx_lockout_state_locked_until';
  SELECT table_name FROM information_schema.tables
   WHERE table_schema = 'auth' AND table_name = 'shedlock';
  ```
  and confirm `flyway_schema_history` shows exactly one successful row for version 5 (Finding 2, 8).

## Required Tests

None named in `package.md` §8 map to this task. Acceptance is operational (AC1–AC4 above) plus
`mvn -pl services/auth verify` staying green on the existing suite.

## Constraints

- **Migration immutability (L1):** additive-only; no edits to `V1`–`V4`.
- **VERBATIM (design.md §4c):** the V5 SQL, including `IF NOT EXISTS` on both statements, is copied
  exactly as given — this is a deliberate deviation from the plain `CREATE TABLE`/`CREATE INDEX`
  style in `V1`–`V4`, not an oversight to correct (Finding 5).
- **Failure handling:** if `flyway:migrate` fails partway, drop any partially created V5 objects
  (`DROP INDEX IF EXISTS auth.idx_lockout_state_locked_until;` / `DROP TABLE IF EXISTS auth.shedlock;`,
  per `package.md` §10) and re-run; the task is not done until `flyway_schema_history` shows one
  clean row for V5 (Finding 8).
- No transaction/thread-safety/null-handling concerns — this task has no runtime code.

## Open Questions

**No blockers remain.** The one blocker (Finding 1 / flyway-maven-plugin) is resolved above by human
decision. One item is explicitly deferred, with an owner:

- **Deferred to task #30:** confirm the ShedLock `LockProvider` selected for the scheduled cleanup
  job reads `TIMESTAMPTZ` columns (`lock_until`, `locked_at`) correctly, given the verbatim SQL fixes
  that type now (Finding 7). Not actionable in T01.

The four contracts in this task's header (`contracts/api/auth.yaml`, `contracts/api/token-claims.md`,
`contracts/events/auth/email-requested.v1.schema.json`,
`contracts/events/auth/security-audit.v1.schema.json`) remain confirmed non-dependencies of T01 (see
`AI_CONTEXT_ANALYSIS.md`); no change from Phase 1/2.
