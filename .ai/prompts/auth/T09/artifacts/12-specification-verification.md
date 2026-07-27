# auth · T09 — Phase 12: Specification Verification

Verifying the final implementation (Phase 6/9) and tests (Phase 10/11) against
`spec/auth-service/requirements.md`, `design.md`, and `tasks.md` for T09 only.

---

## Traceability matrix

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R8** — reject a password shorter than 12 or longer than 128 chars, wherever a password is set/changed | Yes | `AccountService.java:87` (`register`, `passwordPolicy.validate(...)` before `existsByEmail`); `AccountService.java:206` (`resetPassword`, after eligibility check, before mutation); `AccountService.java:244` (`changePassword`, T08, unchanged); `PasswordPolicy.java:55-62` (`validateLength`, unchanged) | `shouldRejectPasswordShorterThan12OrLongerThan128` (`PasswordPolicyTest.java`, pre-existing); `registerCallsPasswordPolicyValidateWithTheConstructedAccountUuidAndSubmittedPassword`, `resetPasswordCallsPasswordPolicyValidateBeforeAnyMutation` (both `AccountServiceTest.java`, new) | No | No |
| **R9** — reject a password whose HIBP suffix count > 0 | Yes | Same three call sites as R8; `PasswordPolicy.java:64-71` (`validateNotBreached`, unchanged) | `shouldRejectBreachedPasswordUsingHibpRange` (`PasswordPolicyTest.java`, pre-existing); exercised at all three call sites via the same `validate` call as R8's tests | No | No |
| **R10** — fail-open + `password.breach_check_failed` audit when the HIBP API is unreachable | Yes | `PasswordPolicy.java:72-74,77-84` (`recordBreachCheckFailedAudit`, unchanged, internal to `validate` — caller-agnostic by construction) | `shouldAllowPasswordWhenBreachApiIsDownAndAuditFailure` (`PasswordPolicyTest.java`, pre-existing) | No | No |
| Register enumeration safety (L5) — policy check must run before the duplicate-email branch | Yes | `AccountService.java:86-91` (`Account.register(...)` constructed → `passwordPolicy.validate(...)` → `existsByEmail(...)`, in that order) | `registerRejectsPolicyViolatingPasswordEvenWhenEmailIsAlreadyRegistered` (asserts `existsByEmail` is never called for a policy-violating password, even when stubbed to indicate a genuine duplicate) | No | No |
| Register account-UUID correlation (frozen brief Finding 1) — the UUID passed to `validate` must be the real, eventually-persisted UUID, not a throwaway one | Yes | `AccountService.java:86-87` (`account.getAccountUuid()`, taken from the same `Account` instance later passed to `saveAndFlush`) | `registerCallsPasswordPolicyValidateWithTheConstructedAccountUuidAndSubmittedPassword` (asserts the captured UUID equals `response.accountUuid()`) | No | No |
| Register accepted trade-off (frozen brief Finding 1) — encoder is now touched even for a duplicate-email registration | Yes (accepted, not a defect) | `AccountService.java:86` (`Account.register(email, passwordEncoder.encode(...))` runs before the `existsByEmail` branch) | `registerRejectsKnownDuplicateAfterEncodingAndConstructingTheAccount` (renamed from `registerRejectsKnownDuplicateWithoutTouchingEncoder`; asserts encoder IS called, save/token/outbox are NOT) | No | No — human-approved at Phase 4 |
| `resetPassword` mutation ordering (frozen brief Finding 4) — `validate` must run before `unlock`/`changePasswordHash`/`revokeAllForPrincipal`/audit | Yes | `AccountService.java:200-209` (`isPasswordResetEligible` → `passwordPolicy.validate` → `unlock` (if `LOCKED`) → `changePasswordHash` → `revokeAllForPrincipal` → `recordAudit`, in that fixed order) | `resetPasswordCallsPasswordPolicyValidateBeforeAnyMutation` (`InOrder` proof across `passwordPolicy`, a spied `Account`, `passwordEncoder`, `refreshTokenTracker`, `auditService`) | No | No |
| `resetPassword` rejection has no side effects | Yes | Same ordering as above — a thrown `PasswordPolicyViolationException` at line 206 prevents every statement after it | `resetPasswordRejectsPolicyViolatingPasswordWithoutMutatingAccountOrRevokingSessions` | No | No |
| Reset-token-validity residual risk (frozen brief Finding 3, accepted) | Yes (documented, not fixed) | `AccountService.java:182-191` (Javadoc, corrected at Phase 9 per Kimi Finding 8 — the token is not durably consumed on rollback, the residual signal is the response type, not token consumption) | No dedicated test (deliberately — proving transactional rollback semantics would require Testcontainers infrastructure with zero precedent in this module, rejected at Phase 11 Gap 3 as out of scope) | No — accepted risk, not a requirement | No |
| DTO validation consistency (frozen brief Finding 5/6) — `RegisterAccountRequest` must not duplicate `PasswordPolicy`'s length enforcement | Yes | `RegisterAccountRequest.java:20-21` (`@Size` removed, `@NotBlank` only) | `passwordLengthIsNoLongerBeanValidated` (`RegisterAccountRequestValidationTest.java`, replaces `passwordBoundaries`) | No | No |
| **AC9** — new call sites use the existing `PasswordPolicyViolationException` → `400`/`VALIDATION_ERROR` mapping, no new problem type | Yes | `AccountExceptionHandler.java` unchanged (Files NOT to Modify, confirmed untouched) | `registerPropagatesPolicyViolationForTheExceptionHandlerToTranslate`, `passwordResetPropagatesPolicyViolationForTheExceptionHandlerToTranslate` (`AccountControllerTest.java`, new — propagation); `onPasswordPolicyViolationReturns400WithValidationErrorTypeAndDetail` (`AccountExceptionHandlerTest.java`, T08, unchanged — response shape) | No | No |
| **AC10** — `changePassword` behavior unchanged | Yes | `AccountService.java:236-249` (method body untouched by this task, confirmed by diff) | `shouldChangePasswordWithCorrectCurrentPasswordAndPolicyCompliantNewPassword`, `shouldRejectChangePasswordWhenNewPasswordViolatesPolicy` (T08, pre-existing, unmodified, still passing) | No | No |
| **L2** (password policy content) | Yes | `PasswordPolicy.java` fully unchanged — only newly wired into two more callers | Named tests below | No | No |

