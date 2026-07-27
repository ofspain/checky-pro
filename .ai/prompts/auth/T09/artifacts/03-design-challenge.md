# auth · T09 — Phase 3: Design Challenge Findings

Adversarial review of `artifacts/02-task-implementation-brief.md`. Each finding is structured as: **Issue · Severity · Evidence · Recommended brief amendment.**

---

### 1. Register UUID availability claim contradicts the "prevents save" acceptance criterion

**Severity:** High

**Issue:** The brief states that for `register` the account UUID is "available at the point validation would run (post-persist for register)" and simultaneously asserts that a policy violation must "prevent `accountRepository.save/saveAndFlush`". These two statements are mutually exclusive: if validation is genuinely post-persist, the save has already occurred by the time `PasswordPolicy.validate` can throw.

**Evidence:**
- `02-task-implementation-brief.md` §Inputs: "post-persist for register".
- `02-task-implementation-brief.md` §Required Tests: "a violation prevents `accountRepository.save`/`saveAndFlush`".
- `Account.java` lines 57-65: `Account.register(...)` assigns `UUID.randomUUID()` at construction time, before any persistence. The UUID is therefore available pre-persist.
- `AccountService.java` lines 79-87: current flow is `existsByEmail` → `encode` → `saveAndFlush` → `issueAndEmitVerificationEmail`.

**Recommended brief amendment:**
- Correct §Inputs to state the UUID is available at construction (`Account.register`), not post-persist.
- Mandate the exact ordering in `AccountService.register`: normalize email → assign/obtain account UUID → `passwordPolicy.validate(...)` → `passwordEncoder.encode(...)` → `saveAndFlush(...)` → emit.
- List `Account.java` under **Files to Modify** if `Account.register` must be changed to accept a nullable initial password hash or an explicit UUID so the service can validate before encoding.

---

### 2. Register check ordering determines enumeration safety; the brief assumes but does not lock it

**Severity:** High

**Issue:** The brief's §Security claims that adding password-policy validation has "no enumeration-safety impact" because a policy violation is a "non-existence-revealing rejection". That claim only holds if `passwordPolicy.validate` runs **before** the duplicate-email check. If the duplicate-email check runs first, an existing email with a non-compliant password returns the uniform `202 Accepted`, while a new email with the same non-compliant password returns `400 Bad Request`, allowing an attacker to infer whether the email is registered.

**Evidence:**
- `02-task-implementation-brief.md` §Security: "no enumeration-safety impact … a policy violation is a distinct, non-existence-revealing rejection".
- `agents.md` / service-specific rules: "Enumeration-safe everywhere: registration, verification, password-reset request/confirm, and login return uniform responses that never reveal whether an email exists …".
- `design.md` L5: "Enumeration-safe responses. Login, registration, password-reset request, password-reset confirmation, and email verification endpoints return uniform responses that do not reveal whether an email exists …".
- `AccountController.java` lines 46-55: `DuplicateEmailException` is caught and converted to the same `202` response as success, so branch order in the service becomes the only enumeration safeguard.

**Recommended brief amendment:**
- Explicitly lock the ordering in `register`: `passwordPolicy.validate` must run before `accountRepository.existsByEmail` (or at least before any account-existence branch).
- State that this ordering is required by L5 and is not merely a performance preference, so Phase 5 cannot legitimately place the duplicate-email check before policy validation.
- Add a dedicated AccountServiceTest asserting that a non-compliant password for both a new and an existing email throws `PasswordPolicyViolationException` before the existence/duplicate path is reached.

---

### 3. `resetPassword` password validation after token consumption creates a token-validity oracle

**Severity:** High

**Issue:** If `passwordPolicy.validate` runs after `verificationTokenService.consumeForPurpose`, a caller with a candidate reset token and an intentionally non-compliant password receives `VALIDATION_ERROR` when the token is valid but `INVALID_TOKEN` when the token is invalid, expired, already used, or belongs to the wrong purpose. This lets an attacker test whether a token is still redeemable without actually changing the password. This contradicts L5/R15 uniform-rejection requirements and is an enumeration-safety regression introduced specifically by adding password-policy enforcement.

**Evidence:**
- `02-task-implementation-brief.md` §Constraints: "neither the existing … token/eligibility check in `resetPassword` has a requirement-mandated order relative to the new policy check".
- `AccountService.java` lines 170-187: current flow is `consumeForPurpose` → `findByAccountUuid` → eligibility → `changePasswordHash`.
- `AccountExceptionHandler.java` lines 39-45 and 56-63: `VerificationTokenRejectedException` maps to `INVALID_TOKEN`; `PasswordPolicyViolationException` maps to `VALIDATION_ERROR`.
- `agents.md`: "Enumeration-safe everywhere … password-reset request/confirm … never reveal whether an email exists … or whether a token is invalid".
- `requirements.md` R15: "IF a password-reset token is invalid, expired, already used, or belongs to a deleted/suspended account, THEN the system SHALL return a uniform failure response indistinguishable from a valid token".

**Recommended brief amendment:**
- Mandate that `passwordPolicy.validate` runs **before** any token-consuming operation in `resetPassword`.
- Because `PasswordPolicy.validate` requires an `accountUuid` for audit, add to scope either:
  - a non-consuming token-to-account lookup (e.g., reuse or extend `VerificationTokenService.verify`) to obtain the account UUID before validation, or
  - an explicit, author-approved exception to L5 acknowledging that reset tokens unavoidably disclose validity once a compliant password is supplied, but that bad-password validity-oracles must still be closed.
- Add controller/service tests proving that a bad password yields the same problem type regardless of token validity.

---

