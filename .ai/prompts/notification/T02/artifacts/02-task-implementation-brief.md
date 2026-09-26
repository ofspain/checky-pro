# notification · T02 · Phase 2 — Task Implementation Brief

## Task

Add `V1__notifications_baseline.sql` (VERBATIM), a role/grants migration mirroring crypto-service's own
`V2__crypto_app_role_and_grants.sql` exactly, and a template-seeding migration; attempt a real
`mvn -pl services/notification flyway:migrate` against the shared local Docker Compose Postgres.

## Purpose

Establishes the `notifications` schema every later task's own entities/repositories are built on top
of, plus the least-privilege runtime role and the minimal real content the template renderer (task 9)
will consume.

## Scope

**In:**
- `V1__notifications_baseline.sql` — byte-for-byte `design.md` §4c copy, unmodified.
- `V2__notification_app_role_and_grants.sql` — new, mirrors crypto's `V2` exactly in structure
  (passwordless `CREATE ROLE ... LOGIN` guarded by an existence check, `GRANT CONNECT` via
  `EXECUTE format(...)` targeting `current_database()`, `GRANT USAGE` on the `notifications` schema and
  its sequences, narrow `GRANT INSERT, SELECT` on `delivery_log` only — no other table, per this task's
  own literal scope).
- `V3__seed_launch_templates.sql` — new, real seed content for the 7 required launch mappings
  (`design.md` §4c's own topic-mapping table, excluding `auth.user.lifecycle (user.suspended)` →
  `account.suspended`, explicitly marked optional there), **two rows each** (EMAIL + IN_APP), matching
  the default channel-preference matrix where both channels are ON for SECURITY/PAYMENT categories:
  `email.verify`, `email.password_reset`, `user.welcome`, `invoice.created`, `payment.seen`,
  `payment.finalized`, `receipt.issued` — 14 rows, `version = 1` for all. Content is minimal, real, and
  functionally complete (not polished copy), using `{{variable}}` placeholder syntax as a reasonable,
  templating-engine-agnostic default — O6 (the actual rendering engine) is task 9's own open decision;
  this syntax may need revisiting once that's chosen, disclosed explicitly, not treated as final.
- Attempt `mvn -pl services/notification flyway:migrate` for real. Docker is available; before
  attempting, confirm (or run) `services/auth`'s own migrations against the same shared Postgres first,
  since `contact_projection.email CITEXT` depends on the `citext` extension auth's own `V1` enables —
  this is an environment-ordering step, not a change to any VERBATIM SQL.
- A Testcontainers-backed integration test (mirroring crypto's own `ChainBaselineMigrationIntegrationTest`)
  proving: all 8 tables exist and no others; `V1` matches `design.md` §4c byte-for-byte; every migration
  recorded successful; `notification_app` can `INSERT`/`SELECT` but not `UPDATE`/`DELETE` on
  `delivery_log`; `notification_app` has no access to any other table in the schema (unlike crypto's own
  T02, where three tables were granted at once, this task grants only one — the "ungranted" set is
  larger here and should be asserted, not just the "granted" one).

**Out:**
- Any JPA entity/repository (`ContactProjection`, `ChannelPreference`, `Template`, `ProcessedEvent`,
  `DeliveryLog`, `InappNotification`, `DeliveryRetry`) — later tasks' own scope per `design.md` §6.
- Any config class, consumer, or security wiring.
- Rows for `auth.user.lifecycle (user.suspended)` — explicitly optional (Q7), not seeded at launch.
- Any change to `V1`'s own VERBATIM text.

## Business Rules

None individually targeted (Phase 1, unchanged) — this task lays down schema, not behavior.

## Locked Decisions

L1 (`processed_events`), L3 (`delivery_log` append-only, enforced here at the grant level), L6
(`channel_preferences`), L7 (`delivery_retry`), L8 (`inapp_notifications`, storage only), L9
(`templates`, versioned) — all schema-level only, none behaviorally implemented by this task.

## Dependencies

Flyway (T01), the shared `services/auth/compose.local.yaml` Postgres, `citext` extension (via auth's
own `V1`, an environment-ordering dependency, not a code one).

## Inputs

`design.md` §4c's own VERBATIM SQL block; crypto-service's `V1__chain_baseline.sql` /
`V2__crypto_app_role_and_grants.sql` / `ChainBaselineMigrationIntegrationTest` as structural precedent.

## Outputs

Three new migration files; one new integration test class; a real `flyway:migrate` attempt with its
honest result recorded.

## State Changes

None to application state (no entities exist yet). Real state change: the shared local Postgres
instance gains a `notifications` schema and a `notification_app` role, if the real migration succeeds.

## Files to Create

- `services/notification/src/main/resources/db/migration/V1__notifications_baseline.sql`
- `services/notification/src/main/resources/db/migration/V2__notification_app_role_and_grants.sql`
- `services/notification/src/main/resources/db/migration/V3__seed_launch_templates.sql`
- `services/notification/src/test/java/com/themistra/notification/NotificationBaselineMigrationIntegrationTest.java`

## Files to Modify

None.

## Files NOT to Modify

- `services/notification/pom.xml`, `NotificationServiceApplication.java`, `T01SkeletonRegressionTest.java`.
- Every file under `spec/`.
- `services/auth`, `services/crypto` migrations/source (cited as precedent only).

## Acceptance Criteria

1. **AC1.** `V1` is byte-for-byte identical to `design.md` §4c's fenced SQL block.
2. **AC2.** `notification_app` role exists, passwordless, granted `INSERT, SELECT` only on
   `delivery_log` — verified both statically and by a real Testcontainers attempt proving `UPDATE`/
   `DELETE` genuinely fail at the database level, and that every other table is genuinely inaccessible
   to this role.
3. **AC3.** All 14 template rows exist with the content specified above, `version = 1`.
4. **AC4.** `mvn -pl services/notification flyway:migrate` succeeds against the real local Postgres,
   attempted for real (with auth's own migrations confirmed run first for `citext`), not merely
   disclosed as blocked.
5. **AC5.** `flyway_schema_history` records all three new migrations as successful.

## Required Tests

`NotificationBaselineMigrationIntegrationTest`, per the Scope section above.

## Constraints

- **VERBATIM discipline**: `V1` is copied exactly; role/grant/seed content lives in separate,
  clearly-labeled migrations, never folded into `V1` itself.
- **Least privilege**: `notification_app` never owns any table (mirrors crypto's owner/grantee split —
  Postgres table owners bypass GRANT/REVOKE entirely, so the role must be a pure grantee).
- **No committed password** for `notification_app` (L10) — set out-of-band, matching crypto's own
  documented local-dev workaround (`ALTER ROLE ... PASSWORD ...` run manually, once, locally).
- **Environment ordering**: the real migration attempt (AC4) requires auth's own migrations to have run
  first against the same Postgres instance, for `citext`. If that ordering can't be satisfied in this
  environment, disclose the exact failure honestly rather than silently skip AC4.

## Open Questions

No blockers. Both of Phase 1's open questions are resolved as working decisions above (template content
and migration split; citext ordering plan), subject to Phase 3/4 challenge like any other design choice.
