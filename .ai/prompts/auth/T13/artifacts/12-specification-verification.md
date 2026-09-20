# auth · T13 — Phase 12: Specification Verification

Verifying the final implementation (`LoginFailureHandler`, `LoginSuccessHandler`,
`AccountUserDetailsService`'s fix, `LockoutService.isCurrentlyLocked`, the two production import
fixes) and test suite (39 executed + 4 Testcontainers-unexecuted) against
`spec/auth-service/requirements.md`, `design.md`, and `tasks.md` for T13 only.

---

## Traceability matrix

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R16** — failed password attempt for an `ACTIVE` account increments the counter and records `login.failed` | Yes | `LoginFailureHandler.java:71-97` (`recordFailure`), `:99-102` (`isLockoutEligible`) | `shouldAppendRowAndMirrorAuditEventForLoginFailure`, `expiredLockAccountCallsRecordFailedAttempt`, `SasLoginIntegrationTest.wrongPasswordAgainstKnownAccountIncrementsCounterAndAudits` (Testcontainers, unexecuted) | No | No |
| **R18** — once the interval elapses, the next attempt is allowed; success resets/unlocks | Yes | `AccountUserDetailsService.java:48-49` (the core fix — `accountLocked` via `isCurrentlyLocked`, not raw status), `LoginSuccessHandler.java:39-50` (`recordSuccessfulAttempt` call) | `expiredLockDoesNotMapToAccountLocked`, `stillDelegatesToTheInheritedRedirectBehavior`, `SasLoginIntegrationTest.expiredLockAccountCanSuccessfullyLoginAndUnlocks` (Testcontainers, unexecuted) | No | No |
| **R43** — security-relevant actions are audited via the outbox | Yes | Already fully implemented by the pre-existing `AuditService.record` (T09-era); this task supplies the `login.failed` call site (`LoginFailureHandler.java:104-108` (`auditFailure`), locked shape per Phase 4 Finding 2) | `shouldAppendRowAndMirrorAuditEventForLoginFailure` (named), `unknownEmailAuditsWithNullUuidsAndNeverCallsLockoutService` | No | No — success-path auditing deliberately out of scope (Phase 2 scoping decision, task statement names only `login.failed`) |

