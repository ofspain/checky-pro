# notification · T02 · Phase 9 — Review Resolution

Disposition of all 6 findings from `artifacts/08-independent-review.md`. All accepted and fixed.
No changes to the VERBATIM `V1__notifications_baseline.sql`, or to `V2`/`V3`.

## Finding 1 (High) — real `flyway:migrate` needs a citext-into-`notifications` pre-step

**Accepted.** Verified: `V1` line 5 (`SET search_path TO notifications;`) replaces the search path
entirely, so `citext` must already be visible inside `notifications` before `V1` runs; a plain
`public`-schema install (what `services/auth`'s own migration already does) is not enough, and
PostgreSQL allows only one installation of a given extension per database.

This is an environment-provisioning fact, not a code defect, and `V1` is immutable VERBATIM, so the
fix is documentation, not SQL. Added a "Local development database" section to
`services/notification/README.md` (mirroring `services/crypto/README.md`'s own precedent section)
recording both real cases with their exact commands:
- fresh database, `citext` not installed anywhere: `CREATE EXTENSION IF NOT EXISTS citext SCHEMA
  notifications`;
- `citext` already installed in `public` by `services/auth`'s own migration (the realistic case on
  the shared local stack): `ALTER EXTENSION citext SET SCHEMA notifications`, which was the actual
  command run against `auth-postgres-1` in Phase 6, approved by the user before touching shared
  state, and verified afterward not to break `auth.accounts.email`.

Frozen artifacts (00-04) were not retroactively rewritten, per this pipeline's own discipline of
correcting forward rather than editing history.

## Finding 2 (Medium) — ungranted-tables test only proved SELECT denial

**Accepted.** `notificationAppHasNoAccessAtAllToTablesOutsideAc2Scope` now also attempts a minimal
`INSERT` against each of the 7 ungranted tables and asserts `permission denied`, via a new
`minimalInsertFixtureFor(table)` helper (one literal-valued INSERT per table's own NOT NULL
columns). Kept as one test method rather than splitting, since both checks share the same
loop/connection and the name's own "no access at all" claim is now actually true.

## Finding 3 (High) — self-review didn't record the real `flyway:migrate` result

**Accepted**, resolved here rather than by rewriting the already-committed `07-self-review.md`. For
the record: the real command was run in Phase 6 (`.ai/prompts/notification/T02/artifacts/06-implementation-notes.md`,
steps 5-6) and **succeeded** against the real shared local Postgres, after the Finding-1 citext
relocation was applied. `07-self-review.md` only restated the Testcontainers `verify` run and
omitted this; that omission is now closed by this entry plus the new README documentation, so a
future environment has a written, repeatable path to the same success rather than depending on
Phase 6's own notes being read.

## Finding 4 (Low) — SQL string concatenation in test helpers

**Accepted.** `assertInsertAndSelectSucceedUpdateAndDeleteAreDenied`, `insertStatementFor`, and
`cleanUpAsAdmin` now bind `sourceEventKey` via `PreparedStatement` instead of concatenating it into
SQL text. Table names remain concatenated (a fixed internal constant list, not user input — JDBC
cannot parameterize identifiers regardless).

## Finding 5 (Low) — template subject convention unguarded

**Accepted.** `launchTemplatesAreSeededWithVersionOne` now also selects `subject` and asserts every
`EMAIL` row has a non-null subject and every `IN_APP` row has a null subject, without asserting
exact copy.

## Finding 6 (Low) — `extractFirstSqlFence` brittle against future `design.md` edits

**Accepted.** `extractFirstSqlFence` now asserts the extracted fence starts with `-- Notification
Service baseline (notifications schema).` before returning it, so a future `design.md` edit adding
an earlier SQL fence fails loudly with a clear message instead of silently comparing `V1` against
the wrong block.

## Verification

`mvn -pl services/notification -am verify` — 19 tests, 0 failures (9 `T01SkeletonRegressionTest` +
10 `NotificationBaselineMigrationIntegrationTest`, same counts as Phase 7's own record; all 6 fixes
landed as assertion/helper changes inside existing test methods, no new `@Test` methods added).
