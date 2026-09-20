# auth · T08 — Phase 5: Implementation Plan

Planning only — no code. Every file below traces to the frozen brief's Files sections; nothing
added beyond what it authorizes.

## Files to create

- `services/auth/src/main/java/com/themistra/auth/account/dto/ChangePasswordRequest.java`

## Files to modify

- `services/auth/src/main/java/com/themistra/auth/common/ProblemTypes.java`
- `services/auth/src/main/java/com/themistra/auth/account/Account.java`
- `services/auth/src/main/java/com/themistra/auth/account/PasswordPolicy.java`
- `services/auth/src/main/java/com/themistra/auth/account/AccountService.java`
- `services/auth/src/main/java/com/themistra/auth/account/AccountExceptionHandler.java`
- `services/auth/src/main/java/com/themistra/auth/account/AccountController.java`

## Public methods (signatures)

**`ChangePasswordRequest`** (new record):
```java
public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {
    @Override public String toString() { ... } // omits both fields — unlike PasswordResetConfirmRequest,
                                                // both are real account credentials, not a bearer token
}
```

**`ProblemTypes`** (new constant):
```java
public static final URI CURRENT_PASSWORD_MISMATCH = URI.create(BASE + "current-password-mismatch");
```

**`Account`** (widened, signature unchanged):
```java
public void changePasswordHash(String newPasswordHash)
// guard widens from `status == DELETED` to `status != ACTIVE`
```

**`PasswordPolicy`** (widened signature):
```java
public void validate(String rawPassword, UUID accountUuid, UUID actorUuid)
// was: public void validate(String rawPassword)
```

**`AccountService`** (new constructor param, new method, new nested exception):
```java
public AccountService(AccountRepository accountRepository, PasswordEncoder passwordEncoder,
        OutboxPublisher outboxPublisher, AuditService auditService,
        VerificationTokenService verificationTokenService, RefreshTokenTracker refreshTokenTracker,
        PasswordPolicy passwordPolicy, Clock clock)   // passwordPolicy inserted before clock

@Transactional
public void changePassword(UUID accountUuid, String currentPassword, String newPassword)

public static class CurrentPasswordMismatchException extends RuntimeException {
    public CurrentPasswordMismatchException() { super("Current password does not match"); }
}
```

**`AccountExceptionHandler`** (two new handlers):
```java
@ExceptionHandler(AccountService.CurrentPasswordMismatchException.class)
ProblemDetail onCurrentPasswordMismatch(AccountService.CurrentPasswordMismatchException e)

@ExceptionHandler(PasswordPolicy.PasswordPolicyViolationException.class)
ProblemDetail onPasswordPolicyViolation(PasswordPolicy.PasswordPolicyViolationException e)
```

**`AccountController`** (new endpoint):
```java
@PostMapping("/me/password")
public ResponseEntity<Void> changePassword(Authentication authentication,
        @Valid @RequestBody ChangePasswordRequest request)
```

## Private methods

**`AccountService.changePassword`'s body** (no new private helper methods — reuses two existing
private helpers unchanged):
1. `getAccount(accountUuid)` — existing, throws `AccountNotFoundException` if missing.
2. `recordAudit("password.changed", accountUuid, accountUuid)` — existing, unchanged signature.

**`PasswordPolicy`**'s existing private methods (`validateLength`, `recordBreachCheckFailedAudit`)
are modified in place, not replaced:
- `validateNotBreached(String rawPassword, UUID accountUuid, UUID actorUuid)` — gains the two new
  parameters, passes them through to `recordBreachCheckFailedAudit`.
- `recordBreachCheckFailedAudit(UUID accountUuid, UUID actorUuid)` — gains the two new parameters,
  passes them into `RecordAuditEventRequest` instead of the current `null, null`.
- `validateLength` — unchanged.

No new private methods anywhere.

## Entities used

- `Account` — `getStatus()`, `getPasswordHash()`, `changePasswordHash(String)` (widened guard).
  No new fields, no new persistence annotations.

## Repositories used

- `AccountRepository` — only via the existing `findByAccountUuid` call inside `getAccount`; no new
  repository method.

