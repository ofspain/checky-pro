# auth · T12 — Phase 12: Specification Verification

Verifying the final implementation (`LockoutState`, `LockoutStateRepository`,
`LockoutProperties`, `LockoutService`; `AccountService.lock`/`unlock`) and test suite (60 executed
+ 8 Testcontainers-only) against `spec/auth-service/requirements.md`, `design.md`, and `tasks.md`
for T12 only.

---

## Traceability matrix

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R16** — failed attempt for an eligible account increments the counter | Yes | `LockoutService.java:55-68` (`recordFailedAttempt`), delegates to `LockoutStateMachine` (T11) | `LockoutServiceTest.nonLockingFailureStillPersistsUpdatedCounters`, named test | No | No — audit emission (R16's other half) remains T13's scope, as scoped at Phase 2/4 |
| **R17** — 5th failure locks, increments `lock_count` | Yes | `LockoutService.java:55-68` (`LOCK` branch), `AccountService.java:316-321` (`lock`, guarded) | `LockoutServiceTest.shouldLockAccountAfterFiveFailedAttemptsWithinThirtyMinutes`, `LockoutPersistenceIntegrationTest.fiveFailuresLockARealAccountAndPersistARealRow` (Testcontainers, unexecuted here) | No | No |
| **R18** — post-unlock success resets and unlocks | Yes | `LockoutService.java:81-95` (`recordSuccessfulAttempt`), `AccountService.java:324-330` (`unlock`, guarded) | Named test, `LockoutPersistenceIntegrationTest.successfulAttemptUnlocksARealLockedAccountAndClearsTheRealRow` (Testcontainers, unexecuted here) | No | No |
| **R19** — counter decays after 30 min inactivity | Yes | Delegated entirely to `LockoutStateMachine.evaluate` (T11); `LockoutService` only persists the result | `LockoutServiceTest.postUnlockDecaySignalsUnlockAndClearsLockedUntil` (proves decay-driven `UNLOCK` reaches `AccountService` through this task's own wiring, not just T11's pure logic) | No | No |

**Named tests (`package.md` §8):**
- `shouldLockAccountAfterFiveFailedAttemptsWithinThirtyMinutes` — exists verbatim in both
  `LockoutServiceTest.java:53` and (already, from T11) `LockoutStateMachineTest.java`. Same
  package.md numbering drift as T11 (maps to R15/R17 depending on source — confirmed, not fixed,
  same pre-existing issue).
- `shouldResetLockoutCounterOnSuccessfulLogin` — exists verbatim in `LockoutServiceTest.java:69`,
  proves the service-layer wiring on top of T11's already-proven decision logic.

---

## Self-correction: the "first cross-module service dependency" claim was wrong

Phases 0, 1, and 2 of this task's own artifacts repeatedly stated that `LockoutService` injecting
`AccountService` would be "the first cross-module (`authn` → `account`) service dependency in this
codebase," based on a grep that only checked `authz`/`audit`. Re-verified this phase:
`com.themistra.auth.authn.AccountUserDetailsService` (pre-existing, not part of this task) already
injects `AccountService` and imports `AccountStatus`/`LoginView` from `account`
(`AccountUserDetailsService.java:3-5,23,25-26`) — it bridges accounts into the SAS login flow.
This means `LockoutService`'s approach isn't a novel precedent at all; it's consistent with an
existing, already-established pattern in this exact package. This doesn't change anything about
this task's design or correctness — the chosen approach (depend on `AccountService`, never
`Account`) was right regardless — but the earlier phases' claim of novelty was inaccurate and is
corrected here rather than left standing.

---

## Principal-engineer assessment

**(1) Is the task fully complete?** Yes. `LockoutService` loads/updates `lockout_state`
(`LockoutState`/`LockoutStateRepository`), handles decay (via `LockoutStateMachine`, T11), and
ties `Account.lock()`/`unlock()` to `AccountService` (the two new guarded methods) — exactly the
task statement's three clauses. `resetLockout` is also present, ahead of T14's need, per the
frozen brief's Finding 3 disposition.

**(2) Does it satisfy every acceptance criterion?** All ten (AC1-AC10) from the frozen brief are
implemented. AC1-AC9 are directly unit-tested (60/60 executed, passing). AC10 (concurrency) has a
real, purpose-built Testcontainers test (`concurrentFailedAttemptsDoNotLoseUpdates`, added at
Phase 11) but it could not be executed in this environment (no Docker) — see Remaining Risks.

**(3) Does it violate any LOCKED decision?** No. L4's constants flow through
`LockoutProperties` → `LockoutService`'s constructor → `LockoutStateMachine`, unchanged from T11's
adopted formula. L12 verified clean by direct `grep` this phase: the only
`com.themistra.auth.account` import across all four new `authn` files is `AccountService`, in
`LockoutService.java:3` — never `Account` itself.

**(4) Remaining risks:**
- **Not executed in this environment:** all 8 `LockoutPersistenceIntegrationTest` tests, including
  the concurrency proof (AC10) and the three Phase-11-added edge cases (second lock cycle, blocked
  attempt, `resetLockout`) — compile clean, reasoned through carefully, but genuinely unverified
  against real Postgres/Testcontainers here. This is the single largest residual risk carried out
  of this task; flagged consistently since Phase 5, not a late discovery.
- Module-wide `mvn -pl services/auth verify` still cannot run — the same pre-existing, unrelated
  `token` package compile break tracked since T03. This task's own classes were verified via
  isolated `javac` + JUnit Platform Launcher instead throughout (60/60 passing, most recent run
  this phase's own re-verification).
- `package.md` §11 Q5 (lifecycle-event publication on lock/unlock) remains genuinely unresolved by
  the spec author — this task deliberately emits nothing (Scope > Out), a reversible default, not
  a decision this task was positioned to make.
- The account-existence-validation trust boundary (Phase 9 Finding B) means a caller error (an
  invalid UUID) fails silently rather than loudly for two of three `LockoutService` entry points —
  an intentional, documented trade-off given the established caller-trust precondition, not an
  oversight, but worth any future caller (T13/T14) being aware of.
- `recordSuccessfulAttempt`'s missing-row no-op (Phase 9 Finding C) means a `LOCKED` account whose
  `lockout_state` row is lost to external corruption cannot self-heal through this service —
  documented as an operator-facing data-integrity scenario in the code itself, not repaired.

---

## Verdict

**PASS** — `LockoutService` implements the task statement's three clauses exactly, all ten
acceptance criteria are met, no LOCKED decision is violated, module boundaries are clean (and this
phase corrected an inaccurate "novel precedent" claim from earlier phases rather than let it
stand), and 60/60 executable tests pass. The one open item — Testcontainers verification of the
concurrency fix and three edge-case integration tests — is a real, consistently-flagged
environment limitation (no Docker here), not a defect in the implementation or an untested code
path in principle: every one of those scenarios has an equivalent, passing, mocked-unit-test proof
in `LockoutServiceTest`.
