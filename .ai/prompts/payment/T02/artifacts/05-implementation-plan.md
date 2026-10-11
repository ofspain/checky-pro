# payment · T02 · Phase 5 — Implementation Plan

Traces to the frozen brief (`04-frozen-task-brief.md`) only. No file or decision beyond it.

## Files to create

1. `services/payment/src/main/resources/db/migration/V1__payments_baseline.sql`
2. `services/payment/src/main/resources/db/migration/V2__payment_app_role_and_grants.sql`
3. `services/payment/src/test/java/com/themistra/payment/SchemaBaselineMigrationIntegrationTest.java`

## Files to modify

1. `services/payment/README.md` — add the local-dev `ALTER ROLE payment_app PASSWORD
   'payment-app-local-only';` instruction line, mirroring `services/crypto/README.md`'s own
   identical shape (frozen brief, L13 carve-out).

## Public methods (signatures)

None (no Java production code — DDL/role statements only; the test class's own `@Test`/
`@BeforeAll` methods are listed under Tests below, not "public methods" in the production sense).

## Private methods

None.

## Entities used

None (no JPA entities in this task — tasks 5, 9, 10, 15, 19, 23 each own their own table).

## Repositories used

None.

## Services used

None.

## Unit / integration tests required

- `com.themistra.payment.SchemaBaselineMigrationIntegrationTest` — Testcontainers Postgres, real
  JDBC, no Spring context (mirrors `services/crypto`'s own
  `ChainBaselineMigrationIntegrationTest` shape exactly). Test methods (AC-traced):
  1. `baselineCreatesAllNineTablesWithTheirCheckConstraints` — AC1.
  2. `migrationSucceedsAndPaymentAppIsCreatedWithLoginAndConnect` — AC2.
  3. `paymentAppCanInsertAndSelectOnLedgerEntriesAndVerificationRecords` — AC3 (positive half).
  4. `paymentAppCannotUpdateOrDeleteLedgerEntriesOrVerificationRecords` — AC3 (negative half, a
     real Postgres permission-denied error caught and asserted, not merely "no exception").
  5. `paymentAppInsertUsesTheIdentitySequenceWithoutASeparatePermissionFailure` — AC4.
  6. `paymentAppHasNoGrantOnTheOtherSevenTables` — AC5 (a real
     `information_schema.table_privileges` query for `payment_app`, asserting zero rows for
     `invoices`, `payments`, `receipts`, `ledger_anchors`, `processed_events`, `outbox`,
     `shedlock`).
  7. `noPasswordIsCommittedInTheRoleMigration` — AC6 (a text-scan of `V2`'s own file content —
     this one assertion is legitimately a file-content check, not a DB behavior check, mirroring
     how T01's own equivalent checks worked).
  8. `baselineTablesAreOwnedByTheMigrationRoleNeverByPaymentApp` — AC7, mirrors crypto's own
     identically-named-in-spirit test.
  9. `paymentAppCannotCreateOrDropTablesInThePaymentsSchema` — AC8.

## Execution order

1. **`V1__payments_baseline.sql`** — the full 9-table baseline, copied verbatim from `design.md`
   §4c (re-verified directly against the spec file in this phase, not from memory, after the
   8-vs-9 table count error found at Phase 3).
2. **`V2__payment_app_role_and_grants.sql`** — role creation (`LOGIN`), dynamic `GRANT CONNECT`,
   schema `USAGE`, sequence `USAGE, SELECT`, then the two named tables' INSERT+SELECT-only grant.
3. **Manual migration run** — `mvn -pl services/payment flyway:migrate` against local Docker
   Compose Postgres, confirming AC1/AC2 with real output before writing the automated test (the
   task statement's own explicit instruction to actually run it, not just write the SQL).
4. **`README.md`** — add the one local-dev `ALTER ROLE` line.
5. **`SchemaBaselineMigrationIntegrationTest.java`** — written last, against the now-real
   migrations, in AC order (1 → 2 → 3 → 4 → 5 → 6 → 7 → 8).
6. **Final check** — `mvn -pl services/payment test` (now requires Docker/Testcontainers, unlike
   T01's own Docker-free suite) and a mutation pass on at least the two most load-bearing
   assertions (the UPDATE/DELETE denial, and the "no grant on the other seven tables" check),
   mirroring this task's own established discipline from T01.
