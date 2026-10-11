STATUS: FROZEN (approved 2026-10-11, Human Approval gate)

# payment · T02 · Frozen Task Brief

## Phase 3 disposition (all 7 findings ACCEPTED — none rejected)

1. **V2 scope omits `LOGIN`/`GRANT CONNECT`.** Real gap: the TIB's own Scope/AC text didn't
   restate these explicitly, even though Phase 1's extraction and the cited sibling templates both
   have them. Made explicit below (AC2) rather than relying on an implementer cross-referencing the
   templates themselves.
2. **Table count wrong — I said 8, `design.md` §4c defines 9.** Verified directly: `grep -n
   "^CREATE TABLE" spec/payment-service/design.md` returns `invoices, payments,
   verification_records, receipts, ledger_entries, ledger_anchors, processed_events, outbox,
   shedlock` — nine tables. This was my own factual error, repeated across Phase 0 and Phase 1 of
   this task. Corrected below and in every AC from this point forward; Phase 0/1's own artifacts
   are left as-written (disclosed here, not silently rewritten) per this session's own "disclose
   forward" convention.
3. **No table-ownership verification.** Confirmed `crypto-service` has exactly this test
   (`baselineTablesAreOwnedByTheMigrationRoleNeverByCryptoApp`,
   `ChainBaselineMigrationIntegrationTest.java:209`) — added as a new AC/test below.
4. **README password placeholder needs an explicit L13 carve-out**, mirroring T01's own documented
   Flyway-plugin-password carve-out — made explicit in Locked Decisions below.
5. **Test's `payment_app` connection method unspecified.** Confirmed crypto's own pattern: a
   test-only password set via `ALTER ROLE` in `@BeforeAll`, then a direct `DriverManager`
   connection as the app role (`ChainBaselineMigrationIntegrationTest.java:62-72,277-281`) — folded
   into Required Tests.