## Services used

- `PasswordEncoder` — `matches(raw, hash)` (first production use in this module),
  `encode(raw)` (existing use, new call site).
- `PasswordPolicy` — `validate(rawPassword, accountUuid, actorUuid)` (first production caller).
- `AuditService` — via the existing `recordAudit` helper; also indirectly via `PasswordPolicy`'s
  own (now-corrected) breach-check-failure audit.
- No `OutboxPublisher` call — no event for this action (frozen brief).
- No `RefreshTokenTracker` call — no revocation (frozen brief decision 3).

## Unit/integration tests required

Per the frozen brief's Required Tests section, across four files:

- `AccountServiceTest` (extend): `shouldChangePasswordWithCorrectCurrentPasswordAndPolicyCompliantNewPassword`,
  `shouldRejectChangePasswordWhenCurrentPasswordDoesNotMatch`,
  `shouldRejectChangePasswordWhenNewPasswordViolatesPolicy`,
  `shouldRejectChangePasswordForEveryNonActiveAccountStatus`,
  `shouldNotRevokeRefreshTokenFamiliesOnSuccessfulPasswordChange`,
  `shouldAllowNewPasswordIdenticalToCurrentPassword`.
- `AccountControllerTest` (extend): `changePasswordReturnsNoContentOnSuccess`,
  `changePasswordPropagatesCurrentPasswordMismatchForTheExceptionHandlerToTranslate`,
  `changePasswordPropagatesPolicyViolationForTheExceptionHandlerToTranslate`.
- `AccountExceptionHandlerTest` (extend): `onCurrentPasswordMismatchReturns400WithCurrentPasswordMismatchType`,
  `onPasswordPolicyViolationReturns400WithValidationErrorTypeAndDetail`.
- `PasswordPolicyTest` (extend/fix): update every existing `validate(...)` call site to the new
  3-arg signature (mechanical; existing assertions, including the named test
  `shouldRejectPasswordShorterThan12OrLongerThan128`, are otherwise unchanged); add an assertion
  that the breach-check-failure audit event carries the real `accountUuid`/`actorUuid` passed in.
- No new test file — unlike T06/T07, this task adds no new DTO validation edge cases beyond
  `@NotBlank` (no `@Email`, no custom format), so a dedicated `ChangePasswordRequestValidationTest`
  is not required by the brief; a couple of `@NotBlank` boundary assertions can live directly in
  `AccountControllerTest` or be added to the existing `PasswordResetDtoTest`-style file only if
  Phase 7/8 review flags a real gap — not planned proactively here (no speculative test files).

Full test generation itself is Phase 10's job, per this pipeline's established split — this section
only enumerates what the frozen brief already requires so Phase 6 implementation and Phase 7/8
review can check coverage against a known list.

## Execution order

1. `common/ProblemTypes.java` — add `CURRENT_PASSWORD_MISMATCH`. No dependencies.
2. `account/Account.java` — widen `changePasswordHash`'s guard. Isolated entity change.
3. `account/PasswordPolicy.java` — widen `validate`'s signature and thread the new parameters
   through to the audit call. No dependency on 1/2.
4. `account/dto/ChangePasswordRequest.java` — new DTO. No dependency on 1–3.
5. `account/AccountService.java` — new `PasswordPolicy` constructor dependency, new
   `changePassword` method, new nested `CurrentPasswordMismatchException`. Depends on 1 (indirectly,
   via the exception handler that will reference the new type), 2, 3.
6. `account/AccountExceptionHandler.java` — two new mappings. Depends on 1 and 5 (references
   `AccountService.CurrentPasswordMismatchException` and `PasswordPolicy.PasswordPolicyViolationException`).
7. `account/AccountController.java` — new endpoint. Depends on 4 and 5.
8. Tests (Phase 10): `PasswordPolicyTest` signature fixes first (unblocks compilation of anything
   that constructs a real `PasswordPolicy`), then `AccountServiceTest`, `AccountControllerTest`,
   `AccountExceptionHandlerTest`, matching the same order the production files were built in.
