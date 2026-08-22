<!-- MODEL: Claude Sonnet — Phase 10 (Test Generation). -->

# auth · T40 · Phase 10 — Test Generation

## Test manifest

| Test | Acceptance criterion | Verifies |
|---|---|---|
| `LockoutPersistenceIntegrationTest.fiveFailuresLockARealAccountAndPersistARealRow` (assertion added) | AC4 | Automatic lock now writes an `account.locked` `auth_audit` row (T40's `AccountService.lock` fix) |
| `LockoutPersistenceIntegrationTest.successfulAttemptUnlocksARealLockedAccountAndClearsTheRealRow` (assertion added) | AC4 | Automatic unlock now writes an `account.unlocked` `auth_audit` row |
| `LockoutPersistenceIntegrationTest.resetLockoutOnARealLockedAccountClearsTheRowAndTransitionsToActive` (assertion added, Phase 9) | AC4 | `resetLockout`'s unlock path (a third caller of the same fixed code) is also audited |
| `AccountServiceTest` (49 tests, unchanged assertions, all still passing) | AC4 | No regression from the `lock`/`unlock`/`adminUnlock` refactor — including the double-fire regression this task's own Phase 6 caught and fixed before it could ship |

No new `@Test` methods were added — three existing tests gained new assertions proving the
`AccountService` fix's real-world behavior, matching the frozen brief's proportionate scope
(a small, targeted fix, not new test-authorship for its own sake).

## Documentation deliverable (no test surface)

`package.md`'s header bump and §11/§12 edits, `auth-decisions.md` D-030 — verified via direct source
cross-referencing at Phases 0, 4, 6, and 9, not test execution (no test surface for documentation).

## Verification performed

- `mvn -pl services/auth clean test-compile` — clean, no errors, after every phase's changes.
- `mvn -pl services/auth test -Dtest=LockoutPersistenceIntegrationTest` — 8/8 pass.
- `mvn -pl services/auth test -Dtest=AccountServiceTest` — 49/49 pass.
- `mvn -pl services/auth verify` (full suite): 705 tests, 1 failure, 6 errors — the same, unchanged
  Groups A/B failure set throughout every phase since Phase 6's fix landed; zero regressions.

---

**Phase 10 complete — test manifest written.** Proceed to Phase 11 (Kimi Test Review) on approval.
