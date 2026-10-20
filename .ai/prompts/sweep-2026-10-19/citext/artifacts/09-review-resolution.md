# citext callback · Phase 9 — Review Resolution

Consumes:
- `artifacts/08-independent-review.md` (Kimi, `1e077057`)
- `artifacts/07-self-review.md`

I checked every finding against the source at `1e077057` before deciding.
- **No file:line evidence:** the review gives none, and six of its eight findings restate the self-review (S1–S7).
- **A wrong file name:** it cites `V1__auth_baseline.sql`; the file is `V1__auth_baseline_schema.sql`.
- **The main claim, run for real:** on a fresh `postgres:16-alpine` database I ran notification's `flyway:migrate` first and auth's second. Auth failed at V1 line 9 with `type "citext" does not exist`, created no `auth.accounts`, and `citext` was in `notifications`. Findings 1 and 2 are confirmed.

## Decisions

| Finding | Decision | Change |
|---|---|---|
| F1 — auth's side unguarded (= S1) | **Accept** | New `services/auth/src/main/resources/db/migration/beforeMigrate.sql`. It refuses a database where `citext` lives in a schema other than `auth` **and** `auth.accounts` does not exist yet, so V1 has not run. The message names the schema and says to run auth in its own database. The second condition keeps an already-migrated database starting: auth's citext columns hold the type wherever the extension lives, and the local shared database has `citext` in `notifications` since T02. Auth runs Flyway at every start, so a check without it would stop local auth. |
| F2 — no test for notification first, then auth | **Accept** | New `AuthCitextCallbackIntegrationTest`, 5 tests, each on its own database: a fresh database migrates with `citext` in `auth`; notification's install in `notifications` is refused before V1 with both messages, no `auth.accounts` and no versioned history row; an administrator's install into `auth` is accepted; an already-migrated database whose `citext` was then moved away still migrates, and its citext column still compares; a second migrate is clean. |
| F3 — no re-run test | **Accept** | `NotificationBaselineMigrationIntegrationTest.aSecondMigrateOnTheMigratedDatabaseIsClean`, plus auth's re-run test. |
| F4 — callback not checksum-validated (= S3) | **Accept the comment; reject `R__`** | Both callbacks' headers say edits are not checksum-validated and name the test that pins them. A repeatable `R__` migration runs *after* the versioned ones, too late for V1. |
| F5 — refusal leaves Flyway objects (= S4) | **Accept** | Both refusals carry a Postgres `HINT`: Flyway has already created an empty schema and history table, and they can be dropped. |
| F6 — no guard against cross-schema reads (= S6) | **Reject: already guarded** | Every integration test in every service migrates a fresh container holding only that service's own migrations. A migration or query that read another service's table would fail there, as it would against a database-per-service deployment. The review's claim that notification's callback "explicitly names `auth`" is wrong: the schema in its message comes from `pg_extension` at run time. |
| F7 — Postgres version not pinned (= S5) | **Accept** | `aMigratingRoleThatIsNotASuperuserCanInstallCitextAndMigrate`: a role that is not a superuser, owns its database and has CREATEROLE runs all 19 migrations, with `citext` in `notifications` and `rolsuper` false. The notification design note now says Postgres 13 or later. V2's role creation is not exercised there, because `notification_app` already exists in the container (the test says so). |
| F8 — no test for `citext` in `public` (= S2) | **Accept** | `aDatabaseWhereAnAdministratorPutCitextInPublicIsRefusedTheSameWay`. Both refusal tests share one helper, which requires the message to name the exact schema. |

**D-005 (auth: "migrations contain schema only").** The auth callback's `DO` block is a one-off precondition check. It stores no function or procedure, and it holds no business logic, so I read it as within D-005. I record it here so the author can disagree.

## Deliberate breaks (5, all caught)

| Break | Caught by |
|---|---|
| A1 auth: the "V1 not yet run" condition removed | `anAlreadyMigratedDatabaseStillMigratesAfterCitextWasMovedAway` |
| A2 auth: the check disabled | `aDatabaseWhereNotificationAlreadyInstalledCitextIsRefusedBeforeV1` |
| A3 auth: `'auth'` replaced by `'public'` in the check | `citextPreinstalledIntoTheAuthSchemaIsAccepted` (added because this break had no guard) |
| N1 notification: refuse only `auth`, not every other schema | `aDatabaseWhereAnAdministratorPutCitextInPublicIsRefusedTheSameWay` |
| N2 notification: refuse an already-migrated database | `aSecondMigrateOnTheMigratedDatabaseIsClean` |

## Spec

- **Notification:** a second dated note in `spec/notification-service/design.md` after the V1 block covers the `public` refusal, the re-run, Postgres 13 or later and auth's guard. The §4c block is unchanged.
- **Auth:** no spec note was added, because auth spec edits were not part of this sweep's approval. The guard is documented in its own callback header and test.

## Suite state after resolution

`mvn -o -pl services/<service> verify`, both BUILD SUCCESS. Counts are test cases in the Surefire reports.

| Service | Test cases | Failures |
|---|---|---|
| auth | 787 (782 + 5) | 0 |
| notification | 463 (460 + 3) | 0 |
