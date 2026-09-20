# auth · T18 · Phase 3 — Design Challenge

Consumed `artifacts/02-task-implementation-brief.md` and the spec package (`requirements.md`, `design.md`, `tasks.md`, `agents.md`) plus the existing T16/T17 code it depends on.

No conflicts with the explicit LOCKED decisions (L6, L12) were found. The issues below are unstated assumptions, ambiguous rules, and untestable gaps that should be resolved before the brief is frozen in Phase 4.

---

1. **Issue · Begin-enroll retry-delete for an abandoned unconfirmed enrollment conflicts with AC1/R22.**
   **Severity:** High.
   **Evidence:** R22 says the flow applies to "an authenticated user without a confirmed TOTP enrollment." The Phase 1 extraction's AC1 says begin-enroll is allowed "only if no enrollment (confirmed or not) already exists" (T17's `UNIQUE(account_id, type)` resolution). The TIB instead proposes silently deleting an unconfirmed row and recreating it. This changes the meaning of "no confirmed enrollment" to "no confirmed enrollment, and any unconfirmed one is disposable," which is not stated in the spec and has device-replacement / UX side effects.
   **Recommended brief amendment:** Explicitly choose one behavior and lock it: (a) reject any existing enrollment (confirmed or unconfirmed), or (b) delete-and-retry only when the existing enrollment is unconfirmed, with a stated security/UX rationale. If (b), require the delete+insert to be in the same `@Transactional` method and add a test proving the old secret/URI is invalidated.

2. **Issue · TOTP clock-skew tolerance window is treated as undecided, but Phase 1 already narrowed it.**
   **Severity:** Medium.
   **Evidence:** The TIB flags the ±1 step (90 s) window as "not in L6" and asks for Phase 4 confirmation. However, `artifacts/01-specification-extraction.md` restates L6 as "compared against the submitted 6-digit code for the current and adjacent time steps." Leaving the window open in Phase 2 creates an acceptance-criterion gap (AC6) and risks renegotiating a decision that Phase 1 already made.
   **Recommended brief amendment:** Lock the window in the frozen brief as "current 30 s step plus one adjacent step in each direction" and remove the Phase 4 re-confirmation flag; if a different window is intended, explicitly override L6 with a LOCKED amendment.

3. **Issue · Recovery-code entropy and format are not locked, making AC7 untestable.**
   **Severity:** Medium.
   **Evidence:** R23 only says "generate 10 single-use recovery codes"; L6 only says "only SHA-256 hashes are stored." The TIB proposes 16 random bytes + URL-safe Base64 without padding, but flags both the byte count and encoding for Phase 4 confirmation. `VerificationTokenService` uses 32 bytes for a comparable single-use token, so the 16-byte choice is not obviously consistent with existing precedent.
   **Recommended brief amendment:** Lock the recovery-code format in the brief: entropy in bytes, encoding/alphabet, and resulting raw-code length. Also state whether codes are case-sensitive and whether any normalization happens before hashing, because the hash lookup (`findByAccountIdAndCodeHash`) depends on it.

