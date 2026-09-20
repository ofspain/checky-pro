<!-- MODEL: Claude Sonnet — Phase 10 (Test Generation). -->

# auth · T40 · Phase 10 — Test Generation

## Test manifest

| Test | Acceptance criterion | Verifies |
|---|---|---|
| `LockoutPersistenceIntegrationTest.fiveFailuresLockARealAccountAndPersistARealRow` (assertion added) | AC4 | Automatic lock now writes an `account.locked` `auth_audit` row (T40's `AccountService.lock` fix) |
| `LockoutPersistenceIntegrationTest.successfulAttemptUnlocksARealLockedAccountAndClearsTheRealRow` (assertion added) | AC4 | Automatic unlock now writes an `account.unlocked` `auth_audit` row |
| `LockoutPersistenceIntegrationTest.resetLockoutOnARealLockedAccountClearsTheRowAndTransitionsToActive` (assertion added, Phase 9) | AC4 | `resetLockout`'s unlock path (a third caller of the same fixed code) is also audited |
| `AccountServiceTest` (49 tests, unchanged assertions, all still passing) | AC4 | No regression from the `lock`/`unlock`/`adminUnlock` refactor — including the double-fire regression this task's own Phase 6 caught and fixed before it could ship |

Originally, three existing tests gained new assertions and no new `@Test` methods were added
(Phase 6/9). **Revised at Phase 11** (Kimi gap-closure, all four accepted): two new unit tests added
(mirroring `adminUnlockCalledTwiceOnlyAuditsAndPublishesOnce`'s existing idempotency pattern), and
the four existing/new tests strengthened with exact-count + null-actor + payload-status assertions
— all directly targeting the exact class of bug (double-firing) this task's own Phase 6 caught once
already, closing the same risk on the system-initiated path too:

| Test | New in Phase 11? | Verifies |
|---|---|---|
| `AccountServiceTest.lockTransitionsActiveToLocked` | Strengthened | `user.locked` payload status = `LOCKED`; `account.locked` audit row with `actorUuid=null` |
| `AccountServiceTest.lockNoOpsWhenAccountIsNotActive` | Strengthened | Escalating re-lock (already `LOCKED`) fires zero audit/publish calls — matches D-027's documented limitation |
| `AccountServiceTest.lockCalledTwiceOnlyAuditsAndPublishesOnce` | **New** | Idempotency: second `lock` call on an already-`LOCKED` account fires nothing additional |
| `AccountServiceTest.unlockTransitionsLockedToActive` | Strengthened | `account.unlocked` audit row with `actorUuid=null`; `user.unlocked` published |
| `AccountServiceTest.unlockNoOpsWhenAccountIsNotLocked` | Strengthened | No-op unlock fires zero audit/publish calls |
| `AccountServiceTest.unlockCalledTwiceOnlyAuditsAndPublishesOnce` | **New** | Idempotency: second `unlock` call fires nothing additional |
| `LockoutPersistenceIntegrationTest` (all three T40 assertions) | Strengthened | Exact-count (`hasSize(1)`, not `anySatisfy`) + `actorUuid()==null`, against real Postgres |

## Documentation deliverable (no test surface)

`package.md`'s header bump and §11/§12 edits, `auth-decisions.md` D-030 — verified via direct source
cross-referencing at Phases 0, 4, 6, and 9, not test execution (no test surface for documentation).

## Kimi Phase 11 test review — gaps closed

All 7 findings verified against source before disposition. Kimi's own preamble noted Maven wasn't
available in its environment (`mvn: command not found`) — its review was source-inspection-only;
every accepted change was independently compiled and run for real here.

| Gap | Disposition |
|---|---|
| Gap 1 — automatic lock/unlock idempotency untested | **Accepted.** Added `lockCalledTwiceOnlyAuditsAndPublishesOnce`/`unlockCalledTwiceOnlyAuditsAndPublishesOnce`, mirroring the existing admin-path test exactly. |
| Gap 2 — `anySatisfy` doesn't catch double-emission | **Accepted.** All three `LockoutPersistenceIntegrationTest` assertions changed to `.filteredOn(...).hasSize(1)` — directly closes the same double-fire risk class this task's own Phase 6 self-caught once already. |
| Gap 3 — no assertion that `actorUuid` is `null` for system-initiated events | **Accepted.** Added to all unit and integration assertions. |
| Gap 4 — no test for the `user.locked` payload's status | **Accepted.** Added to `lockTransitionsActiveToLocked`. |
| Gap 5 — full-suite verification not independently reproducible by Kimi (no Maven in its environment) | **No code action possible** — not something a code/test change addresses; the verification record below is the answer. |
| Gap 6 — manifest groups the `resetLockout` assertion ambiguously | **Accepted.** Test manifest table above now lists it explicitly, not grouped. |
| Gap 7 — no automated doc-traceability check between `package.md` and `auth-decisions.md` | **Rejected** — disproportionate tooling investment for a documentation task, same reasoning as T38/T39's equivalent rejections. |

## Verification performed

- `mvn -pl services/auth clean test-compile` — clean, no errors, after every phase's changes.
- `mvn -pl services/auth test -Dtest=AccountServiceTest` — **51/51 pass** (49 + 2 new).
- `mvn -pl services/auth test -Dtest=LockoutPersistenceIntegrationTest` — **8/8 pass**, all three
  T40 assertions now exact-count + null-actor, against real Postgres.
- `mvn -pl services/auth verify` (full suite): **707 tests, 1 failure, 6 errors** — up 2 from Phase
  9 (the two new unit tests), same, unchanged Groups A/B failure set; zero regressions.

---

**Phase 10 complete — test manifest written and updated post-Phase-11.** Proceed to Phase 12
(Specification Verification) on approval.
