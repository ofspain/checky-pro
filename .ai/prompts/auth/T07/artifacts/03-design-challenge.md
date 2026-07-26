# auth · T07 · Phase 3 — Design Challenge Findings

**Consumed:** `artifacts/02-task-implementation-brief.md` (Phase 2 TIB for T07 password reset).  
**Reviewed against:** `spec/auth-service/agents.md`, `spec/auth-service/requirements.md`, `spec/auth-service/design.md`, `spec/auth-service/package.md`, `spec/auth-service/tasks.md`, and current `services/auth` implementation state.

This brief is largely sound, but it relies on `VerificationTokenService.consume` as a purpose-blind primitive and leaves several account-state, race-condition, and contract ambiguities unresolved. Below are the findings the author should fold into the brief before it is frozen.

---

## 1. Token purpose is not enforced — an email-verification token can reset a password

- **Severity:** Critical
- **Evidence:** `VerificationTokenService.consume(rawToken)` is purpose-generic by design (`VerificationTokenService.java` lines 105–133). It returns the account UUID for any token whose account is not `DELETED`/`SUSPENDED`, regardless of whether the token's purpose is `EMAIL_VERIFY` or `PASSWORD_RESET`. The TIB (Dependencies, State Changes) proposes calling `.consume(rawToken)` directly from the password-reset path with no purpose check.
- **Recommended brief amendment:** Add a purpose-aware consumption step. Either extend `VerificationTokenService` with a new `consumeForPurpose(rawToken, Purpose.PASSWORD_RESET)` method that checks `token.purpose == PASSWORD_RESET` before marking the token consumed, or have `AccountService` query the token record by hash, validate purpose without mutating it, and only then call `consume`. A post-consume purpose check is insufficient because it would consume a valid `EMAIL_VERIFY` token and then reject the password-reset request, violating the TIB's own promise that "`password-reset` failure ... no state change." State both the method/flow and a test case: `shouldRejectEmailVerifyTokenUsedForPasswordReset`.

---

## 2. `InvalidAccountStateException` from `changePasswordHash` can reveal account state

- **Severity:** Critical
- **Evidence:** `Account.changePasswordHash` throws `InvalidAccountStateException` when the account is `DELETED` (`Account.java` lines 109–114). That exception is mapped to `409 CONFLICT` with the exception's message in `AccountExceptionHandler.onInvalidState` (`AccountExceptionHandler.java` lines 25–32). The TIB states that every rejection reason must return the existing `400 INVALID_TOKEN` ProblemDetail (R15), but a concurrent `DELETED` transition (or a purpose mistake from Finding 1) would instead return `409` with detail that discloses state.
- **Recommended brief amendment:** Explicitly require the password-reset path to:
  1. Re-read the account by UUID inside the same transaction after `consume` returns.
  2. Verify the account status is `ACTIVE` or `LOCKED` before calling `changePasswordHash`.
  3. Convert any `InvalidAccountStateException` that still escapes (defensive) into `VerificationTokenRejectedException` so the controller/handler path stays on the single `400 INVALID_TOKEN` mapping. Add a test: `shouldReturnInvalidTokenForResetOnDeletedAccount`.

---

## 3. `AccountStatus.PENDING_VERIFICATION` is not rejected before password change

- **Severity:** High
- **Evidence:** `VerificationTokenService.isAccountUsable` only excludes `DELETED` and `SUSPENDED` (`VerificationTokenService.java` lines 141–144). A `PENDING_VERIFICATION` account can therefore pass `consume`. `Account.changePasswordHash` does not reject `PENDING_VERIFICATION` — it only rejects `DELETED` — so the password update would succeed. There is no business rule in the TIB that says a not-yet-verified account may reset its password, and this would be a functional/security inconsistency with R13, which explicitly restricts reset *requests* to `ACTIVE`/`LOCKED` accounts.
- **Recommended brief amendment:** State that the password-reset confirmation path must only accept accounts whose status is `ACTIVE` or `LOCKED`, and that any other status (including `PENDING_VERIFICATION` and `DELETED`/`SUSPENDED`) must produce the uniform `VerificationTokenRejectedException` → `400 INVALID_TOKEN` response. Add the corresponding acceptance-criterion test case.

---

## 4. Stale account read / account-state race between consume and password update

