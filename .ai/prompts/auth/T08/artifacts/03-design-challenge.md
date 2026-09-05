# auth · T08 — Phase 3: Design Challenge

Adversarial review of the Phase 2 Task Implementation Brief for **T08 — Change own password**. Findings only; no redesign or implementation.

---

### 1. Password-policy enforcement is scoped out even though R11 and its named test require it

- **Issue:** The brief scopes `PasswordPolicy` out of T08 and treats the named test `shouldRejectPasswordShorterThan12OrLongerThan128` as deferred. That test is mapped to **R11** in `spec/auth-service/package.md` §8, and R11 says the new password must be "meeting policy."
- **Severity:** Blocker
- **Evidence:**
  - `requirements.md` R11: "WHEN an authenticated caller submits their current password and a **new password meeting policy** to `POST /accounts/me/password`, THEN the system SHALL update the password hash."
  - `package.md` line 88: `shouldRejectPasswordShorterThan12OrLongerThan128` → R11.
  - `tasks.md` lists task 9 as applying `PasswordPolicy` to registration, change-password, and password-reset.
  - `PasswordPolicy` already exists (foundation task 3), so T08 has the bean available.
- **Recommended brief amendment:**
  Resolve the open question by wiring `PasswordPolicy` into T08:
  - Add `PasswordPolicy` as a dependency of `AccountService`.
  - Call `passwordPolicy.validate(newPassword)` **before** `PasswordEncoder.encode(newPassword)` and `Account.changePasswordHash`.
  - Add `PasswordPolicyViolationException` (or its container `PasswordPolicy`) to the exception-handler contract.
  - Move `shouldRejectPasswordShorterThan12OrLongerThan128` from "not included" into Required Tests.
  - If the author explicitly wants policy enforcement deferred to task 9, add a clear statement that **T08 will not satisfy the full R11 acceptance criteria** and that the named R11 test is intentionally deferred.

---

### 2. Wrong-current-password rejection has no defined exception type or problem type

- **Issue:** The brief says the exception handler maps a wrong current password to "a `4xx` problem-detail response," but no exception class or `ProblemTypes` constant is created or named.
- **Severity:** High
- **Evidence:**
  - Files to Create lists only `ChangePasswordRequest.java`.
  - Files to Modify lists `AccountExceptionHandler.java` but no new exception.
  - `common/ProblemTypes.java` has no constant for a current-password mismatch.
  - Acceptance criteria AC2 only says "rejected"; it does not define the exception type or HTTP shape.
- **Recommended brief amendment:**
  - Add a new domain exception to Files to Create, e.g. `account/CurrentPasswordMismatchException.java`.
  - Add `common/ProblemTypes.java` to Files to Modify and define a stable problem-type URI (e.g. `https://checky.pro/problems/current-password-mismatch`).
  - Specify the mapping: status, type, title, and that `detail` must be `null`/constant so the response cannot vary by cause.
  - Add the corresponding assertion to `AccountExceptionHandlerTest`.

---

### 3. Wrong-current-password status is specified only as "`4xx`", which is untestable

- **Issue:** "`4xx`" is a range, not a contract. Without a single status code the controller/integration tests cannot assert the exact response.
- **Severity:** Medium
- **Evidence:**
  - Outputs section: "A `4xx` problem-detail body on a wrong current password."
  - No status is listed in the Acceptance Criteria.
- **Recommended brief amendment:**
  Pick a concrete status and document it. Because the caller is authenticated, recommend **`400 Bad Request`** with problem type `current-password-mismatch` and title "Current password is incorrect." Alternatively, if the author prefers `401 Unauthorized`, document that choice explicitly and add it as a locked output.

---

### 4. No account-status gate before verifying/encoding the password

- **Issue:** The brief does not say the change-password method must check the caller's account status. `Account.changePasswordHash` rejects only `DELETED`; `SUSPENDED` accounts would silently update their password, and calling `PasswordEncoder.matches` against a `null` `passwordHash` (a deleted account reached defensively) could throw.
- **Severity:** High
- **Evidence:**
  - `Account.java` line 109–114: `changePasswordHash` only checks `status == DELETED`.
  - Business Rules list only R11; no status eligibility rule is stated.
  - `resetPassword` already has an explicit `ACTIVE | LOCKED` eligibility gate, making the omission in change-password inconsistent.
