# notification · T05 · Phase 9 — Review Resolution

Disposition of the 6 findings from `artifacts/08-independent-review.md`, cross-referenced with
`artifacts/07-self-review.md`'s own 2 findings (Kimi's own Finding #5 is the same substance as
self-review's own Finding #2 — resolved once, together, below).

## Finding 1 (Kimi) — required T05 tests are entirely missing

**Disposition: DEFERRED, not implemented now.** Verified true: no `preference/` test directory
exists yet. Same disposition as T03/T04's own Phase 9 precedent: Phase 6's own explicit rule ("tests
are Phase 10's job") defers all new tests, and this is not a defect in T05's own completion state at
this point in the pipeline. Self-review's own Finding 1 already closed the *risk* this finding also
names (the core upsert SQL was unverified) via a temporary, deleted scratch test proving the full
insert/update/stale-rejection sequence works — Kimi's own recommended test list is retained verbatim
as Phase 10's own required-test list, including the explicit out-of-order and transaction-join cases.

## Finding 2 (Kimi) — `JpaRepository` inheritance exposes `save`/`delete`, bypassing the out-of-order guard

**Disposition: ACCEPTED, fixed (Kimi's own offered option 2).** Verified: `ContactProjectionRepository`
does inherit `save`/`saveAll`/`delete` from `JpaRepository`, none of which apply the `WHERE
updated_at <= EXCLUDED.updated_at` guard. The frozen brief explicitly specified `extends
JpaRepository<ContactProjection, UUID>`, so narrowing the interface (Kimi's own option 1) would
contradict an already-frozen decision — not done. Instead, added a prominent Javadoc warning on the
interface itself naming `upsertEmail` as the only sanctioned write path and explaining why the
inherited mutators exist without being a valid alternative.

## Finding 3 (Kimi) — no test proves `display_name` stays null

**Disposition: ALREADY TRACKED, no new action.** This is the same substance as the Phase 4 frozen
brief's own Finding #5 disposition (Kimi's own Phase 3 review), which already added this exact
assertion to Phase 10's own Required Tests. Re-verified the required-test list still names it
explicitly — nothing to add.

## Finding 4 (Kimi) — no test verifies `@Transactional` propagation behavior

**Disposition: ALREADY TRACKED, no new action.** The Phase 5 implementation plan already specifies
a reflection-based propagation test for `ContactProjectionUpdaterUnitTest`, mirroring
`IdempotencyGuardUnitTest.recordIfNewUsesDefaultRequiredPropagation` exactly, plus the
transaction-join/rollback integration test. Re-verified both are still in Phase 10's own required-test
list — nothing to add.

## Finding 5 (Kimi) / Self-review Finding 2 — `upsertEmail` discards the affected-row count

**Disposition: ACCEPTED, fixed.** `ContactProjectionUpdater.upsertEmail`'s return type changed from
`void` to `boolean` (`true` = accepted, `false` = rejected as stale by the out-of-order guard),
mirroring `IdempotencyGuard.recordIfNew`'s own shape exactly. No caller exists yet to consume the
new return value (task 6's own scope) — this closes the observability gap proactively rather than
requiring a follow-up refactor later.

## Finding 6 (Kimi) — `V5` migration comment implies `display_name` is revised

**Disposition: ACCEPTED, fixed.** Reworded: "email and updated_at are revised... display_name is
mapped but never written by this task (no data source exists anywhere in auth's own domain)" —
matches `ContactProjection`'s own Javadoc and AC4 exactly, removing the ambiguity Kimi identified.

## Verification

`mvn -pl services/notification clean verify` — 80 tests, 0 failures (the `upsertEmail` return-type
change is source-compatible with every existing caller — there are none yet outside this task's own
files). `git status -s services/auth services/crypto` — empty; no sibling service touched.