4. **Issue · `verifyRecoveryCode` is included with no caller and no audit contract.**
   **Severity:** Medium.
   **Evidence:** The task statement says "recovery-code generation/verification," but the TIB's Out section says nothing calls `verifyRecoveryCode` until task 20. R29 says a failed recovery-code verification must record `mfa.failed` and deny authentication. If `verifyRecoveryCode` returns a boolean, it is unclear whether it records the audit itself or leaves that to task 20, which makes the method's contract untestable now.
   **Recommended brief amendment:** Either remove `verifyRecoveryCode` from T18 (make it task 20's responsibility) or define its full contract: input normalization, hash algorithm, atomic `markUsed` semantics, return type, and whether it records `mfa.failed` on failure.

5. **Issue · Account-status preconditions for begin/confirm/disable are unstated.**
   **Severity:** High.
   **Evidence:** R22/R23/R28 describe an "authenticated user" but never say the account must be `ACTIVE`. `MfaEnrollmentRepository.findAccountIdByUuid` resolves any non-deleted account, and `AccountService.findLoginView` only excludes `DELETED`. A `PENDING_VERIFICATION`, `LOCKED`, or `SUSPENDED` account could therefore begin, confirm, or disable MFA, which conflicts with the service-wide posture that security-sensitive self-service actions require an active account.
   **Recommended brief amendment:** State the required account status for each flow (e.g., `ACTIVE` for confirm and disable; `ACTIVE` or `PENDING_VERIFICATION` for begin-enroll if self-service verification is allowed before email confirmation) and the exception thrown when the check fails.

6. **Issue · Disable password re-verification path is underspecified and leaves edge cases unhandled.**
   **Severity:** Medium.
   **Evidence:** The TIB proposes `accountService.getByUuid(accountUuid)` -> `accountService.findLoginView(email)` -> `passwordEncoder.matches(...)`. This is two DB round trips and relies on the email not changing between calls. It also does not say what happens if `findLoginView` returns empty (e.g., the account was deleted between the two calls). More importantly, the path does not check account status before verifying the password, so a `LOCKED` or `SUSPENDED` account could still disable MFA if it supplies the right password and TOTP code.
   **Recommended brief amendment:** Document the chosen password-verification sequence explicitly, including the status check (see issue 5), the exception on `findLoginView` empty, and whether a single `AccountService` lookup-by-UUID method would be preferable (even if that means requesting an `AccountService` change in T18's scope).

7. **Issue · Wrong password on disable records no audit, which conflicts with `agents.md`.**
   **Severity:** Medium.
   **Evidence:** The TIB says no `mfa.failed` is recorded for a wrong password because R29's literal wording is about TOTP/recovery-code verification failure. However, `agents.md` states that "every security-relevant action is recorded" and lists "MFA events" and "password/key changes" as examples. A destructive MFA-disable attempt with a wrong password is security-relevant; omitting an audit row is a visible gap.
   **Recommended brief amendment:** Decide and document the audit behavior: either record `mfa.failed` for wrong-password disable attempts, or record a different event type (e.g., `mfa.disabled_failed`) with an explicit rationale for why it is excluded from `mfa.failed`.

8. **Issue · Audit-event shape for `mfa.failed` / `mfa.disabled` is unspecified.**
   **Severity:** Low.
   **Evidence:** The TIB says to call `AuditService.record(...)` but does not specify actor, target, outcome, or details. For self-service flows the actor and target are usually the same UUID, but the brief should be explicit so tests can assert the exact audit row. There is also no mention of whether the submitted code type (`totp` vs `recovery_code`) belongs in `details`.
   **Recommended brief amendment:** Add a short audit-event contract: `eventType` (`mfa.failed` / `mfa.disabled`), `outcome` (`FAILURE` / `SUCCESS`), `accountUuid` = target, `actorUuid` = caller's UUID for self-service, and `details` empty (to avoid leaking codes).

9. **Issue · No logging guard is stated for secrets in parameters or return records.**
   **Severity:** Medium.
   **Evidence:** `agents.md` says "Never log tokens, secrets, or emails." `MfaService.beginEnroll` returns the raw TOTP secret and the `otpauth://` URI (which contains the secret); `confirm` returns 10 raw recovery codes; `disable` receives the raw password and TOTP code. The TIB mentions a "new small record" for the return value but does not require it to mask secret material in `toString()`.
   **Recommended brief amendment:** Add a constraint that any record returned by `beginEnroll`/`confirm` overrides `toString()` to omit raw secrets/codes/URI, and that no logging or AOP intercepts `MfaService` method parameters or return values that contain secret material.

10. **Issue · Unknown account UUID handling is not specified for any flow.**
    **Severity:** Low.
    **Evidence:** `MfaEnrollmentRepository.findAccountIdByUuid` returns `Optional<Long>`. The TIB does not say what exception `beginEnroll`, `confirm`, or `disable` should throw when the UUID does not resolve. `MfaNotEnrolledException` is semantically wrong for begin-enroll; `MfaAlreadyEnrolledException` does not apply.
    **Recommended brief amendment:** Specify the exception for an unresolved account UUID (e.g., reuse `AccountService.getByUuid` so it throws `AccountNotFoundException`, or define a new `MfaAccountNotFoundException`) and apply it consistently across the three flows.

11. **Issue · Provisioning URI account label choice has PII implications.**
    **Severity:** Low.
    **Evidence:** `TotpGenerator.buildProvisioningUri` accepts an `accountLabel`. Using the account email would expose PII inside the authenticator app's label and in any URI logs. `agents.md` restricts PII in access tokens; the same posture should apply to data handed to third-party authenticator apps.
    **Recommended brief amendment:** State that `accountLabel` is the account UUID (or another non-PII identifier), never the email, and document the rationale in the brief.
