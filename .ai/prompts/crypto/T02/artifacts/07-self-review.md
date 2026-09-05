<!-- MODEL: Claude Sonnet — Phase 7 (Self Review). -->

# crypto · T02 · Phase 7 — Self Review

Re-read all four touched files against AC1–AC4, then went beyond a text re-read: exercised the one
part of Phase 6's verification that was asserted but not actually proven — that `V2`'s `CREATE ROLE`
guard is genuinely idempotent, not just written to look idempotent.

## Additional verification performed this phase

**`V2`'s `DO $$ IF NOT EXISTS ... END $$` guard, proven under a real re-run, not just read.**
`flyway:clean` is blocked by Flyway 11's own `cleanDisabled=true` default (a real safety feature,
discovered by trying it), so I manually reproduced the scenario it would have tested: dropped the
`chain` schema directly as `checky` (`DROP SCHEMA chain CASCADE`), confirmed the `crypto_app` *role*
survived (roles are cluster-level, schemas are not), then re-ran `mvn -pl services/crypto
flyway:migrate`. Both migrations reapplied cleanly, `V2` included — `BUILD SUCCESS`.

**Negative-proof that the guard is doing real work, not a no-op**: ran a raw, unguarded `CREATE ROLE
crypto_app ...` directly against the same already-existing role and got exactly the failure the guard
exists to prevent: `ERROR: role "crypto_app" already exists`. Confirms this isn't a theoretical
scenario — schema-drop-and-recreate without a role reset is a realistic local-dev pattern (e.g., a
developer resets their schema during T03+ work without tearing down the whole Postgres container),
and without the guard, `V2` would break on the very next `flyway:migrate` after that.

Re-spot-checked the grant behavior after the re-migration (insert as `crypto_app`, succeeded; cleanup
as `checky`) — the grants aren't a one-time artifact of the first migration run, they hold after a
schema recreation too, since `V2` reapplies them against the freshly-created tables each time.

## Checked and found correct

- **AC1**: `diff` against a fresh extraction from `design.md` — empty (Phase 6), re-confirmed no
  drift since.
- **AC2**: `\dt chain.*` — 10 tables + `flyway_schema_history`, all owned by `checky`.
- **AC3**: real INSERT/UPDATE/DELETE attempts on all three named tables (Phase 6), plus this phase's
  additional `watches`-denial and owner-unaffected checks — the grant is exactly as scoped as
  intended, no broader and no narrower.
- **AC4**: `spring.flyway.enabled=false` present; no runtime auto-migration attempted this session
  since the app was never started (correctly out of this task's own scope).
- **pom.xml/application.properties**: XML/properties syntax already proven valid by every successful
  Maven invocation this task (`validate`, `flyway:migrate` x2, `verify`) — no separate parse check
  needed.

## Noted, not changed

The `WARNING: DB: schema "chain" already exists, skipping (SQL State: 42P06)` line every migration
run emits is expected, not a defect: Flyway's own `<schemas>chain</schemas>` plugin config creates
the schema before running `V1`, and `V1`'s own (verbatim, unmodifiable) `CREATE SCHEMA IF NOT EXISTS
chain` then finds it already there. Harmless by construction (`IF NOT EXISTS`), but worth naming
here so a future reader of a migration log doesn't mistake it for something broken.

## Verification re-run

`git status --porcelain services/auth` — empty, both before this phase's additional checks and after.
No file under `services/auth` touched at any point in T02.

---

**Phase 7 complete — self review done; the one previously-unproven claim (role-creation idempotency)
was tested for real, not just re-read.** Proceed to Phase 8 (Independent Review) on approval.