**Named tests** — both present, unchanged, passing:
- `shouldRejectPasswordShorterThan12OrLongerThan128` (`PasswordPolicyTest.java`)
- `shouldRejectBreachedPasswordUsingHibpRange` (`PasswordPolicyTest.java`)

**L5 note:** `register`'s enumeration-safety ordering is now explicitly locked and regression-tested
(see table above). No other L5-listed endpoint (login, password-reset request, email verification)
was touched by this task.

**Module boundaries (L-series, ArchUnit):** every changed file stays within `account`/`account.dto`;
`PasswordPolicy` was already an `account`-package collaborator since T08 — no new cross-module
dependency introduced.

---

## Principal-engineer assessment

**(1) Is the task fully complete?** Yes. `PasswordPolicy` is now applied to all three password-setting
call sites the task statement names — registration, change-password (already done at T08, confirmed
unchanged and still tested), and password-reset. Both named tests pass, both directly and through
their new callers.

**(2) Does it satisfy every acceptance criterion?** All ten (AC1-AC10) from the frozen brief are
implemented and tested, including the four that exist specifically because of Phase 4's human
decisions (register's ordering/UUID-correlation fix, the accepted encoder trade-off, resetPassword's
mutation ordering, DTO consistency) and the one Phase 9 correction (the resetPassword Javadoc's
token-consumption claim, fixed per Kimi's Phase 8 Finding 8).

**(3) Does it violate any LOCKED decision?** No. L2's policy content is entirely unchanged, only
newly wired into two more callers. L5 is upheld — the enumeration-safety ordering in `register` is
explicit, documented, and regression-tested, not merely assumed. No module-boundary violation.

**(4) Remaining risks:**
- Module-wide `mvn -pl services/auth verify` still cannot run end-to-end — the same pre-existing,
  unrelated compile break tracked since T03. Every test in this task was verified via isolated
  `javac` + JUnit Platform Launcher instead (63/63 passing, most recent run this phase).
- The HIBP breach-check network call now executes inside `@Transactional` `register` and
  `resetPassword`, in addition to `changePassword` (T08) — `register` specifically is a public,
  unauthenticated endpoint not covered by R41's rate-limited endpoint list. Logged as an accepted
  residual risk with a recommended follow-up at self-review (Phase 7) and independent review
  (Phase 8, Finding 7); out of scope for this task since `PasswordPolicy`'s transactional context
  is unchanged production code this task didn't author.
- The reset-token-validity residual signal (a policy-violating password on a valid, unused token
  returns a response distinguishable from an invalid-token rejection) remains, by explicit human
  decision at Phase 4, un-closed — correctly documented in code (post-Phase-9 correction) as not
  durably burning the token, which if anything weakens rather than strengthens the case for concern.
- `contracts/api/auth.yaml` does not yet exist in this repository (confirmed empty
  `contracts/api/` directory) — a pre-existing gap predating this task, not something T09 was
  scoped to create, and not blocking since no contract-conformance test exists for this endpoint
  family in any prior task either.

---

## Verdict

**PASS** — every requirement, LOCKED decision, human-approved design decision, and both named tests
in T09's scope are implemented and tested. No requirement was left partially satisfied; the two
genuine design tensions Phase 3 surfaced (register's UUID/enumeration-safety interaction, reset's
token-validity oracle) were each resolved by explicit human decision at Phase 4, and the one Phase 9
correction (Kimi's Finding 8) improved documentation accuracy without changing any tested behavior.
