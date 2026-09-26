# notification · T02 · Phase 1 — Specification Extraction

## Business Rules

No individual R-numbered requirement is functionally implemented by this task — it lays down the
`notifications` schema every later requirement's own implementation depends on, the same relationship
crypto-service's own T02 had to that service's requirements. The schema's own tables map forward to:
R7/R8 (`processed_events` — idempotency), R9/R10/R18 (`channel_preferences` — preference resolution),
R11 (`delivery_log` — delivery attempts), R12/R13 (`delivery_retry` — bounded retry), R14
(`templates` — versioned rendering), R16/R17 (`inapp_notifications` — in-app stream/read API).

## Locked Decisions

- **L1.** Idempotent by event key — `processed_events`'s `event_key VARCHAR(200) PRIMARY KEY` is the
  ledger this decision depends on.
- **L3.** Dispute-grade, append-only delivery log — `delivery_log`'s own shape (no `UPDATE`-friendly
  columns, `attempt SMALLINT` supporting new rows per retry) and this task's own grant requirement
  (INSERT+SELECT-only, no UPDATE/DELETE) directly enforce "append-only" at the DB-permission layer,
  mirroring crypto-service's own `observations`/`attestations` grant pattern exactly.
- **L6.** Preference resolution with a safe default — `channel_preferences`'s `uq_pref`
  unique constraint and `chk_pref_category`/`chk_pref_channel` check constraints are the schema-level
  half of this decision.
- **L7.** Bounded retry, then dead-letter — `delivery_retry`'s scheduling table and `delivery_log`'s
  own `DEAD_LETTERED` outcome value are this decision's schema-level half.
- **L8.** Zero trust on the in-app surface — `inapp_notifications`'s own shape (no security logic yet;
  this task only creates storage) is what task 13's resource-server-validated stream/read API will
  read from.
- **L9.** Templates are versioned — `templates`'s `uq_template UNIQUE (name, channel, version)`
  constraint enforces this at the schema level.

## Files involved

**New, this task's own deliverable:**
- `services/notification/src/main/resources/db/migration/V1__notifications_baseline.sql` — VERBATIM
  copy of `design.md` §4c's own SQL block, not paraphrased. Creates the `notifications` schema and all
  7 tables plus `shedlock`.
- A second migration for the service DB role and its narrow grant (mirroring crypto-service's own
  `V1`-schema/`V2`-role-and-grants split, since the VERBATIM `V1` text itself contains no
  `CREATE ROLE`/`GRANT` statement) — exact numbering and content is Phase 2's own job to finalize, not
  invented here.
- A migration (or seeding mechanism — Phase 2 to decide) populating `templates` with the launch
  templates' actual content — genuinely open, see Open Questions below.

**Read-only, precedent to mirror:**
- `services/crypto/src/main/resources/db/migration/V1__chain_baseline.sql` /
  `V2__crypto_app_role_and_grants.sql` — the direct, working precedent for the schema/role-grant split
  and the role-creation SQL shape (passwordless `CREATE ROLE ... LOGIN`, `DO $$ ... EXECUTE format(...)`
  for `GRANT CONNECT`, `GRANT USAGE` on schema and sequences, narrow named `GRANT INSERT, SELECT`).
- `services/auth/compose.local.yaml` — the shared local Postgres+Kafka compose file this task's own
  "against local Docker Compose Postgres" text refers to.

**Not touched:** any file under `spec/`; `services/auth`, `services/crypto` migrations/source (cited as
precedent only).

## Dependencies

Flyway (already added to `services/notification/pom.xml` in T01), the shared local Postgres via
`services/auth/compose.local.yaml`, no application code (no entities/repositories exist yet — those are
later tasks' own scope per `design.md` §6's package map).

## Acceptance Criteria

1. **AC1.** `V1__notifications_baseline.sql` exists, byte-for-byte identical to `design.md` §4c's own
   fenced SQL block.
2. **AC2.** A new, passwordless, least-privilege DB role exists (naming: `notification_app`, mirroring
   `crypto_app`'s own convention) with `INSERT, SELECT` only on `delivery_log` — no `UPDATE`/`DELETE`,
   verified by both a static grant-statement check and (mirroring crypto's own
   `ChainBaselineMigrationIntegrationTest.cryptoAppCanInsertAndSelectButNotUpdateOrDeleteOnTheThreeGrantedTables`)
   a real Testcontainers-backed attempt that proves `UPDATE`/`DELETE` are actually denied at the
   database level, not just absent from the grant statement.
3. **AC3.** The launch templates are seeded with real, minimal content for every event→template
   mapping in `design.md` §4c's own topic-mapping table.
4. **AC4.** `mvn -pl services/notification flyway:migrate` succeeds against the real, local Docker
   Compose Postgres — attempted for real in this environment (Docker is available), not merely
   disclosed as blocked.
5. **AC5.** Flyway's own migration history records every migration this task adds as successful
   (mirroring crypto's own `allMigrationsAreRecordedAsSuccessfulInFlywayHistory`).

## Tests required

A Testcontainers-backed integration test class (mirroring crypto's own
`ChainBaselineMigrationIntegrationTest` naming and structure) proving: all 8 tables exist (7 + shedlock)
and no others; the `V1` migration file is byte-for-byte identical to `design.md`'s own fenced block;
every migration recorded successful in `flyway_schema_history`; the new role can `INSERT`/`SELECT` but
not `UPDATE`/`DELETE` on `delivery_log`; the role has no access at all to tables outside this task's own
grant scope (mirroring crypto's own `UNGRANTED_TABLES`-style check, expected to be non-empty here since
only `delivery_log` is granted this task, unlike crypto's T02 where three tables were granted at once).

## Open Questions

1. **"Seed the launch templates" has no specified content anywhere in the spec package** (Phase 0
   finding). `design.md` §4c gives the `templates` table's own DDL, not seed rows. Phase 2 must propose
   concrete, minimal subject/body content for each of the 7 event→template mappings — genuinely
   authored content, not extracted from an existing VERBATIM artifact. Not a blocker for this
   extraction, but the single most consequential open decision for Phase 2.
2. **`contact_projection.email CITEXT` depends on the `citext` extension already being enabled** on the
   shared database (verified: auth's own `V1__auth_baseline_schema.sql` enables it; notification's own
   VERBATIM `V1` does not, correctly, since amending a VERBATIM artifact isn't this task's place). A real
   `mvn -pl services/notification flyway:migrate` attempt (AC4) will fail with an unrecognized-type error
   unless auth's migrations have already run against the same Postgres instance. Phase 6 must confirm
   this ordering before attempting the real migration, not treat a resulting failure as this task's own
   defect.
