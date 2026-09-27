# notification · T02 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Proceeding to merge preparation.

## Commit title

`notification-service T02: notifications schema baseline + least-privilege notification_app role`

## Commit message

```
notification-service T02: notifications schema baseline + least-privilege role

Add V1__notifications_baseline.sql, a byte-for-byte transcription of
design.md §4c's notifications schema (contact_projection,
channel_preferences, templates, processed_events, delivery_log,
inapp_notifications, delivery_retry, shedlock). Add
V2__notification_app_role_and_grants.sql, a new passwordless
notification_app role granted INSERT+SELECT only on delivery_log (this
task's own literal scope) - owns nothing, connects only as a grantee, per
L3's append-only intent enforced at the DB-permission layer. Add
V3__seed_launch_templates.sql, 14 real seed rows (7 event mappings x 2
channels, version 1) for the launch templates.

Run mvn -pl services/notification flyway:migrate for real against the
shared local Postgres. Doing so surfaced a genuine citext/search_path
interaction (V1's own SET search_path TO notifications hides any type
installed in public, including citext, which auth's own migration already
installs there; Postgres allows only one installation per database) -
resolved by relocating the extension (ALTER EXTENSION citext SET SCHEMA
notifications), approved before touching this cross-service shared state
and verified not to break auth.accounts.email afterward. Documented as a
repeatable bootstrap step in services/notification/README.md.

Add NotificationBaselineMigrationIntegrationTest: 13 real
Testcontainers-Postgres tests proving schema completeness, the V1
byte-for-byte guarantee, grant/denial on every table (SELECT, INSERT,
UPDATE, DELETE), DDL denial, the owner/grantee ownership split, V2's own
SQL idempotency under direct re-execution, the citext pre-step landing
where V1 needs it, the flyway-maven-plugin's connection details, and V2's
secrets-discipline invariants (no committed password, no hardcoded
database name).

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Created**
- `services/notification/src/main/resources/db/migration/V1__notifications_baseline.sql`
- `services/notification/src/main/resources/db/migration/V2__notification_app_role_and_grants.sql`
- `services/notification/src/main/resources/db/migration/V3__seed_launch_templates.sql`
- `services/notification/src/test/java/com/themistra/notification/NotificationBaselineMigrationIntegrationTest.java`

**Modified**
- `services/notification/README.md` (Local development database section — citext bootstrap, migration, local-password steps)

**Process artifacts**
- `.ai/prompts/notification/T02/artifacts/00-12-*.md` — full 14-phase pipeline record (this file completes it).

## Summary

Establishes the `notifications` Postgres schema every later notification-service task builds on, a
least-privilege runtime role scoped to this task's own literal grant (`delivery_log` only — the
other 6 tables + `shedlock` are deliberately ungranted until their own consuming task, mirroring
crypto-service's incremental-grant pattern), and the 14 seed rows the template renderer (task 9)
will read. The real local migration was attempted and succeeded, not merely disclosed as blocked,
after resolving a previously-unknown `citext`/`search_path`/extension-singleton interaction with
`services/auth`'s own already-installed extension.

## Testing performed

- `mvn -pl services/notification -am verify` — 22 tests, 0 failures (9 `T01SkeletonRegressionTest` +
  13 `NotificationBaselineMigrationIntegrationTest`), `BUILD SUCCESS`.
- `mvn -pl services/notification flyway:migrate` — run for real against the shared local Postgres
  (`auth-postgres-1`), after `services/auth`'s own migrations and the `citext` relocation; succeeded.
- Two real mutation tests, both reverted clean (`git status -s` empty afterward): (1) an over-broad
  `UPDATE` grant added to `V2`, confirming the UPDATE/DELETE-denial checks fail on a real regression
  — this caught a genuine flaw in the checks' own first draft (a `WHERE`-clause predicate was masked
  by a separate Postgres SELECT-for-WHERE-read denial), fixed, and re-confirmed; (2) `V1` byte-for-
  byte and table-ownership checks were already proven in earlier phases.
- `git status -s services/auth services/crypto` — empty throughout; no sibling service touched.

## Specification references

- **Task:** `spec/notification-service/tasks.md`, task 2 ("Schema V1").
- **Requirements (schema-level foundation only, no behavior this task):** R7, R8 (`processed_events`),
  R9, R10, R18 (`channel_preferences`), R11 (`delivery_log`), R12, R13 (`delivery_retry`), R14
  (`templates`), R16, R17 (`inapp_notifications`).
- **LOCKED decisions:** L1 (idempotent by event key), L3 (dispute-grade append-only delivery log —
  the one decision this task DB-enforces, not just shapes), L6 (safe-default preference resolution),
  L7 (bounded retry, then dead-letter), L8 (zero trust on the in-app surface), L9 (versioned
  templates).

## Known, deliberate gaps (not this task's scope)

- No JPA entity/repository code for any of the 8 tables — later tasks' own scope per `design.md` §6.
- `shedlock` exists (required by `V1`'s own verbatim text) but has no ShedLock Maven
  dependency/`@EnableSchedulerLock` yet — deliberately deferred to task 14 (Phase 4 disposition).
- V3's template content (subject/body copy, `{{variable}}` placeholder syntax) is disclosed
  provisional pending O6 (task 9's template-engine choice).
- The `citext` bootstrap step is documented and was applied to the one shared local Postgres this
  session used; it has not been exercised end-to-end against a genuinely fresh Postgres instance
  since the fix (e.g. a new CI runner or teammate machine) — flagged as a residual risk in Phase 12.

## Reviewer notes

- Kimi's Phase 3 (design), Phase 8 (implementation), and Phase 11 (test) reviews raised 7, 6, and 5
  findings respectively — all 18 verified against actual source before disposition; all were real
  and actioned. No finding was rejected as false in this task (unlike crypto-service's own T02,
  where one Phase 11 finding was checked and found to be reading stale content).
- The Phase 11 mutation test is worth a reviewer's attention specifically: it demonstrates why a
  `WHERE`-clause predicate is the wrong shape for a "privilege X is denied" check when the table
  also lacks `SELECT` — Postgres denies the column read needed to evaluate the predicate before ever
  reaching the privilege being tested, so a mistakenly-broad grant can still pass a badly-shaped
  test. Worth checking for the same shape in any future privilege-denial test in this codebase.

---

**Phase 13 complete — PR description drafted, all phases 0-12 closed for notification-service T02.**
