<!-- MODEL: Claude Sonnet — Phase 10 (Test Generation). -->

# crypto · T02 · Phase 10 — Test Generation

## Outcome: one real Testcontainers integration test, proactively (not waiting for Kimi to flag it)

`package.md` §8's 28 named tests still map to zero T02 behavior (schema/migration only, no business
logic). But unlike T01 (pure static file content), T02 produces genuine runtime database behavior —
Phase 11's own precedent at T01 (Kimi correctly pushed back that "no feature tests" isn't the same as
"no tests for this task's own ACs") applies even more directly here, so this phase writes the guard
test up front instead of waiting for another review cycle to catch the gap.

Wrote `services/crypto/src/test/java/com/themistra/crypto/ChainBaselineMigrationIntegrationTest.java`
— a real Testcontainers Postgres integration test, not a text scan. Runtime Flyway is disabled by
design (Phase 4/9), so there's no Spring-context `@ServiceConnection` auto-migration path to use; the
test runs both migrations via the Flyway Java API directly against a fresh `postgres:16-alpine`
container, then connects **over real TCP/JDBC** (the same auth path Phase 9 confirmed enforces real
password checking, unlike a `docker exec` shell connection).

4 tests:
1. `allTenBaselineTablesExist` — AC1/AC2.
2. `cryptoAppRoleRequiresItsProvisionedPassword` — wrong password rejected with a real Postgres auth
   error; correct (test-provisioned) password succeeds. Directly encodes Phase 9's own design
   decision that `V2` alone grants no login capability.
3. `cryptoAppCanInsertAndSelectButNotUpdateOrDeleteOnTheThreeGrantedTables` — AC3's positive and
   negative cases on all three named tables.
4. `cryptoAppHasNoAccessToTablesOutsideAc3Scope` — the grant is exactly as scoped as intended.

**Negative-proof**: mutated `V2` to add an over-broad `UPDATE` grant on the three named tables, re-ran
test 3 alone — it failed, catching the regression. Reverted; full suite green again
(`mvn -pl services/crypto verify` — `BUILD SUCCESS`). `git status --porcelain services/auth` — empty.

## What still needs no test

Everything `package.md` §8 names (R1-R28, L15) — no feature code exists. `AC4`
(`spring.flyway.enabled=false`) already has direct proof from Phase 9's own manual smoke test
(correct-password boot succeeds with zero Flyway log activity, wrong-password boot fails with a real
auth error) — encoding that specific check as an automated test would mean either starting the full
Spring context in-test (meaningfully heavier than this task's scope justifies for a property that's a
one-line, unlikely-to-silently-regress config value) or duplicating what test 2 above already proves
at the JDBC layer (a wrong/no password is rejected) — test 2 already covers the substance of what
would go wrong if that property were flipped back on incorrectly.

---

**Phase 10 complete — a real, mutation-tested integration test written proactively, not scoped down
to "no tests" and left for a later review to catch.** Proceed to Phase 11 (Test Review) on approval.

## Addendum (post Phase 11) — 8 of 9 findings accepted and added; 1 verified false against source

Kimi's Phase 11 review raised 9 findings. Its own headline claim — Finding 1, that the whole suite
"cannot pass" because `V2` hardcodes `GRANT CONNECT ON DATABASE checky` — was checked against the
actual current file and its full git history before acting on it: the file has contained the
dynamic-SQL `current_database()` fix (Phase 9) since its very first commit; there is no version of
this file, ever, with a hardcoded database name. Kimi's own review explicitly states it could not
run Maven and was reading the committed file - it appears to have reviewed stale or cached content,
not what's actually there. This suite has run green, repeatedly, throughout this task. No action
taken on Finding 1 beyond recording that it was checked and found false.

Findings 2-9 were all real, verified against the actual test file, and led to genuine additions
(going from 4 tests to 10):

- **#2** (weak table-existence check) — resolved two ways: `containsAll` → `containsExactlyInAnyOrderElementsOf`
  (catches stray tables), plus a new, stronger test that automates Phase 6's manual `diff`: reads
  `design.md`'s verbatim SQL fence directly and asserts `V1__chain_baseline.sql` is byte-identical to
  it. Chosen over Kimi's suggested column/constraint introspection companion test because the
  byte-diff already proves total fidelity more strongly and without duplicating the schema in test code.
- **#3** (no automated idempotency proof) — added: re-invoke `Flyway.migrate()` a second time,
  assert no exception.
- **#4** (no positive SELECT proof) — added: query back each inserted row, assert count = 1.
- **#5** (scope-denial covers only 1 table) — extended from `watches` alone to all 7 non-AC3 tables.
- **#6** (no AC4 test) — added: `application.properties` loaded via `java.util.Properties`, asserts
  `spring.flyway.enabled=false`.
- **#7** (no DDL-denial proof) — added: `CREATE TABLE` and `DROP TABLE` as `crypto_app` both denied.
- **#8** (no migration-history proof) — added: both versioned rows present and `success=true`.
- **#9** (no ownership proof) — added: no `chain` table is owned by `crypto_app`.

**Two real bugs found in my own new test code while first running it** (both fixed): the
migration-history query needed to exclude Flyway's own synthetic unversioned schema-creation row;
the DDL-denial test asserted the wrong Postgres error string for `DROP TABLE` (real message is "must
be owner of table X", not "permission denied" — a different, legitimate denial class from
`CREATE TABLE`'s schema-privilege denial).

**Negative-proof**: mutation-tested the two most novel additions individually — appended a stray
comment to `V1` (byte-diff test caught it) and transferred `chain.observations`' ownership to
`crypto_app` via a scratch statement (ownership test caught it) — both reverted after confirming
failure. Full suite: 10/10 green. `mvn -pl services/crypto verify` — `BUILD SUCCESS`.
`mvn -pl services/auth validate` — `BUILD SUCCESS`; `git status --porcelain services/auth` — empty.