- **Severity:** High
- **Evidence:** The TIB describes the sequence "consume → password-update → revoke-all-families → audit" as one transaction, but it does not say whether the account is re-read after `consume` returns. If the service loads the account once before `consume`, a concurrent admin delete/suspend can land in between. Even with an `@Transactional` boundary, the read is stale relative to the committed state of a concurrent transaction. This is the same class of gap that motivated `VerificationTokenService.consume` re-checking account usability after `markConsumed`.
- **Recommended brief amendment:** Require a fresh read of `Account` from `accountRepository.findByAccountUuid(accountUuid)` (or a new `findByAccountUuidForUpdate`) after `consume` succeeds and before `changePasswordHash`, then validate status. Document that status mismatch at this point must be treated as a uniform rejection, not as `InvalidAccountStateException`.

---

## 5. Password-reset confirmation HTTP status is unspecified

- **Severity:** Medium
- **Evidence:** The TIB says success returns "`204 No Content` (mirrors `verify-email`)" for `password-reset`, but for `password-reset-request` it only says "always returns the same acknowledgement." Existing endpoints are split: `POST /accounts` returns `202`, while `POST /accounts/resend-verification` returns `200`. The password-reset-request contract (R12) and named tests do not specify a status, which will force an arbitrary decision at implementation time and may fail contract tests.
- **Recommended brief amendment:** Pick and lock the status for `password-reset-request`. Recommend `202 Accepted` because the endpoint performs a deferred side-effect (email emission), consistent with registration. Update the Acceptance Criteria and Required Tests sections accordingly.

---

## 6. DTO validation is unspecified

- **Severity:** Medium
- **Evidence:** `PasswordResetRequest` and `PasswordResetConfirmRequest` are listed as files to create, but the TIB does not state the validation annotations. For consistency with `ResendVerificationRequest` (`@NotBlank @Email`) and `VerifyEmailRequest` (`@NotBlank`), `PasswordResetRequest.email` should be `@NotBlank @Email`, `PasswordResetConfirmRequest.token` should be `@NotBlank`, and `PasswordResetConfirmRequest.newPassword` should at minimum be `@NotBlank`. Without this, blank inputs reach the service layer in ways the tests would not exercise, and the contract tests for malformed payloads cannot be written.
- **Recommended brief amendment:** Add a "DTO fields & validation" subsection under Inputs or Files to Create specifying the validation annotations. Note that length/policy validation remains out of scope per the deferred `PasswordPolicy` decision.

---

## 7. New password can leak via auto-generated `record toString()`

- **Severity:** Medium
- **Evidence:** The TIB says "the new plaintext password never appears in a log statement, response body, or exception message." But if `PasswordResetConfirmRequest` is a plain Java record with a `newPassword` field, its auto-generated `toString()` will include the plaintext password. Standard request logging, exception dumps, or audit serialization may therefore leak it unless explicitly guarded. `EmailRequestedEventPayload` already shows the pattern of overriding `toString()` to prevent credential leakage.
- **Recommended brief amendment:** Require `PasswordResetConfirmRequest` to override `toString()` to omit `newPassword`, or add a code-level rule/guard in the brief's Constraints section. Also note that neither DTO constructor nor getter should ever be passed to a log formatter.

---

## 8. Semantics of password reset on a `LOCKED` account are unclear

- **Severity:** Medium
- **Evidence:** R13 and the TIB explicitly allow reset *requests* for `LOCKED` accounts. `Account.changePasswordHash` does not reject `LOCKED`, so the confirmation would succeed. However, design.md/R18 says a `LOCKED` account becomes `ACTIVE` only after a *successful authentication attempt*, not after a password reset. The TIB is silent on whether reset should also unlock the account or clear the lockout counter, leaving an ambiguous state in which a user has reset the password but still cannot log in until lockout expires.
- **Recommended brief amendment:** State explicitly whether password reset on a `LOCKED` account:
  - Leaves the account `LOCKED` (lockout still enforced; user must wait for expiry or admin unlock), or
  - Unlocks the account and clears the failed-attempt counter.
  Either choice is defensible, but the brief must pick one and add the matching acceptance criterion and test.

---

## 9. Family revocation does not purge live SAS authorizations

