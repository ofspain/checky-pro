# payment · T02 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Proceeding.

**Note on process:** this task's own work was committed phase-by-phase to the long-lived
`spec/service-specs-and-ai-framework` branch, mirroring T01's own and every sibling service's own
identical convention. This artifact is the consolidated summary of that already-committed
history, not a pending commit — `main` was never touched and stays deployable throughout.

## Commit title

`payment-service T02: schema baseline (9 tables) & payment_app least-privilege role`

## Commit message

```
payment-service T02: schema baseline (9 tables) & payment_app least-privilege role

Adds V1__payments_baseline.sql (verbatim copy of design.md section 4c,
confirmed byte-for-byte - 9 tables: invoices, payments,
verification_records, receipts, ledger_entries, ledger_anchors,
processed_events, outbox, shedlock) and V2__payment_app_role_and_grants.sql
(idempotent CREATE ROLE ... LOGIN, dynamic GRANT CONNECT via
current_database(), schema/sequence USAGE, INSERT+SELECT-only on
ledger_entries and verification_records, no password committed,
environment-agnostic).

Ran the real migration against local Docker Compose Postgres per the
task statement's own explicit instruction, not just writing the SQL -
started the auth-postgres-1 container, confirmed BUILD SUCCESS, then
verified the resulting database state directly: table ownership,
payment_app's LOGIN/CONNECT, the exact 4-row grant set, and - via real
TCP connections as payment_app from a separate client container (never
docker exec, which bypasses password auth via loopback trust) - UPDATE
denied, DELETE denied, CREATE TABLE denied, SELECT succeeds.

Adds SchemaBaselineMigrationIntegrationTest (17 tests, Testcontainers
Postgres), mirroring crypto-service's own ChainBaselineMigrationIntegrationTest
template. Found and fixed 2 real bugs while writing it (a comment-vs-
content ambiguity already hit twice in T01's own tests, now in SQL; a
genuine information_schema row-multiplication bug), then 7 more
following Kimi's test review (Phase 11) - idempotency proven by real
SQL re-execution not just Flyway's own bookkeeping, exact privilege-type
assertions, an environment-agnostic-database-name guard whose mutation
test broke the real migration outright, DML denial extended beyond
SELECT, a DB-level L6 money-type check, RETURNING-based seed helpers
(which surfaced a second real bug - a tx_hash uniqueness collision -
fixed before it ever failed), and a structural V1-contains-no-grants
check independent of the byte-for-byte comparison.

One real factual error of my own, caught by Kimi's design challenge
(Phase 3) and corrected before any code existed: Phase 0/1 miscounted
the baseline as 8 tables; design.md section 4c actually defines 9
(shedlock was dropped from the count).

Spec: spec/payment-service/tasks.md task 2. Locked decisions: L6, L7,
L12, L13 (L13 carries the same documented local-dev Flyway/README
carve-out already established in T01).

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

(The task-template's own generated trailer text names a different, stale model identity; the
trailer above is this session's real, current convention and is what was actually used on every
commit for this task.)

## Files changed

**Created**
- `services/payment/src/main/resources/db/migration/V1__payments_baseline.sql`
- `services/payment/src/main/resources/db/migration/V2__payment_app_role_and_grants.sql`
- `services/payment/src/test/java/com/themistra/payment/SchemaBaselineMigrationIntegrationTest.java`

**Modified**
- `services/payment/README.md` (local-dev `ALTER ROLE` instruction, mirroring crypto's own section)

**Process artifacts (not application code)**
- `.ai/prompts/payment/T02/artifacts/00-*.md` through `12-*.md` — the full 14-phase pipeline
  record for this task (this file is `13-*.md`).

## Summary

The `payments` schema now exists for real against a live Postgres: all 9 baseline tables, owned by
the admin role, with a least-privilege `payment_app` runtime role scoped to exactly the two
tables this task names. No Java entity/repository/service code exists yet (tasks 5, 9, 10, 15,
19, 23 each own their own table's grant and code, mirroring crypto-service's own incremental
V2–V9 pattern — the other seven tables deliberately have zero grant today). The task statement's
own explicit instruction to actually run the migration (not just write the SQL) was followed and
its real output captured; every claimed database behavior was then independently re-verified,
first manually (Phase 6) and then via a permanent, mutation-tested Testcontainers suite
(Phases 10–11).

## Testing performed

- Real `mvn -pl services/payment flyway:migrate` run against local Docker Compose Postgres:
  `BUILD SUCCESS`.
- Manual database verification (Phase 6): table ownership, role `LOGIN`/`CONNECT`, exact grant
  set, and real TCP permission checks (UPDATE/DELETE/DDL denied, SELECT succeeds) as `payment_app`
  from a separate client container.
- `SchemaBaselineMigrationIntegrationTest`: **17/17 passing** (plus 8 carried over from T01 in the
  same module — **25/25 total**).
- Nine separate mutation tests across Phases 10 and 11 (two SQL-comment ambiguities, an
  `information_schema` DISTINCT bug, an accidental `UPDATE` grant, a pre-emptive grant on an
  ungranted table, a widened `NUMERIC` scale, a hardcoded database name, and a missing `INSERT`
  grant) — each confirmed to actually fail the relevant test (or, in the hardcoded-name case,
  break the real migration outright), then reverted and confirmed clean.

## Specification references

- **Task:** `spec/payment-service/tasks.md` task 2 — "Schema V1."
- **Requirement IDs:** none (schema-only, no `requirements.md` R-number applies, consistent with
  every sibling service's own equivalent schema task).
- **LOCKED decisions:** L6 (money as `NUMERIC`, no floating-point type), L7 (append-only in
  practice, not just comment), L12 (table/module ownership mapping), L13 (secrets, with the one
  documented Flyway/README local-dev carve-out, same as T01's own).

---

**Phase 13 complete — all 14 phases (0–13) closed for payment-service T02.**
