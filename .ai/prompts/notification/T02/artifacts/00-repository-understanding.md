# notification · T02 · Phase 0 — Repository Understanding

## 1. Architecture summary

Unchanged from T01's own Phase 0: `notification-service` is a consume-only Kafka fan-out layer owning
the `notifications` schema — templates, preferences, delivery log, idempotency ledger, in-app store,
retry scheduling. T02's own job is to lay down that schema for real via Flyway, greenfield, and to seed
the launch templates.

## 2. Existing code this task touches

**Still nothing beyond T01's skeleton.** `services/notification/` now has a `pom.xml`,
`NotificationServiceApplication`, and its own `T01SkeletonRegressionTest` — no entities, repositories,
migrations, or config classes exist yet. T02 is the first task to add real content under
`src/main/resources/db/migration/`.

The exact `V1__notifications_baseline.sql` content is already fully specified, VERBATIM, in
`design.md` §4c — 7 tables (`contact_projection`, `channel_preferences`, `templates`,
`processed_events`, `delivery_log`, `inapp_notifications`, `delivery_retry`) plus a `shedlock` table,
inside a `notifications` schema created with `CREATE SCHEMA IF NOT EXISTS`.

## 3. Established patterns to follow

**The precedent to mirror for the DB-role/grants half of this task is `crypto-service`'s own T02, not
auth's.** Verified directly: `services/auth/src/main/resources/db/migration/` contains no
`CREATE ROLE`/`GRANT` statement anywhere, and auth's own `application.properties` runs its application
as the same `checky` admin/owner credentials Flyway itself migrates with
(`spring.datasource.username=${DB_USERNAME:checky}`) — auth has no least-privilege runtime role at all.
Crypto-service's own `V2__crypto_app_role_and_grants.sql` is the real, working precedent for exactly
this task's own ask ("Grant the service DB role INSERT+SELECT-only on `delivery_log`"): a new,
passwordless `CREATE ROLE ... LOGIN` (real password set out-of-band, never committed), `GRANT CONNECT`
via a `DO $$ ... EXECUTE format(...)` block (never hardcoding the database name), `GRANT USAGE` on the
schema, `GRANT USAGE, SELECT` on sequences backing `GENERATED ALWAYS AS IDENTITY` columns, and narrow,
named `GRANT INSERT, SELECT` on specific tables only.

**Grants are incremental, task by task — not all-at-once.** Crypto-service's own pattern (confirmed
directly across its migration history: `V2` grants 3 tables, `V3` adds `outbox`, `V4` adds
`provider_health`, `V5` adds `token_allowlist`, `V6` adds `watches`/`chain_cursors`, `V9` adds
`screening_results`) grants each table's access in the task that first needs it, not upfront. T02's own
task statement matches this exactly — it names only `delivery_log`, not all 7 tables — so the other six
tables' own grants are expected to land in later tasks (4, 5, 8, 9, 13, 14), each adding its own grant
migration when it needs one, not this task's job to anticipate.

**Local dev Postgres/Kafka is one shared compose file, not per-service.**
`services/auth/compose.local.yaml` is the only compose file in the repo (verified — no
`services/crypto/compose.local.yaml`, no root-level one). It provisions a single `checky`
database/user/password on `localhost:5432` and a single Kafka broker on `localhost:9094`, used by all
three services identically (crypto's and notification's own `flyway-maven-plugin` blocks both connect
to the exact same `jdbc:postgresql://localhost:5432/checky`). "Local Docker Compose Postgres" in this
task's own statement means this file, run from the repo root as
`docker compose -f services/auth/compose.local.yaml up -d`, despite living under `services/auth/`.

## 4. Testing conventions

Unchanged. Testcontainers Postgres for any repository-level integration test this task's own scope
might need (though the task statement itself only asks for the migration + a real local
`flyway:migrate`, not new JPA entities/repositories yet — those are later tasks' own scope per
`design.md` §6's package map).

## 5. Known gaps / unknowns

- **"Seed the launch templates" has no specified content.** `design.md` §4c gives the `templates`
  table's own DDL (name, channel, version, subject, body) but no actual seed rows — no real subject
  lines or body text for `email.verify`, `email.password_reset`, `user.welcome`, `invoice.created`,
  `payment.seen`, `payment.finalized`, `receipt.issued` exists anywhere in the spec package. This is
  the one genuinely open content-authoring decision this task must make (Phase 2's own job to propose
  concrete, minimal seed content), unlike a VERBATIM DDL/config artifact that's simply copied.
- **`contact_projection.email CITEXT` depends on the `citext` Postgres extension already being
  enabled on the shared database** — verified that `services/auth/src/main/resources/db/migration/V1__auth_baseline_schema.sql`
  enables it (`CREATE EXTENSION IF NOT EXISTS citext`, Postgres extensions are database-wide, not
  per-schema). The VERBATIM `V1__notifications_baseline.sql` in `design.md` §4c does not itself enable
  it — correctly, since a VERBATIM artifact is copied exactly, not amended. This means a real local
  `flyway:migrate` attempt for notification-service will fail with an unrecognized-type error unless
  auth's own migrations have already run against the same Postgres instance first. Not a defect in the
  spec; a real ordering dependency for Phase 6 to handle operationally (run auth's migration first, or
  confirm the extension is already present), not by editing the VERBATIM SQL.
- **Docker is genuinely available in this environment** (confirmed throughout the crypto-service
  pipeline's own T28/T29 investigation) and `services/auth/compose.local.yaml`'s containers already
  exist locally, just currently stopped (`docker ps -a` shows `auth-postgres-1`/`auth-kafka-1`,
  status `Exited`). Unlike every "run the real migration against Docker" instruction in
  crypto-service's own T01-T29 (where Docker was unavailable until late in that pipeline), this task
  may actually be able to attempt the real `mvn -pl services/notification flyway:migrate` for real,
  not just disclose it as blocked — worth attempting directly in Phase 6, honestly reporting whichever
  way it goes.
