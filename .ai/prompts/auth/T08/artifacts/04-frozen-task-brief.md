> STATUS: FROZEN

# auth · T08 — Phase 4: Frozen Task Brief

Human Approval gate. All four judgment calls Kimi's Phase 3 review surfaced were put to the human
directly (not assumed); answers below are final for this task. Downstream phases may not
renegotiate this brief.

## Human decisions (this gate)

1. **`PasswordPolicy` wiring** — **Wire it into T08 now.** Matches R11's literal text ("a new
   password meeting policy") and its own named test's mapping to R11 in `package.md` §8. This also
   authorizes closing Finding 7 (breach-check audit's null actor/target) as part of the same
   change, since T08 is the first real production caller.
2. **Account-status eligibility** — **`ACTIVE` only.** `PENDING_VERIFICATION`, `LOCKED`,
   `SUSPENDED`, and `DELETED` are all uniformly rejected.
3. **Refresh-token family revocation on success** — **None.** Existing sessions remain valid;
   change-password is not treated as a compromise-recovery flow the way password-reset is.
4. **New password identical to current password** — **Allowed.** No special-casing; re-hashed and
   audited like any other valid new password.

## Phase 3 findings — disposition

| # | Finding | Severity | Disposition |
|---|---|---|---|
| 1 | Policy enforcement scoped out despite R11/named test | Blocker | **Accepted** — human decision 1 above. |
| 2 | No exception type/problem type defined for wrong current password | High | **Accepted.** New nested `AccountService.CurrentPasswordMismatchException`; new `ProblemTypes.CURRENT_PASSWORD_MISMATCH`. |
| 3 | Status code for wrong current password unspecified ("4xx") | Medium | **Accepted**, Kimi's own recommendation: **`400 Bad Request`**. |
| 4 | No account-status gate before verifying/mutating | High | **Accepted** — human decision 2 above; see State Changes for exact placement (before the current-password check, to avoid a `null`-`passwordHash` NPE risk on a `DELETED` account). |
| 5 | Session revocation trade-off not recorded | Medium | **Accepted the "record the trade-off" branch** — human decision 3 above; AC8 below makes it an explicit, tested acceptance criterion rather than a silent omission. |
| 6 | Wrong current password must not affect lockout counter | Medium | **Accepted as a constraint**, currently a no-op: `LockoutService` does not exist anywhere in this codebase yet (tasks 11–13, not yet built). Noted for whoever builds it. |
| 7 | `password.breach_check_failed` audit has null actor/target | Medium | **Accepted**, folded into decision 1: `PasswordPolicy.validate` gains `accountUuid`/`actorUuid` parameters, threaded into the audit call. |
| 8 | `ProblemTypes.java` missing from Files to Modify | Low | **Accepted**, trivial — folded into the file list below. |
| 9 | Password-reuse (new == current) unaddressed | Low | **Accepted the "allow" branch** — human decision 4 above; AC9 makes it explicit. |

No finding was rejected.

## Scope

**In:** `POST /accounts/me/password`, authenticated; current-password verification;
`PasswordPolicy` enforcement on the new password (now in scope, see decision 1); `ACTIVE`-only
account-status gate; a `password.changed` audit event with real actor/target context;
`PasswordPolicy`'s breach-check-failure audit gains real actor/target context.

**Out:** Refresh-token family revocation (decision 3); rate limiting (task 31); contract file
authoring (tasks 33/34); anything to do with lockout counters, since `LockoutService` doesn't exist
yet (tasks 11–13).

## Business Rules

- **R11.** WHEN an authenticated caller submits their current password and a new password meeting
  policy to `POST /accounts/me/password`, THEN the system SHALL verify the current password,
  validate the new password against `PasswordPolicy`, and, if both pass, update the password hash.
- Only `ACTIVE` accounts may use this endpoint (decision 2).
- A new password identical to the current password is accepted (decision 4).

## Locked Decisions

- **L2.** `PasswordPolicy`'s content (12–128 length, HIBP fail-open with audit) is enforced here
  for the first time in production — the policy itself is unchanged, only newly wired in.
- **L3.** New password encoded, current password verified, via the same delegating
  `PasswordEncoder` (`{bcrypt}`, strength 12).

## Dependencies

- `PasswordEncoder` (`matches`, `encode`)
- `PasswordPolicy.validate` (widened signature, see Files to Modify)
- `AccountRepository.findByAccountUuid` (via the existing `getAccount` private helper)
- `Account.changePasswordHash` (widened guard, see Files to Modify)
- `AuditService.record`
- `Clock`, `Authentication`

## Inputs

- `Authentication` — JWT `sub` → account UUID.
- `ChangePasswordRequest { currentPassword: String, newPassword: String }`, both `@NotBlank`.

## Outputs

- `204 No Content` on success.
- `400`/`CURRENT_PASSWORD_MISMATCH` — wrong current password.
- `400`/`VALIDATION_ERROR` — new password fails `PasswordPolicy` (`detail` = the policy violation
  message, consistent with `InvalidAccountStateException`'s existing precedent of exposing
  `e.getMessage()`).
- `409`/`INVALID_STATE` — account is not `ACTIVE` (reuses the existing mapping).

## State Changes

Exact order, service-layer method (name: `changePassword`):

1. `account = getAccount(accountUuid)` — existing private helper (`AccountNotFoundException` if
   missing; safe to leak here since the caller is authenticated as this exact account, no
   enumeration concern).
2. If `account.getStatus() != ACTIVE` → `InvalidAccountStateException`. **Checked before the
   current-password comparison** specifically to avoid calling `passwordEncoder.matches` against a
   `DELETED` account's `null` `passwordHash` (Finding 4's NPE risk).