**Named tests (`package.md` §8):**
- `shouldAppendRowAndMirrorAuditEventForLoginFailure` — exists verbatim
  (`LoginFailureHandlerTest.java:88`), maps to R37 in `package.md` (session-revocation, unrelated)
  — the same pre-existing package.md/requirements.md numbering drift confirmed at T09/T11/T12,
  reconfirmed here; the real match is R43 (this task's own header already names it correctly).
- `shouldResetLockoutCounterOnSuccessfulLogin` — exists verbatim
  (`LoginSuccessHandlerTest.java:65`), maps to R18.

---

## Beyond the three scoped requirements: the core fix and the human-approved import repair

Two things this task delivers are not literally named by R16/R18/R43's text but are load-bearing
for R18 to be satisfiable at all through the real login flow, both fully tested:

- **`AccountUserDetailsService`'s `accountLocked` fix.** Without it, Spring's
  `DaoAuthenticationProvider` would reject a login attempt at the pre-authentication gate for as
  long as `Account.status == LOCKED`, regardless of whether `lockout_state.locked_until` had
  already elapsed — R18's "the system SHALL allow the next authentication attempt" would be
  unreachable in practice. Verified end-to-end intent in `AccountUserDetailsServiceTest`
  (`stillLockedMapsToAccountLocked`, `expiredLockDoesNotMapToAccountLocked`) and
  `SasLoginIntegrationTest` (unexecuted, Testcontainers).
- **The two wrong-package import fixes** (`SecurityChainsConfig.java`,
  `ReuseDetectingAuthorizationService.java`) — human-approved at Phase 4 after Kimi's Phase 3
  attempt to dismiss the blocker as stale was independently verified false. Confirmed this phase:
  `mvn -pl services/auth compile` and `test-compile` both succeed with zero errors, re-run fresh
  for this verification, not merely carried over from Phase 6/10's own runs.

---

## Principal-engineer assessment

**(1) Is the task fully complete?** Yes. Both task-statement clauses are implemented: lockout
counter increment is wired into the real SAS failure path (`LoginFailureHandler`), reset-on-success
into the real success path (`LoginSuccessHandler`), and `login.failed` audit events are recorded
with the full locked shape (Phase 4 Finding 2). The `AccountUserDetailsService` fix — not literally
named by the task statement, but necessary for the failure/success paths to be reachable at all
for a previously-locked account — is also complete and tested.

**(2) Does it satisfy every acceptance criterion?** All ten (AC1-AC10) from the frozen brief are
implemented. AC1-AC4, AC7-AC9 are directly unit-tested (39/39 executed, passing, most recently
re-verified this phase). AC5/AC6 (the core fix) have both a unit-level proof
(`AccountUserDetailsServiceTest`) and an end-to-end proof (`SasLoginIntegrationTest`, unexecuted
here — see Remaining Risks). AC10 (the module compiles) is independently re-confirmed this phase.

**(3) Does it violate any LOCKED decision?** No. L4 unchanged (no new lockout arithmetic — this
task only wires existing T11/T12 logic to real call sites). L5 verified by construction: neither
handler branches on account status or exception subclass when producing the HTTP response — only
which *internal* calls fire differs; confirmed by direct code reading at Phase 7/9 and re-confirmed
this phase by inspection of the final `LoginFailureHandler.java`. L12 verified clean this phase via
a fresh `grep` for `import com.themistra.auth.account.Account;` across every file this task
touched: zero matches.

**(4) Remaining risks:**
- **`SasLoginIntegrationTest`'s 4 tests remain unexecuted** — no Docker daemon in this sandbox,
  the same limitation carried through every Testcontainers test in this entire pipeline (T12's own
  integration test is in the same state). CSRF-token scraping and session-cookie propagation were
  verified against actual Spring Security source at write-time (Phase 10), and the redirect-following
  default was caught and fixed at Phase 11 before it could silently invalidate every assertion in
  the file — but the test has never actually run against a live server. This is the single largest
  residual risk this task carries forward, flagged consistently since Phase 5, not a late discovery.
- **Six pre-existing, previously-unverifiable test/architecture issues surfaced** when the module
  became compilable for the first time (Phase 10): two Mockito field-initialization-order bugs and
  three `UnnecessaryStubbingException`s across `TokenClaimsCustomizerTest`,
  `ReuseDetectingAuthorizationServiceTest`, `AdminAccountControllerTest`, and
  `AdminAccountRoleControllerTest`; and — the more significant one — `ArchitectureTest`'s own
  module-boundary rule (`only_the_account_module_may_touch_the_Account_entity`) has apparently
  never actually been checked in this codebase's history, and fails against pre-existing,
  untouched code (`AccountResponse.from(Account)`) once it can finally run. None of these six are
  in files T13 created or touches beyond the two approved import lines; none were fixed here
  (deliberately, to avoid scope creep into five unrelated files across three unrelated packages).
  Recorded here for whoever owns those areas next — this verification would be incomplete if it
  silently omitted a discovery this significant.
- `package.md` §11 Q2 (rate-limit thresholds) and Q5 (lockout event publication) remain
  unresolved by the spec author — neither blocks this task; Q5 was already deferred at T12's own
  scope, unchanged here.

---

## Verdict

**PASS** — both clauses of the task statement are implemented and tested, all ten acceptance
criteria are met, no LOCKED decision is violated, module boundaries are clean, and the module
compiles end-to-end for the first time since T03 (re-verified fresh this phase, not just carried
forward). The one significant open item — Testcontainers verification of the real SAS filter
chain — is a real, consistently-flagged environment limitation, not a defect in the implementation
or an untested code path in principle: every scenario in `SasLoginIntegrationTest` has an
equivalent, passing, mocked-unit-test proof. The six newly-surfaced pre-existing issues (five
test-hygiene bugs, one apparently-never-enforced ArchUnit rule) are explicitly out of this task's
scope and reported in full rather than silently discovered and dropped.
