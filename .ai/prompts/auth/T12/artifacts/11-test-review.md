# auth · T12 — Phase 11: Test Review

Reviewed the Phase 10 test manifest and the actual test files
(`LockoutServiceTest.java`, `LockoutPropertiesTest.java`,
`LockoutPersistenceIntegrationTest.java`, and the new `AccountServiceTest.java` lock/unlock tests)
against the frozen brief (`04-frozen-task-brief.md`) acceptance criteria and Required Tests.

---

## Gap 1 — No test proves the pessimistic lock actually serializes concurrent updates

- **Why it matters.** The frozen brief Required Tests explicitly call for a "pessimistic-lock-respecting test, not just single-threaded logic." `LockoutServiceTest` uses a mocked repository, so it cannot prove `FOR UPDATE OF ls` is effective. `LockoutPersistenceIntegrationTest` runs against real Postgres but is single-threaded. The concurrency-critical property — that two simultaneous evaluations for the same account cannot lose a counter increment — is therefore unverified.
- **Suggested test.** Add a Testcontainers-backed test (or extend the integration test) that fires two concurrent `recordFailedAttempt` calls for the same account starting from an existing row and asserts the final `failedAttempts` equals the sum of both calls (e.g., two calls from `failedAttempts=3` should result in `5`, not `4`).

---

## Gap 2 — Unit tests assert counters but not the persisted timestamp/duration fields

- **Why it matters.** `LockoutServiceTest` asserts `getFailedAttempts()` and `getLockCount()` on the persisted `LockoutState`, but never `getLastFailedAt()` or `getLockedUntil()`. A regression in `LockoutState.applyDecision` that failed to copy the nullable `Instant` fields would go undetected at the unit layer.
- **Suggested test.** In `shouldLockAccountAfterFiveFailedAttemptsWithinThirtyMinutes` and `reLockWhileAccountStatusStillLockedDoesNotThrowAndStillLocksAgain`, add assertions that the saved state has the expected `lastFailedAt` and `lockedUntil` values (use an `ArgumentCaptor` if the mock returns the same instance).

---

## Gap 3 — No focused unit test for a `NONE` decision that persists updated counters

- **Why it matters.** AC4 requires that a decayed or non-locking `NONE` decision still persists the updated counters. The named test builds up to the 5th failure but starts from `failedAttempts=4`, so it tests only the `LOCK` path. There is no focused unit test for an intermediate failure (e.g., 3 → 4) that asserts `repository.save(...)` is called, `failedAttempts` is incremented, and no `AccountService` method is invoked.
- **Suggested test.** Add `nonLockingFailureStillPersistsUpdatedCounters`: start with `existingRow(3, T0, null, 0)`, evaluate a failure one minute later, assert `decision.statusChange() == NONE`, `decision.failedAttempts() == 4`, `repository.save(...)` called once, and zero `AccountService` interaction.

---

## Gap 4 — No unit test for post-unlock decay that emits `UNLOCK`

- **Why it matters.** T11's Phase 9 fix returns `UNLOCK` when a failure evaluated after `lockedUntil` has elapsed does not re-lock because the decay window has passed. `LockoutService` would then call `accountService.unlock(...)`. None of the current tests exercise this transition through the service layer.
- **Suggested test.** Add `postUnlockDecaySignalsUnlockAndClearsLockedUntil`: start with a locked row whose `lastFailedAt` is more than 30 minutes before `now` and whose `lockedUntil` is in the past. Evaluate `FAILURE`. Assert `decision.statusChange() == UNLOCK`, `decision.lockedUntil() == null`, `accountService.unlock(...)` called exactly once, and `accountService.lock(...)` never called.

---

## Gap 5 — `LockoutPropertiesTest` only asserts `@Min(1)` rejects zero, not negative values

- **Why it matters.** `@Min(1)` also rejects negative inputs; the boundary is `<= 0`, not just `== 0`. Testing only the zero case leaves the negative-boundary behavior unverified.
- **Suggested test.** Extend each of the three "shouldRejectNonPositive..." tests (or replace them with a parameterized test) to cover both `0` and `-1` for the field in question.

---

## Gap 6 — Integration tests miss re-lock, blocked-attempt, and `resetLockout` edge cases

- **Why it matters.** The Testcontainers suite is the only place that exercises the real native queries, the real `LockoutStateMachine`, and the real `AccountService` chain together. It currently covers only the happy-path first lock and first unlock. Edge cases such as re-lock after expiry (T11 AC7), a blocked attempt before `lockedUntil`, and `resetLockout` are only covered by mocked unit tests.
- **Suggested test.** Add three integration tests: (a) a second lock cycle after expiry results in `lockCount=2` and `AccountStatus.LOCKED`; (b) a failure one instant before `lockedUntil` leaves `Account.status` and counters unchanged; (c) `resetLockout` on a locked account clears the row and transitions back to `ACTIVE`.