- **Recommended brief amendment:**
  Add a status eligibility rule and test coverage:
  - Require `ACTIVE` before matching or mutating (recommendation), or explicitly allow `ACTIVE | LOCKED` if that is the intended contract.
  - Reject `PENDING_VERIFICATION`, `SUSPENDED`, and `DELETED` with a mapped domain exception.
  - Add a service test proving `changePasswordHash` and `encode` are never invoked when the account is ineligible.

---

### 5. Session/refresh-token revocation after password change is silently omitted

- **Issue:** R14 (password-reset) explicitly revokes all refresh-token families; R11 does not. The brief excludes revocation, but it never records the security trade-off, so a future reviewer may assume credential rotation invalidates existing sessions.
- **Severity:** Medium
- **Evidence:**
  - Scope/Out: "Refresh-token family revocation — R11's text does not name this ... out of scope."
  - `AccountService.resetPassword` calls `refreshTokenTracker.revokeAllForPrincipal(..., "PASSWORD_RESET")`; T08 would not.
- **Recommended brief amendment:**
  Either:
  - Add an explicit acceptance criterion stating **"Existing refresh-token families remain valid after a successful change-password"** and record the security rationale, or
  - If the author agrees that rotating one's own password should invalidate existing sessions, add revocation to scope and mirror `resetPassword`'s `revokeAllForPrincipal(..., "PASSWORD_CHANGED")` call.

---

### 6. Wrong-current-password attempts must not affect the brute-force lockout counter

- **Issue:** R16 increments the lockout counter on password login failures. An authenticated change-password call is not a login, but if a shared authentication helper is reused, a wrong current password could inadvertently trigger lockout tracking.
- **Severity:** Medium
- **Evidence:**
  - `requirements.md` R16 ties failed-attempt counting to password login attempts, not to the change-password endpoint.
  - The brief's Dependencies and Acceptance Criteria mention no `LockoutService` interaction.
- **Recommended brief amendment:**
  Add a constraint: "A wrong current password on `POST /accounts/me/password` returns the configured problem response and has no effect on `lockout_state` or failed-attempt counters." Add a unit test asserting `LockoutService` is never called.

---

### 7. `password.breach_check_failed` audit lacks actor/target context if policy enforcement is moved into T08

- **Issue:** This is conditional on the resolution of Finding 1. `PasswordPolicy.recordBreachCheckFailedAudit` currently records a `password.breach_check_failed` audit row with `accountUuid = null` and `actorUuid = null`. `agents.md` requires audit events to carry actor, target, outcome, and correlation id.
- **Severity:** Medium
- **Evidence:**
  - `PasswordPolicy.java` lines 68–75: the audit request passes `null` for both UUIDs.
  - `agents.md`: "every security-relevant action is recorded with actor, target, outcome, and correlation id."
- **Recommended brief amendment:**
  If T08 takes the policy path, add `UUID actorUuid/accountUuid` parameters to the policy validation call site so the caller's UUID is recorded as both actor and target. If policy remains in T09, add a note that this gap must be closed in the task-9 brief.

---

### 8. `ProblemTypes.java` is not listed as a file to modify

- **Issue:** Every new RFC 9457 problem type in this service is centralized in `common/ProblemTypes.java`. The brief adds a new mapping but omits the constant source file.
- **Severity:** Low
- **Evidence:**
  - `common/ProblemTypes.java` already defines `INVALID_TOKEN`, `NOT_FOUND`, etc.
  - Files to Modify does not include `common/ProblemTypes.java`.
- **Recommended brief amendment:**
  Add `common/ProblemTypes.java` to Files to Modify and specify the exact stable URI string for the new type.

---

### 9. New-password identical to current password is not addressed

- **Issue:** The brief does not state whether the system should allow, reject, or silently update the hash when the caller submits a new password equal to the current password.
- **Severity:** Low
- **Evidence:**
  - R11 and AC1 only require "correct current password + a new password."
  - NIST 800-63B (L2) forbids forced rotation, but does not speak to reusing the same password at the user's own request.
- **Recommended brief amendment:**
  Add a business rule: e.g. **"If the new password is identical to the current password, the request is still allowed; a new hash is generated and a `password.changed` audit event is recorded"** or **"If the new password matches the current password, reject with problem type `password-reuse-not-allowed`."** Either choice is acceptable, but it must be explicit and tested.
