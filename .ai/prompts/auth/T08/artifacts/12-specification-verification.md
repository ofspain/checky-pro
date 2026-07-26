# auth · T08 — Phase 12: Specification Verification

Verifying the final implementation (Phase 6/9) and tests (Phase 10/11) against
`spec/auth-service/requirements.md`, `design.md`, and `tasks.md` for T08 only.

---

## Traceability matrix

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R11** — authenticated caller submits current + new password; current password verified; new password validated against policy; hash updated | Yes | `AccountController.java:123-128` (`changePassword` endpoint, caller derived from `Authentication`); `AccountService.java:209-220` (`changePassword` — status gate → current-password check → policy check → mutation → audit, fixed order); `PasswordPolicy.java:45` (`validate`, first production caller) | `shouldChangePasswordWithCorrectCurrentPasswordAndPolicyCompliantNewPassword`, `shouldRejectChangePasswordWhenCurrentPasswordDoesNotMatch`, `shouldRejectChangePasswordWhenNewPasswordViolatesPolicy` (`AccountServiceTest.java`); `changePasswordReturnsNoContentOnSuccess` + 2 propagation tests (`AccountControllerTest.java`) | No | No |
| Account-status eligibility (human decision 2, Phase 4) | Yes | `AccountService.java:211-213` (`ACTIVE`-only gate, before the current-password check); `Account.java:116` (`changePasswordHash`'s guard widened from `DELETED`-only to `ACTIVE`-only, confirmed backward-compatible with T07's `resetPassword` at Phase 4/6) | `shouldRejectChangePasswordForEveryNonActiveAccountStatus` — all four non-`ACTIVE` statuses, plus asserts `passwordEncoder.matches` is never called (proves the NPE-avoidance ordering is real) | No | No |
| Session/refresh-token revocation (human decision 3, Phase 4 — explicitly none) | Yes (as decided) | `AccountService.changePassword` — no `refreshTokenTracker` call anywhere in the method body | `shouldNotRevokeRefreshTokenFamiliesOnSuccessfulPasswordChange` | No | No — deliberate, documented trade-off, not R14's behavior |
| Password reuse (human decision 4, Phase 4 — explicitly allowed) | Yes (as decided) | No comparison between `currentPassword`/`newPassword` anywhere in `changePassword` | `shouldAllowNewPasswordIdenticalToCurrentPassword` (with audit assertions per Phase 11 Gap 4) | No | No |
| Wrong-current-password rejection (Kimi Finding 2/3, Phase 4) | Yes | `AccountService.java:347` (nested `CurrentPasswordMismatchException`); `AccountExceptionHandler.java:49` (→ `400`/`CURRENT_PASSWORD_MISMATCH`, fixed title, no variable detail); `ProblemTypes.java:24` (new constant) | `onCurrentPasswordMismatchReturns400WithCurrentPasswordMismatchType`, `onCurrentPasswordMismatchResponseIsIdenticalRegardlessOfConstructionSite` (`AccountExceptionHandlerTest.java`) | No | No |
| Policy-violation rejection | Yes | `AccountExceptionHandler.java:57` (`PasswordPolicyViolationException` → `400`/`VALIDATION_ERROR`, `detail` = message, matching `InvalidAccountStateException`'s existing precedent) | `onPasswordPolicyViolationReturns400WithValidationErrorTypeAndDetail` | No | No |
| Breach-check audit actor/target (Kimi Finding 7, Phase 4) | Yes | `PasswordPolicy.java:45-47` (`validate` widened to accept `accountUuid`/`actorUuid`, `Objects.requireNonNull`-guarded per Kimi Finding 4); threaded into `recordBreachCheckFailedAudit`'s `RecordAuditEventRequest` | `shouldAllowPasswordWhenBreachApiIsDownAndAuditFailure` (assertion flipped from `isNull()` to real UUID equality); `shouldRejectNullAccountOrActorUuid` | No | No |
| **L2** (password policy content) | Yes | `PasswordPolicy`'s length/breach logic itself unchanged — only newly wired into a real call path | Named test below | No | No |
| **L3** (BCrypt delegating encoder) | Yes | `AccountService.changePassword` uses the same injected `PasswordEncoder` for both `matches` and `encode` | `shouldChangePasswordWithCorrectCurrentPasswordAndPolicyCompliantNewPassword` (`InOrder`-verified, Phase 11 Gap 2) | No | No |
| **Named test** `shouldRejectPasswordShorterThan12OrLongerThan128` | Yes | `PasswordPolicyTest.java` — unchanged assertion, updated to the new 3-arg signature; now exercises logic a real production call path (`AccountService.changePassword`) actually reaches for the first time | `PasswordPolicyTest.java` | No | No |

**L5 note (not a scoped LOCKED decision for this task, confirmed at Phase 0/1):** `POST /accounts/me/password` is not on L5's enumeration-safe endpoint list. Nothing in this task's implementation claims otherwise — `CurrentPasswordMismatchException` and `InvalidAccountStateException` are allowed to be distinguishable from each other and from success, which they are.

**L11 note:** no new public path was added — `PublicEndpoints.java` was correctly left untouched (this endpoint is authenticated), consistent with the frozen brief's Files NOT to Modify list.

---

## Principal-engineer assessment

**(1) Is the task fully complete?** Yes. `POST /accounts/me/password` exists, is authenticated,
verifies the current password, validates the new password against `PasswordPolicy`, updates the
hash, and audits the change — matching R11's full text, including the "meeting policy" clause that
Phase 3's design challenge caught was originally scoped out and then explicitly wired in by human
decision at Phase 4.

**(2) Does it satisfy every acceptance criterion?** All ten (AC1–AC10) from the frozen brief are
implemented and tested, including the four that only exist because of explicit human decisions at
the Phase 4 gate (status eligibility, no session revocation, password reuse allowed, breach-check
audit context).

**(3) Does it violate any LOCKED decision?** No. L2's policy content is unchanged, only newly
wired in. L3's encoder is reused exactly, for both operations. L5 doesn't scope this endpoint
(confirmed, not assumed). L11 is untouched (no new public path). L12 (module boundaries) is
upheld — every changed file stays within `account`/`common`, no new cross-module dependency.

**(4) Remaining risks:**
- Module-wide `mvn -pl services/auth verify` still cannot run end-to-end — the same pre-existing,
  unrelated compile break tracked since T03. Every test in this task was verified via isolated
  `javac` + JUnit Platform Launcher instead.
- `PasswordPolicy`'s HIBP network call now executes inside a live `@Transactional` method for the
  first time in production (self-review Finding 3, Kimi Finding 3 independently confirmed) — an
  accepted operational trade-off, not a defect, but will recur when task 9 wires the same call
  into `register`/`resetPassword`.
- `InvalidAccountStateException`'s detail exposes the caller's own account status
  (`PENDING_VERIFICATION`/`LOCKED`/`SUSPENDED`/`DELETED`) on rejection (Kimi Finding 5) — accepted
  as consistent with this exception's existing behavior everywhere else in the module, not a new
  risk this task introduces, and not enumeration-sensitive since the caller is already
  authenticated as this exact account.
- `Objects.requireNonNull`'s new guard in `PasswordPolicy.validate` means any future caller (task
  9) passing `null` actor/target UUIDs will fail fast with an `NullPointerException` rather than
  silently reintroducing the audit-context gap this task closed — a deliberate constraint on
  task 9's design, not a T08 defect.

---

## Verdict

**PASS** — every requirement, LOCKED decision, human-approved design decision, and the named test
in T08's scope is implemented and tested. No requirement was left partially satisfied (unlike T07,
which had one deliberately deferred clause) — Phase 4's human-approval gate resolved every open
question before implementation began, so nothing here required carrying a partial line item into
this verification.
