# payment · T02 · Phase 0 — Repository Understanding

## 1. Architecture summary (planned, per spec — T01 built the skeleton only)

Per `spec/payment-service/design.md`: PostgreSQL, owns the `payments` schema exclusively (no
cross-schema queries, agents.md). Flyway DDL-only migrations; `V1__payments_baseline.sql` (§4c) is
the *whole* baseline — 8 tables: `invoices`, `payments`, `verification_records`, `receipts`,
`ledger_entries`, `ledger_anchors`, `processed_events`, `outbox`. Money is `NUMERIC(78,0)` /
`BigDecimal` throughout (L6) — no floating-point type anywhere in the schema. Events publish
through the outbox (L8, not built until task 4); consumers dedupe via `processed_events` (L5, not
built until task 11).

Current real state of `services/payment` (T01, already built): `pom.xml` (JPA + Flyway +
Postgres + Kafka + S3, resource-server-only), a bare `PaymentServiceApplication`, and
`T01SkeletonRegressionTest` (8/8 passing). No `application.properties`, no entity, no migration,
no DB role yet — this task is the first to touch the database at all.

## 2. Existing code this task touches

Nothing in `services/payment/src/main/resources/db/migration/` exists yet — this task creates that
directory. Nothing else in `services/payment` needs to change (no Java code is added by this task;
task 2 is schema-only, matching crypto-service's own T02 and notification-service's own T01-era
schema task).

## 3. Established patterns to follow (read directly from the three sibling services' own real,
already-built migrations — not assumed)

- **The migration itself is a verbatim transcription of design.md §4c, with NO role/grant
  statements in it.** Confirmed directly: `services/crypto/src/main/resources/db/migration/V1__chain_baseline.sql`
  carries only `CREATE SCHEMA`/`CREATE TABLE`/`CREATE INDEX` — the least-privilege runtime role is
  a **separate** `V2` migration. Payment's own task statement names a single task ("Schema V1...
  Grant the service DB role...") but covers what crypto split across two files; task 2's own AC
  will need to decide whether this is one migration file or two (flagged under Known Gaps below,
  not assumed).
- **Role naming convention, confirmed in both sibling services:** `<service>_app` —
  `crypto_app` (`services/crypto/.../V2__crypto_app_role_and_grants.sql`), `notification_app`
  (`services/notification/.../V2__notification_app_role_and_grants.sql`). The natural, consistent
  name for this service is `payment_app`. Not stated anywhere in `spec/payment-service/design.md`
  itself (which only ever says "the service DB role") — inferred from the established cross-service
  pattern, not invented from nothing.
- **The idempotent role-creation pattern (verified, both siblings identical):**
  ```sql
  DO $$
  BEGIN
      IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = '<service>_app') THEN
          CREATE ROLE <service>_app LOGIN;
      END IF;
      EXECUTE format('GRANT CONNECT ON DATABASE %I TO <service>_app', current_database());
  END
  $$;
  GRANT USAGE ON SCHEMA <schema> TO <service>_app;
  GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA <schema> TO <service>_app;
  ```
  No password is ever committed in the migration (confirmed in both siblings' own comments: a
  committed password would only ever be right for local dev, and this migration must be safe to
  run unmodified anywhere). Real environments provision it via External Secrets Operator; local
  dev runs one manual, documented `ALTER ROLE <service>_app PASSWORD '<service>-app-local-only';`
  step (both siblings' own `README.md` document this exact step verbatim, substituting the
  service's own role name).
- **Grants are added incrementally, task-by-task — never pre-emptively.** Checked across every
  one of crypto's own `V2`-`V9` migrations: `crypto_app` only ever gains access to a table in the
  SAME migration as the task that actually needs to read/write it (e.g., `V6` grants `watches`
  access only when task 6/task-equivalent needs it; `V7`'s own comment states this explicitly:
  *"real, not granted preemptively here (least-privilege-by-construction, T02)"*). **This directly
  bounds this task's own scope**: payment's task 2 literally names only `ledger_entries` and
  `verification_records` for the INSERT+SELECT-only grant — per this established convention, the
  other six tables (`invoices`, `payments`, `receipts`, `ledger_anchors`, `processed_events`,
  `outbox`) get their own grants in whichever later task first needs to write to them, NOT in this
  task. This is not an oversight to "fix" — it is the established house style.
- **`GENERATED ALWAYS AS IDENTITY` needs `USAGE` on the underlying sequence**, confirmed as a named
  lesson from crypto's own T02 (memory: `crypto-service-task-progress`) — an otherwise-permitted
  `INSERT` still fails without it. The schema-wide `GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA
  payments TO payment_app` line (mirroring both siblings) covers this for every table's identity
  column up front, even though only two tables get row-level grants in this specific task.
- **`docker exec ... psql` silently bypasses Postgres password authentication** (the official
  image's `pg_hba.conf` trusts local-socket/127.0.0.1 connections regardless of password) — a named
  lesson from crypto's own T02. Any manual verification of `payment_app`'s password must go through
  a real host→container TCP connection, not a container shell.

## 4. Testing conventions

Crypto's own T02 used a **Testcontainers-based JDBC integration test** (not a text-scan guard) for
exactly this reason: "a Testcontainers-based JDBC integration test is strictly better than a
text-scan guard test whenever the thing being guarded is actual database behavior (grants,
ownership, auth)" (crypto-service T02 lesson, memory). This task's own tests (Phase 10) will need
real Postgres to prove the grant actually works — a plain file-content scan of the `.sql` file
(like T01's own `T01SkeletonRegressionTest`) cannot prove a `GRANT` statement actually took effect
or that `payment_app` genuinely cannot `UPDATE`/`DELETE` the two named tables.

## 5. Known gaps / unknowns

- **I do not know** whether this task's own "Schema V1" should be one migration file (baseline +
  role/grants together) or two (mirroring crypto's/notification's own `V1` baseline +
  `V2` role-and-grants split). The task statement's own single sentence ("Add
  `V1__payments_baseline.sql`... Grant the service DB role...") reads as one task covering both,
  but every sibling service split it into two Flyway files even when the SPEC task describing it
  was also a single sentence. Flagging for Phase 2 (implementation brief) rather than assuming
  either way.
- **I do not know** whether `payment_app` is the exact name the task's own author intends, since
  `design.md` itself never names it — inferred from the cross-service convention, not confirmed in
  this spec package directly. Flagging rather than silently committing to it as if it were
  spec-stated.
- Task 2's own statement says "run `mvn -pl services/payment flyway:migrate` against local Docker
  Compose Postgres" as part of the task itself (not just a verification step) — this is an action
  to perform, not just code to write; Phase 5/6 should treat the actual migration run (and its
  real output) as part of this task's own deliverable, not an optional nicety.
