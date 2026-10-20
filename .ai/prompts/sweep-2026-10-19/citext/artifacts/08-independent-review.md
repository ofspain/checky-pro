# citext callback · Phase 8 — Independent Code Review

Fresh findings from reviewing the Phase 7 self-review and the `beforeMigrate.sql` callback against the platform rules and deployment assumptions.

---

## Finding 1 — Auth's side of the shared-database scenario is unguarded

- **Issue:** If `notification` migrates first in a database shared with `auth`, the callback installs `citext` into the `notifications` schema. Auth's V1 then runs `CREATE EXTENSION IF NOT EXISTS citext` with Flyway's default schema `auth`, which skips because the extension already exists, but auth's unqualified `CITEXT` columns will fail to resolve under schema `auth`. The deployment rule is "one database per service," but nothing in auth refuses a shared database with a clear message.
- **Severity:** Medium — the failure mode is plausible until the database-per-service deployment is enforced, and the error message would be confusing.
- **Evidence:** Self-review S1; `services/auth/src/main/resources/db/migration/V1__auth_baseline.sql`; `services/notification/src/main/resources/db/migration/beforeMigrate.sql`.
- **Recommendation:** Add a Flyway callback (or a guarded V1 check) in auth that asserts `citext` is usable under the `auth` schema, and raises a clear message if it is installed only in another service's schema. Alternatively, document that auth must never migrate into a database where notification has already installed `citext`.

---

## Finding 2 — No test covers notification-first / auth-second ordering

- **Issue:** The committed test only covers the case where auth (or another schema) already owns `citext` and notification is then refused. It does not cover the reverse: notification installs `citext` first, and auth must either work or fail loudly.
- **Severity:** Medium — this is the sibling half of the shared-database risk.
- **Evidence:** Self-review S1; `NotificationBaselineMigrationIntegrationTest.aDatabaseWhereCitextLivesInAnotherSchemaIsRefusedBeforeAnyMigration`.
- **Recommendation:** Add a test that installs `citext` into `notifications`, runs notification's migrations, then attempts auth's migrations and asserts either success (if auth can resolve `citext`) or a clear failure before any auth table is created.

---

## Finding 3 — No test verifies idempotent re-run of the callback

- **Issue:** `beforeMigrate.sql` uses `CREATE EXTENSION IF NOT EXISTS` and `CREATE SCHEMA IF NOT EXISTS`, so it is idempotent. However, there is no test that runs `mvn flyway:migrate` twice and asserts the second run succeeds with no changes.
- **Severity:** Low — the SQL is obviously idempotent, but a regression (e.g., a stricter check in the DO block) could break re-run.
- **Evidence:** `services/notification/src/main/resources/db/migration/beforeMigrate.sql`.
- **Recommendation:** Add a test or shell-script check that migrates a fresh database twice and asserts the second run is clean.

---

## Finding 4 — The callback is not recorded in `flyway_schema_history`

- **Issue:** This aligns with self-review S3. Flyway callbacks do not appear in `flyway_schema_history`, so an operator cannot see that the callback ran, and an edit to the callback will not trigger Flyway's checksum validation.
- **Severity:** Low — the two baseline tests pin the callback's behaviour, but operational visibility is reduced.
- **Evidence:** Self-review S3.
- **Recommendation:** Document this in the runbook and add a comment in `beforeMigrate.sql` warning that the file is not checksum-protected. Consider wrapping the extension install logic in a tiny repeatable migration (e.g., `R__citext_in_notifications.sql`) if Flyway's callback model is insufficient.

---

## Finding 5 — The refusal path leaves Flyway artifacts behind

- **Issue:** This aligns with self-review S4. When the callback refuses a shared database, Flyway has already created the `notifications` schema and its empty history table. The test asserts this state, but an operator might be surprised to see an empty schema after a failed migration.
- **Severity:** Low — the state is harmless and the test documents it.
- **Evidence:** Self-review S4.
- **Recommendation:** Add a brief note in the callback's error message or in the runbook explaining that the empty `notifications` schema and history table are expected after a refused shared-database migration.

---

## Finding 6 — No test enforces "no service reads another service's schema"

- **Issue:** This aligns with self-review S6. The database-per-service decision relies on a grep showing no cross-schema references. There is no automated guard that prevents a future migration or Java class from referencing `auth.` or `payments.` from notification.
- **Severity:** Low — the grep was done, but it is not a durable guard.
- **Evidence:** Self-review S6.
- **Recommendation:** Add an ArchUnit or source-text test in each service that bans references to other services' schema names in `src/main` and in migrations. This is especially valuable for notification, whose `beforeMigrate.sql` explicitly names `auth` in the error path.

---

## Finding 7 — The production Postgres version is not pinned

- **Issue:** This aligns with self-review S5. `citext` is a trusted extension in Postgres 13+, so a non-superuser with `CREATE` on the database can install it. If production ran Postgres 12 or older, the install would require superuser privileges, which the migration role may not have.
- **Severity:** Low — Postgres 12 is past EOL and unlikely.
- **Evidence:** Self-review S5.
- **Recommendation:** Add a platform requirement note (e.g., in `docs/adr/` or the service manifest) that production Postgres must be ≥ 13, and add a migration test that asserts the installing role is not superuser but still succeeds.

---

## Finding 8 — No test covers an extension installed in `public` by an administrator

- **Issue:** This aligns with self-review S2. If a DBA installs `citext` into `public` (common on managed Postgres), the callback refuses with a clear message. There is no test that exercises this exact scenario, and the error message is not validated.
- **Severity:** Low — the scenario is covered by the "any schema other than notifications" check, but an explicit test would pin the message.
- **Evidence:** Self-review S2.
- **Recommendation:** Add a test that installs `citext` into `public` and asserts the callback raises the expected message naming `public`.

---

## Summary

The citext callback is a clean, minimal fix that correctly pins the extension in the `notifications` schema and refuses dangerous shared-database states. The most important follow-ups are Finding 1 (guard auth's side of the shared-database case) and Finding 2 (test the notification-first ordering). Findings 3–8 are smaller idempotency, visibility, and deployment-guard notes. No production Java code changes are needed.