### 4. `resetPassword` can mutate lockout state before a password is validated

**Severity:** Medium

**Issue:** The current `resetPassword` flow unlocks a `LOCKED` account before changing the password. If password validation is inserted after the unlock but before the hash update, a valid token + non-compliant password will temporarily unlock the account and then roll back on validation failure. The transaction rollback keeps the database consistent, but it is wasteful and increases the chance of subtle ordering bugs in tests.

**Evidence:**
- `AccountService.java` lines 181-184: `account.unlock()` precedes `account.changePasswordHash(...)`.
- `02-task-implementation-brief.md` §Constraints: references `changePassword`'s precedent of running cheaper/local checks before the network-calling policy check.

**Recommended brief amendment:**
- Lock the `resetPassword` ordering as: `consume`/`lookup` token → eligibility check → `passwordPolicy.validate` → `unlock` (if `LOCKED`) → `encode`/`changePasswordHash` → revoke families → audit.
- Add an AccountServiceTest `InOrder` assertion showing that `passwordPolicy.validate` is invoked before any call to `Account.unlock`, `passwordEncoder.encode`, `refreshTokenTracker.revokeAllForPrincipal`, or `auditService.record`.

---

### 5. `RegisterAccountRequest` `@Size` duplicates length enforcement and breaks end-to-end `PasswordPolicyViolationException` coverage

**Severity:** Medium

**Issue:** `RegisterAccountRequest` already carries `@Size(min = 12, max = 128)` on `password`. With `@Valid` in the controller, length violations are caught by Spring's `MethodArgumentNotValidException` before `AccountService.register` is reached. AC1 claims rejection is "via `PasswordPolicy.validate`", but for the HTTP endpoint the actual enforcement path for length is bean validation, not the policy service. The resulting problem body also differs (a `violations` array from `ApiExceptionHandler` versus `title`/`detail` from `AccountExceptionHandler`), even though both map to `VALIDATION_ERROR`.

**Evidence:**
- `RegisterAccountRequest.java` lines 18-20: `@NotBlank @Size(min = 12, max = 128) String password`.
- `AccountController.java` line 48: `@Valid @RequestBody RegisterAccountRequest request`.
- `ApiExceptionHandler.java` lines 32-38: `MethodArgumentNotValidException` produces a problem body with a `violations` property.
- `AccountExceptionHandler.java` lines 56-63: `PasswordPolicyViolationException` produces `title` and `detail`.
- `02-task-implementation-brief.md` AC1: "`register` rejects a password outside 12-128 chars via `PasswordPolicy.validate`".
- `02-task-implementation-brief.md` §Files NOT to Modify: `RegisterAccountRequest.java`.

**Recommended brief amendment:**
- Either remove `@Size` (and `@NotBlank`, if `PasswordPolicy` already rejects blank) from `RegisterAccountRequest` so `PasswordPolicy` is the single enforcement point, and add `RegisterAccountRequest.java` to **Files to Modify**; or
- Keep the DTO annotation and revise AC1 to say "service-level rejection via `PasswordPolicy.validate`" while documenting that end-to-end length violations are intercepted by bean validation. Update the new controller test to mock `PasswordPolicyViolationException` rather than relying on a real length-violating payload.

---

### 6. DTO validation is inconsistent between registration and password-reset confirm

**Severity:** Medium

**Issue:** `RegisterAccountRequest` enforces length at the DTO layer, while `PasswordResetConfirmRequest` only enforces `@NotBlank`. Once `PasswordPolicy` is wired into `resetPassword`, length violations there will genuinely flow through `PasswordPolicy.validate`, but the same length violation on registration will be caught earlier by bean validation. The brief's goal of "uniform enforcement everywhere a password is set" is not uniform at the HTTP contract level.

**Evidence:**
- `RegisterAccountRequest.java`: has `@Size(min = 12, max = 128)`.
- `PasswordResetConfirmRequest.java` lines 12-18: only `@NotBlank` on `newPassword`.
- `02-task-implementation-brief.md` Purpose: "password-content policy is enforced uniformly everywhere a password is set".
- `02-task-implementation-brief.md` §Files NOT to Modify: both DTOs.

**Recommended brief amendment:**
- Pick a single strategy and apply it to both endpoints. Preferred option: remove `@Size` from `RegisterAccountRequest` (and add it nowhere else), making `PasswordPolicy.validate` the sole enforcement point for both endpoints. If the DTO annotations are retained for defense-in-depth, document that they are a redundant second gate and add matching `@Size` to `PasswordResetConfirmRequest`.

---

### 7. `package.md` §8 maps the password-policy named tests to wrong requirement IDs

**Severity:** Low

**Issue:** The phase prompt scopes requirements `R8`, `R9`, `R10` from `requirements.md` (length, HIBP, fail-open), but `package.md` §8 maps the corresponding named tests to `R11`, `R12`, and `R13`. This breaks the traceability checklist that requires every acceptance criterion to map to a named test.

**Evidence:**
- `requirements.md` lines 17-19: R8 length, R9 HIBP, R10 fail-open.
- `package.md` §8 lines 88-90: maps `shouldRejectPasswordShorterThan12OrLongerThan128` → R11, `shouldRejectBreachedPasswordUsingHibpRange` → R12, `shouldAllowPasswordWhenBreachApiIsDownAndAuditFailure` → R13.
- `package.md` §9 line 119: "All §3 acceptance criteria have a passing named test from §8."

**Recommended brief amendment:**
- Add an Open Question noting that `package.md` §8 requirement IDs for the password-policy tests appear misaligned with `requirements.md` and need author correction before the brief is frozen in Phase 4.
