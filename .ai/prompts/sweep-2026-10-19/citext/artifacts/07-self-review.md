# citext callback · Phase 7 — Self-Review

Consumes: commit `de07d046` (diff `b6a4ba49..de07d046`) and its message. This artifact contains no code; it is documentation only. It stands in for Phases 6 and 7, because the fix ran as a direct fix outside the pipeline.

## The defect

- **The conflict:** notification's `V1__notifications_baseline.sql` runs `SET search_path TO notifications` and then uses `CITEXT`, but never installs the extension.
- **The Postgres rule:** an extension can be installed only once per database, into one schema.
- **The shared database:** in a database shared with auth-service, auth's V1 runs `CREATE EXTENSION IF NOT EXISTS citext` with Flyway's default schema `auth`, so the extension lands in `auth`.
- **The failure:** notification then fails with `type "citext" does not exist`.

**How it was found and why tests missed it.** It was first met in notification T02 and fixed by hand on the local database (`ALTER EXTENSION citext SET SCHEMA notifications`), so a fresh deployment still hit it. The Phase 11 Docker smoke run found it again (`artifacts/11-test-review-resolution.md`, Gap 5). Every notification integration test installed `citext` into `notifications` itself before migrating, which is why the suite never showed it.

Both V1 files are immutable (§4c).

## Author decision (2026-10-20)

- **Each service runs in its own database.** That is the deployment's job (part 2), not built here.
- **Notification installs its own `citext`** through a Flyway callback (part 1, this commit).

## The change

| File | Change |
|---|---|
| `services/notification/src/main/resources/db/migration/beforeMigrate.sql` (new) | Runs before every migrate. It runs `CREATE SCHEMA IF NOT EXISTS notifications`, then `CREATE EXTENSION IF NOT EXISTS citext SCHEMA notifications`. A `DO` block then raises an exception if `citext` lives in any schema other than `notifications`, naming that schema and saying to run notification in its own database. |
| 14 notification integration tests | Their hand-written `CREATE SCHEMA` / `CREATE EXTENSION citext SCHEMA notifications` setup is removed, so each one now migrates only through the callback. |
| `NotificationBaselineMigrationIntegrationTest` | Setup relies on the callback alone. New tests: `theCallbackInstallsCitextIntoTheNotificationsSchema`, and `aDatabaseWhereCitextLivesInAnotherSchemaIsRefusedBeforeAnyMigration`. The second creates a second database with `citext` in `auth` and requires both messages. It also requires that `contact_projection` does not exist and that `flyway_schema_history` has no versioned row. |
| `spec/notification-service/design.md` | A dated note after the V1 verbatim block. The block itself is unchanged. |
| `artifacts/11-test-review-resolution.md` | Records the decision and part 1. |

## Evidence

- **Deliberate breaks (3, all caught):**
  - the install line removed;
  - the schema check disabled (`IF false`);
  - `citext` installed into `public`.

  The guarding tests were the baseline test and `ContactProjectionUpdaterIntegrationTest`.
- **Plugin path:** `mvn -o -pl services/notification flyway:migrate` against a fresh `postgres:16-alpine` database migrated to V19, with `citext` in `notifications`. Deployments migrate this way, because the app runs with `spring.flyway.enabled=false`.
- **Suite:** `mvn -o -pl services/notification verify` gives 460 test cases and 0 failures (458 before, plus 2).

## Self-found weaknesses

### S1 — Medium. Auth's side is unguarded

If notification migrates first into a database shared with auth, the callback puts `citext` in `notifications`. Auth's V1 `IF NOT EXISTS` then skips, and its `CITEXT` column most likely fails to resolve under auth's Flyway schema `auth`. This was not tried. Only the deployment's database-per-service rule prevents it, and nothing in auth refuses a shared database with a clear message.

### S2 — Low. A `citext` installed by a database administrator into `public` is refused

Installing extensions into `public` is a common administrator habit (for example on RDS). The callback refuses it with a clear message instead of trying to work with it. That is deliberate, because V1 could not see a `public` copy. It may surprise whoever provisions the database.

### S3 — Low. The callback is not checksum-validated

Flyway callbacks are not recorded in `flyway_schema_history`, so an edit to `beforeMigrate.sql` changes behaviour without any validation error. The two baseline tests pin what it does.

### S4 — Low. Refusal leaves Flyway's own objects behind

Flyway creates the `notifications` schema and its history table before the callback runs. A refused shared database keeps an empty schema and an empty history table. The test asserts this exact state, not "nothing was created".

### S5 — Low. The migrating role needs CREATE on the database

`citext` is a trusted extension in Postgres 13 and later, so a non-superuser with CREATE on the database can install it. The repository does not pin the production Postgres version. The tests use 16.

### S6 — Info. "No service reads another's schema" rests on a grep

The design note says splitting databases breaks nothing. That was checked by searching each service's migrations and main source for other services' schema names. No test enforces it.

### S7 — Info. The local shared database stays in its hand-fixed state

`citext` lives in `notifications` on the local `checky` database (moved during T02), so the callback passes there. An auth migration written later with an unqualified `CITEXT` would fail locally, because auth's Flyway schema is `auth`.

### S8 — Info. The 14 tests now fail together

If the callback breaks, all 14 integration tests fail at setup. That is noisy, but deliberate: the tests no longer hide a missing install.

## Scope check

- **Production code:** one new SQL callback. No Java and no versioned migration changed.
- **Spec:** one dated note. §4c is unchanged.
- **Secrets:** none.
- **Logs:** nothing new is logged.
