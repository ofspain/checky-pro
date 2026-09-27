# notification · T02 · Phase 6 — Implementation Notes

## Files created

- `services/notification/src/main/resources/db/migration/V1__notifications_baseline.sql`
- `services/notification/src/main/resources/db/migration/V2__notification_app_role_and_grants.sql`
- `services/notification/src/main/resources/db/migration/V3__seed_launch_templates.sql`
- `services/notification/src/test/java/com/themistra/notification/NotificationBaselineMigrationIntegrationTest.java`

## What ran, in order, and what the real results were

1. Wrote `V1` exactly as pinned; `diff` against `design.md`'s own fence confirmed byte-for-byte
   equality before proceeding.
2. Wrote `V2` and `V3` exactly as pinned.
3. Wrote `NotificationBaselineMigrationIntegrationTest`, mirroring crypto's own
   `ChainBaselineMigrationIntegrationTest` structure. First run found and fixed a real bug of my own
   (Phase 5's own template-count assertion had a nonsensical, self-contradicting final check —
   `hasSize(9 + 5)` with a comment admitting the two counts weren't the same axis — caught before the
   test even ran once, corrected to a proper `distinctNames` set assertion of size 9).
4. **First real, more significant finding, caught only by actually running the test:** the migration
   failed with `type "citext" does not exist` even after installing `citext` into `public` in
   `@BeforeAll`. Root cause: `V1`'s own VERBATIM `SET search_path TO notifications;` line *replaces*
   the search path entirely rather than appending to it, so a `public`-schema extension type becomes
   invisible the instant that line executes. `services/auth/V1__auth_baseline_schema.sql` never hits
   this because it has no `SET search_path` at all. Since `V1` is immutable VERBATIM, the fix lives
   entirely in environment setup: pre-create the `notifications` schema and install `citext` directly
   into it (`CREATE EXTENSION IF NOT EXISTS citext SCHEMA notifications`), so it's already visible once
   the search path narrows. Fixed, re-ran: all 10 new tests passed against a real, isolated
   Testcontainers Postgres — 19 tests total, 0 failures.
5. Attempted the real local migration per Phase 4's exact command sequence. Starting the shared
   compose stack and running auth's own migration first surfaced the **same `citext` problem in a
   more consequential form**: on the real shared Postgres, `citext` was already installed into
   `public` by auth's own migration, and PostgreSQL only permits one installation of a given extension
   per database — meaning the Testcontainers-style fix (install fresh into `notifications`) wasn't
   available; the only real fix was `ALTER EXTENSION citext SET SCHEMA notifications;`, physically
   relocating the extension auth's own migration had already installed. **Presented to the user
   directly** before touching shared state, given the cross-service impact — approved. Applied it,
   then verified directly that auth's own `accounts.email` column (the citext user) still resolves and
   queries correctly (the type's OID binding is unaffected by which schema now hosts the extension's
   catalog entry — moving it doesn't break already-created columns, only where a *future* unqualified
   reference would resolve from).
6. `mvn -pl services/notification flyway:migrate` — **succeeded**, for real, against the real shared
   local Postgres. The first task in this entire session (across both crypto-service's and
   notification-service's own T02-equivalents) where this instruction was actually executed rather than
   disclosed as environmentally blocked.
7. Set `notification_app`'s local-dev password (`ALTER ROLE notification_app PASSWORD
   'notification-app-local-only'`), matching the established convention.
8. `mvn -pl services/notification -am verify` (final) — **19 tests, 0 failures**, clean
   `package`/`repackage`.

## Disposition summary

| Check | Result |
|---|---|
| `V1` byte-for-byte identical to `design.md` §4c | Confirmed, both by direct `diff` and by the new test's own permanent check |
| `notification_app` role: `INSERT`/`SELECT` on `delivery_log` only | Confirmed, both statically and via a real Testcontainers `UPDATE`/`DELETE`-denial proof |
| 14 template rows, 9 distinct names, version 1 | Confirmed |
| Real `mvn -pl services/notification flyway:migrate` | **Succeeded**, after resolving a genuine, previously-unknown `citext`/`search_path` conflict with auth's own already-installed extension — a real cross-service infrastructure fix, not a code change, approved by the user before touching shared state |
| Full suite | 19 tests, 0 failures, clean repackage |