3. If `!passwordEncoder.matches(currentPassword, account.getPasswordHash())` →
   `CurrentPasswordMismatchException`.
4. `passwordPolicy.validate(newPassword, accountUuid, accountUuid)` — throws
   `PasswordPolicyViolationException` on failure. Runs after the current-password check (no point
   validating/breach-checking a new password if the caller hasn't proven they may change it at all
   — also avoids a wasted HIBP network call on a request that's going to be rejected anyway).
5. `account.changePasswordHash(passwordEncoder.encode(newPassword))`.
6. `recordAudit("password.changed", accountUuid, accountUuid)`.

No outbox event. No refresh-token revocation (decision 3).

## Files to Create

- `account/dto/ChangePasswordRequest.java`

## Files to Modify

- `account/AccountController.java` — new endpoint.
- `account/AccountService.java` — new `changePassword` method; new `PasswordPolicy` constructor
  dependency; new nested `CurrentPasswordMismatchException`.
- `account/AccountExceptionHandler.java` — two new mappings
  (`CurrentPasswordMismatchException` → `400`/`CURRENT_PASSWORD_MISMATCH`;
  `PasswordPolicy.PasswordPolicyViolationException` → `400`/`VALIDATION_ERROR`).
- `account/Account.java` — widen `changePasswordHash`'s guard from `status == DELETED` to
  `status != ACTIVE`. **Confirmed backward-compatible with T07's `resetPassword`**: that method
  always reaches `changePasswordHash` with `status == ACTIVE` already (it calls `account.unlock()`
  first when `LOCKED`, and its own `isPasswordResetEligible` pre-check already excludes
  `PENDING_VERIFICATION`/`SUSPENDED`/`DELETED` before ever reaching this call).
- `account/PasswordPolicy.java` — widen `validate(String)` to
  `validate(String rawPassword, UUID accountUuid, UUID actorUuid)`, threaded into
  `recordBreachCheckFailedAudit`'s `RecordAuditEventRequest`. Zero existing production callers
  today, so this is a pure signature widening with no other call-site impact.
- `common/ProblemTypes.java` — new `CURRENT_PASSWORD_MISMATCH` constant.

## Files NOT to Modify

- `common/PublicEndpoints.java` — endpoint is authenticated, not public.
- `events/EventTopics.java` — no new event.
- `token/RefreshTokenTracker.java` — no revocation (decision 3).
- `account/PasswordPolicyProperties.java` — config shape unchanged.
- `authn/BreachCheckClient.java` — unchanged.

## Acceptance Criteria

- **AC1 (R11).** Correct current password + policy-compliant new password → hash updated;
  `PasswordPolicy.validate` called before `encode`; `encode` called with the raw new password.
- **AC2 (R11/Finding 2/3).** Wrong current password → `CurrentPasswordMismatchException` →
  `400`/`CURRENT_PASSWORD_MISMATCH`; `encode`/`changePasswordHash` never called; no audit.
- **AC3 (R11/L2).** New password failing policy → `PasswordPolicyViolationException` →
  `400`/`VALIDATION_ERROR`; `changePasswordHash` never called; no audit. Only reached if the
  current-password check already passed.
- **AC4 (L3).** Both the match-check and the new hash go through the same `PasswordEncoder` bean.
- **AC5.** Caller identity comes only from `Authentication.getName()`.
- **AC6.** Exactly one `password.changed` audit row on success (`accountUuid` = `actorUuid` = the
  caller's own UUID); none on any rejection path.
- **AC7 (Finding 4, decision 2).** A non-`ACTIVE` account (all four other statuses) →
  `InvalidAccountStateException` → `409`/`INVALID_STATE`; `passwordEncoder.matches` is never
  called (proves the pre-check genuinely runs first, not just that the end state matches).
- **AC8 (Finding 5, decision 3).** After a successful change, `refreshTokenTracker` is never
  called — existing refresh-token families remain valid. This is a deliberate, tested trade-off,
  not a silent omission.
- **AC9 (Finding 9, decision 4).** A new password identical to the current password succeeds
  exactly like any other valid new password — no special-case rejection.
- **AC10 (Finding 7).** When `PasswordPolicy`'s breach-check-failure path is triggered from this
  call path, the recorded `password.breach_check_failed` audit event carries the real
  `accountUuid`/`actorUuid`, not `null`.

## Required Tests

**`AccountServiceTest`** (new):
- `shouldChangePasswordWithCorrectCurrentPasswordAndPolicyCompliantNewPassword` (AC1/AC4/AC5/AC6)
- `shouldRejectChangePasswordWhenCurrentPasswordDoesNotMatch` (AC2)
- `shouldRejectChangePasswordWhenNewPasswordViolatesPolicy` (AC3) — mocks `PasswordPolicy`,
  verifies it's called with `(newPassword, accountUuid, accountUuid)` and that a thrown
  `PasswordPolicyViolationException` propagates with no mutation/audit. Does **not** re-verify
  `PasswordPolicy`'s own boundary logic — that stays `PasswordPolicyTest`'s job (see below); this
  test only proves `AccountService`'s integration/call-order/error-propagation.
- `shouldRejectChangePasswordForEveryNonActiveAccountStatus` (AC7) — loop over
  `PENDING_VERIFICATION`/`LOCKED`/`SUSPENDED`/`DELETED`, mirrors T07's
  `shouldRejectPasswordResetForIneligibleAccountStatuses` pattern; asserts
  `passwordEncoder.matches` is never called.
- `shouldNotRevokeRefreshTokenFamiliesOnSuccessfulPasswordChange` (AC8)
- `shouldAllowNewPasswordIdenticalToCurrentPassword` (AC9)

**`AccountControllerTest`** (new):
- `changePasswordReturnsNoContentOnSuccess`
- `changePasswordPropagatesCurrentPasswordMismatchForTheExceptionHandlerToTranslate`
- `changePasswordPropagatesPolicyViolationForTheExceptionHandlerToTranslate`

**`AccountExceptionHandlerTest`** (new):
- `onCurrentPasswordMismatchReturns400WithCurrentPasswordMismatchType`
- `onPasswordPolicyViolationReturns400WithValidationErrorTypeAndDetail`

**`PasswordPolicyTest`** (modified — signature ripple + Finding 7 closure):
- Update every existing `validate(rawPassword)` call to the new 3-arg signature (mechanical,
  same class of change as every prior task's constructor-signature break).
- **Named test** `shouldRejectPasswordShorterThan12OrLongerThan128` — unchanged assertion, updated
  call signature. This is the test satisfying T08's named-test requirement: it now exercises logic
  that a real production call path (`AccountService.changePassword`) actually reaches for the
  first time, closing the gap Phase 1/2/3 raised.
- New/extended: assert the recorded `password.breach_check_failed` audit event's `accountUuid`/
  `actorUuid` match the values passed into `validate` (AC10).

## Constraints

- **Transactional:** single `@Transactional` `AccountService.changePassword` method.
- **Module boundaries:** no new cross-module dependency; everything stays within
  `account`/`common`.
- **Ordering/security:** status check → current-password check → policy check → mutation → audit,
  strictly in that order; no step after a rejection point may run.
- **Null handling:** `@NotBlank` on both DTO fields.
- **Constraint (Finding 6, currently inert):** a wrong current password must never affect any
  lockout counter. `LockoutService` doesn't exist in this codebase yet — no code change needed now,
  but this constraint should be re-checked whenever tasks 11–13 introduce it.

## Open Questions

None remaining. All four Phase 1/2/3 questions were resolved by explicit human decision at this
gate (see above).
