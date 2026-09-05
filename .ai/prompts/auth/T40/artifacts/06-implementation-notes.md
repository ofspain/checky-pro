<!-- MODEL: Claude Sonnet — Phase 6 (Implementation). -->

# auth · T40 · Phase 6 — Implementation Notes

## What changed

- **`services/auth/src/main/java/com/themistra/auth/account/AccountService.java`** —
  `lock(UUID)` now publishes `"user.locked"` and records `"account.locked"` on a real
  `ACTIVE→LOCKED` transition. `unlock(UUID)`/`adminUnlock(UUID, UUID)` were unified into a single
  private `unlock(UUID accountUuid, UUID actorUuid)` that both public methods delegate to — the
  only place that fires the event/audit, exactly once, gated on a real `LOCKED→ACTIVE` transition.
- **`services/auth/src/test/java/com/themistra/auth/authn/LockoutPersistenceIntegrationTest.java`**
  — two new assertions (in the existing `fiveFailuresLockARealAccountAndPersistARealRow` and
  `successfulAttemptUnlocksARealLockedAccountAndClearsTheRealRow` tests) proving the automatic
  lock/unlock path now genuinely writes an `auth_audit` row.
- **`spec/auth-service/package.md`** — §11 Q2 (resolved, cites D-026), Q3 (partially resolved,
  precisely stated), Q4 (marked out-of-scope for this service), Q5 (resolved, cites the fix); new
  §12 documenting the test-suite status at bump time; header Status `DRAFT`→`READY FOR IMPL`,
  Version `0.1`→`0.2`.

## Deviation forced by reality (flagged, not hidden)

**A real regression, caught by running the actual test suite, not assumed safe.** My first
implementation added the audit/event calls directly inside the plain `unlock(UUID)` method. Since
`adminUnlock` calls `unlock(accountUuid)` internally and then *also* fired its own
`publishLifecycleEvent`/`recordAudit`, this double-fired both the outbox publish and the audit
record on every real admin unlock — caught immediately by running `AccountServiceTest`
(`shouldUnlockAccountViaAdminEndpoint` failed: `Wanted 1 time... But was 2 times`). Fixed by
unifying both callers onto a single private `unlock(UUID, UUID actorUuid)` method that fires
exactly once — cleaner than the originally planned two-separate-call-sites design, and eliminates
the double-fire class of bug entirely rather than just this one instance of it. `adminUnlock` is now
simpler as a result (delegates entirely, no duplicated logic).

## Verification performed

- `mvn -pl services/auth clean test-compile` — clean, no errors.
- `mvn -pl services/auth test -Dtest=AccountServiceTest` — first run: 2 failures (the double-fire
  regression). After the unify-into-one-method fix: **49/49 pass**.
- `mvn -pl services/auth test -Dtest=LockoutPersistenceIntegrationTest` — **8/8 pass**, including
  the two new audit-row assertions, run against real Postgres (Testcontainers) — genuine proof the
  fix works, not inferred from the unit-test mocks alone.
- `mvn -pl services/auth test -Dtest=AccountTest,LockoutServiceTest,AdminAccountControllerTest` —
  all pass, no impact (mocked `AccountService` in `LockoutServiceTest`/`AdminAccountControllerTest`
  is unaffected by internal changes; `AccountTest` covers the `Account` entity, untouched).
- `mvn -pl services/auth verify` (full suite): **705 tests, 1 failure, 6 errors** — up 3 tests from
  the prior baseline (the new assertions), identical Groups A/B failure signatures, zero new
  failures or regressions.

---

**Phase 6 complete — implementation written, verified, one self-caught regression fixed before it
could ship.** Proceed to Phase 7 (Self Review) on approval.