6. **Migration-application method in the test unspecified.** Confirmed crypto applies V1/V2 via
   the Flyway Java API (`Flyway.configure()...migrate()`) directly against the Testcontainers
   database, not Spring Boot auto-migration (which doesn't exist until task 3 anyway) — folded
   into Required Tests.
7. **No DDL-denial test.** Confirmed crypto has exactly this
   (`cryptoAppCannotPerformDdlInTheChainSchema`,
   `ChainBaselineMigrationIntegrationTest.java:191`) — added as a new AC/test below.

## Task

T02 — Schema V1.

## Purpose

Create the `payments` schema baseline (**9 tables**, verbatim from `design.md` §4c), run it
against local Postgres, and establish the least-privilege `payment_app` runtime role — granted
LOGIN, database CONNECT, schema/sequence USAGE, and INSERT+SELECT only on the two append-only/
history tables this task names, and nothing on the other seven (every other table's own grant
lands in whichever later task first needs it).

## Scope

**In:**
- `V1__payments_baseline.sql` — schema-only, verbatim §4c, **9 tables** (`invoices`, `payments`,
  `verification_records`, `receipts`, `ledger_entries`, `ledger_anchors`, `processed_events`,
  `outbox`, `shedlock`), no role/grant statement.
- `V2__payment_app_role_and_grants.sql` — idempotent `CREATE ROLE payment_app LOGIN` (guarded),
  dynamic `GRANT CONNECT ON DATABASE <current_database()> TO payment_app`, schema `USAGE`,
  sequence `USAGE, SELECT`, and INSERT+SELECT-only on `ledger_entries` and `verification_records`.
- Running `mvn -pl services/payment flyway:migrate` against local Docker Compose Postgres.
- One README line documenting the local-dev `ALTER ROLE` step (placeholder password, carved out
  of L13 — see Locked Decisions).

**Out:**
- Any grant on `invoices`, `payments`, `receipts`, `ledger_anchors`, `processed_events`, or
  `outbox` for `payment_app` — deferred to the task that first needs each one. `shedlock`'s own
  grant likewise deferred to task 8 (invoice expiry sweep), the first task that actually needs
  ShedLock.
- Any Java code (entity, repository, service, controller) — tasks 5, 9, 10, 15, 19, 23.
- Setting `payment_app`'s real password anywhere outside the local-dev README instruction.

## Business Rules

None — no `requirements.md` R-number applies to this schema-only task.

## Locked Decisions

- **L6** — `NUMERIC(78, 0)` for every amount column; no floating-point type anywhere in `V1`.
- **L7** — `ledger_entries` append-only in practice, not just in comment: `payment_app` genuinely
  cannot `UPDATE`/`DELETE` it (or `verification_records`), proven against real Postgres.
- **L12** — this migration's own table set matches `design.md` §5's stated module ownership.
- **L13** — no password committed in `V2__payment_app_role_and_grants.sql`, **with one explicit,
  documented carve-out** (Phase 3 finding 4, mirrors the T01 POM carve-out): the local-dev-only
  `ALTER ROLE payment_app PASSWORD 'payment-app-local-only'` instruction in `README.md` is a
  placeholder for local development only, never a real environment's credential — this is the
  same, already-established, cross-service pattern (`crypto-service`/`notification-service`'s own
  README each carry the identical placeholder shape), not a new exception invented here.

## Dependencies

None at the Java level. Infrastructure: local Docker Compose Postgres running and reachable at
`localhost:5432` (the Flyway Maven plugin's own already-configured admin-role URL, from T01).

## Inputs

- `design.md` §4c (the full `V1__payments_baseline.sql` text, copied verbatim — **9 tables**).
- `services/crypto/src/main/resources/db/migration/V2__crypto_app_role_and_grants.sql` and
  `services/notification/.../V2__notification_app_role_and_grants.sql` (role/grants shape).
- `services/crypto/src/test/java/com/themistra/crypto/ChainBaselineMigrationIntegrationTest.java`
  (test shape: Flyway Java API migration, test-only password provisioning, `DriverManager`
  connections as both the admin role and the app role, ownership check, DDL-denial check).

## Outputs

- `services/payment/src/main/resources/db/migration/V1__payments_baseline.sql` (new).
- `services/payment/src/main/resources/db/migration/V2__payment_app_role_and_grants.sql` (new).
- The `payments` schema created in the local Docker Compose Postgres instance.

## State Changes

Database state: the `payments` schema and its 9 tables are created; the `payment_app` role is
created with `LOGIN` and `CONNECT`, no password set by the migration itself.

## Files to Create

- `services/payment/src/main/resources/db/migration/V1__payments_baseline.sql`
- `services/payment/src/main/resources/db/migration/V2__payment_app_role_and_grants.sql`
- `services/payment/src/test/java/com/themistra/payment/SchemaBaselineMigrationIntegrationTest.java`

## Files to Modify

- `services/payment/README.md` — the local-dev `ALTER ROLE` instruction line.

## Files NOT to Modify

- `services/payment/pom.xml`, `PaymentServiceApplication.java`, `T01SkeletonRegressionTest.java`
  (T01's own files, out of scope).
- Anything under `spec/`.

## Acceptance Criteria

- **AC1** — `V1__payments_baseline.sql` matches `design.md` §4c verbatim: **all 9 tables**
  (`invoices`, `payments`, `verification_records`, `receipts`, `ledger_entries`,
  `ledger_anchors`, `processed_events`, `outbox`, `shedlock`), every check constraint, every index.
- **AC2** — `mvn -pl services/payment flyway:migrate` succeeds against local Docker Compose
  Postgres; the `payments` schema exists afterward with all 9 tables. `payment_app` is created
  with `LOGIN` and has `CONNECT` on the database (Phase 3 finding 1).
- **AC3** — connecting as `payment_app`, `INSERT`/`SELECT` succeed on `ledger_entries` and
  `verification_records`; `UPDATE`/`DELETE` on either fail with a real Postgres permission error.
- **AC4** — `payment_app` can `INSERT` into either granted table relying on its
  `GENERATED ALWAYS AS IDENTITY` column without a separate sequence-permission failure.
- **AC5** — `payment_app` has no grant whatsoever on the other seven tables.
- **AC6** — no password for `payment_app` is committed anywhere in `V2`; `README.md` documents the
  one manual local-dev `ALTER ROLE` step (the one, documented L13 carve-out).
- **AC7** (Phase 3 finding 3) — every one of the 9 baseline tables is owned by the migration/admin
  role (`checky`), never by `payment_app`.
- **AC8** (Phase 3 finding 7) — `payment_app` cannot `CREATE TABLE`/`DROP TABLE` anywhere in the
  `payments` schema (no schema-level `CREATE` privilege was ever granted to it).

## Required Tests

- `com.themistra.payment.SchemaBaselineMigrationIntegrationTest` (Testcontainers Postgres, new).
  Applies `V1`/`V2` via the **Flyway Java API directly** (`Flyway.configure()...migrate()`),
  mirroring crypto's own test — not Spring Boot auto-migration, which doesn't exist until task 3.
  Provisions a **test-only** `payment_app` password via `ALTER ROLE` in `@BeforeAll` (connected as
  the admin role), then connects as `payment_app` over real JDBC (`DriverManager`) to prove every
  permission boundary — never `docker exec`, which bypasses password auth via loopback trust.
  Asserts AC1–AC8: table/constraint existence (AC1), migration + role/LOGIN/CONNECT success (AC2),
  grant/denial specifically as `payment_app` (AC3), identity-column insert (AC4), absence of any
  grant on the other seven tables via a real `information_schema.table_privileges` query (AC5),
  no committed password (AC6), table ownership by the admin role (AC7), and DDL denial (AC8).

## Constraints

- **Security:** `payment_app` is the only role this task creates; it owns nothing (table owner
  stays the migration/admin role) — the owner/grantee split is what makes the restriction real.
- **Transaction / module boundaries / null handling / performance / thread-safety:** not
  applicable (DDL/role statements, no application code yet).
- **Idempotency:** the role-creation block must be safe to re-run (guarded `CREATE ROLE`); plain
  `GRANT` statements need no such guard (re-granting an already-held privilege is a silent Postgres
  no-op).

## Open Questions

None. All resolved via the Phase 3 disposition above.