- **Severity:** Medium
- **Evidence:** The TIB says "revoke *every* refresh-token family for that account." Tasks 28/29 (R37/R38/R39) show that the service treats family revocation and live-authorization removal as separate concerns: `DELETE /accounts/me/sessions` revokes the family *and* removes the live SAS authorization via `OAuth2AuthorizationService`. The TIB does not mention clearing SAS authorizations after a password reset. This leaves existing access-token-granting authorizations alive for up to the access-token TTL after a reset, which is a real session-takeover residual risk.
- **Recommended brief amendment:** Clarify whether live SAS authorization removal is in scope for T07 or intentionally deferred to T28/T29. If deferred, document the residual risk explicitly (e.g., "reset revokes refresh-token families; live SAS authorizations remain valid until their access tokens expire"). If in scope, add `OAuth2AuthorizationService` to the dependency list and update the state-change description.

---

## 10. No mention of normalized-email handling in the request DTO

- **Severity:** Low
- **Evidence:** `AccountService` normalizes email via `normalize(email)` before repository lookup, consistent with `RegisterAccountRequest`. The TIB says `password-reset-request` takes an email string but does not say whether normalization happens in the service or the DTO.
- **Recommended brief amendment:** Add a one-line rule: normalize with `trim().toLowerCase(Locale.ROOT)` in `AccountService`, as already done for registration/resend-verification, so the test `shouldEmitPasswordResetEventOnlyWhenEmailExists` uses the same matching semantics as `findLoginView`.

---

## 11. `package.md` maps the named test to the wrong requirement

- **Severity:** Low
- **Evidence:** `spec/auth-service/package.md` line 86 lists `shouldEmitPasswordResetEventOnlyWhenEmailExists` → R8, but R8 is the password-length policy requirement. The correct mapped requirements are R12/R13/R14/R15. This is a spec-level typo, not a brief issue, but implementers will skip it if the brief picks up the named test verbatim.
- **Recommended brief amendment:** Add a note under Required Tests that the test name from `package.md` should map to R12/R13, and that the implementation's own test will verify both existence and correct-status gating. Do not modify `spec/`.

---

## 12. Cross-module dependency on `token.RefreshTokenTracker` lacks brief-level verification plan

- **Severity:** Low
- **Evidence:** The TIB correctly notes that this is `account`'s first dependency on `token` (`RefreshTokenTracker`). `agents.md` L12 forbids cross-module entity imports, not service/component imports, but `ArchitectureTest` is CI-enforced and the brief says the change is "confirmed at Phase 0 as unblocked."
- **Recommended brief amendment:** Add a verification step to Acceptance Criteria: `ArchitectureTest` and any module-boundary ArchUnit tests must still pass after `AccountService` imports `RefreshTokenTracker`. This prevents a surprise CI failure in Phase 7/9.

---

## 13. `PASSWORD_RESET` revocation reason string and audit mirror payload

- **Severity:** Low
- **Evidence:** The TIB proposes revocation reason `"PASSWORD_RESET"`. Existing reasons are `"REUSE_DETECTED"`. The brief does not say what audit payload to mirror for `password.reset` beyond the event type.
- **Recommended brief amendment:** Confirm `revoked_reason` `VARCHAR(64)` is sufficient (it is), and specify that `password.reset` is recorded with `actorUuid = targetAccountUuid` and `outcome = SUCCESS`, mirroring the T06 self-service pattern. No detail field needs to include the revoked family count.

---

## Summary of amendments needed before freezing

| # | Finding | Amend now? |
|---|---------|------------|
| 1 | Enforce `PASSWORD_RESET` token purpose | Yes — critical |
| 2 | Catch/convert `InvalidAccountStateException` and re-read account before change | Yes — critical |
| 3 | Reject `PENDING_VERIFICATION` before password reset | Yes — high |
| 4 | Re-read account inside transaction to avoid stale-state race | Yes — high |
| 5 | Lock `password-reset-request` HTTP status | Yes — medium |
| 6 | Specify DTO validation annotations | Yes — medium |
| 7 | Guard `PasswordResetConfirmRequest.toString()` against password leak | Yes — medium |
| 8 | Clarify `LOCKED` account reset/unlock semantics | Yes — medium |
| 9 | Clarify SAS authorization purge scope | Yes — medium |
| 10 | Confirm email normalization | Nice to have |
| 11 | Correct named-test requirement mapping in brief/tests | Nice to have |
| 12 | Add ArchUnit verification step | Nice to have |
| 13 | Confirm audit payload shape | Nice to have |

No conflicts with `agents.md` or LOCKED decisions were found; however, Findings 2 and 3 describe scenarios where a naive implementation would accidentally violate L5 (enumeration-safe responses) and must be guarded against.
