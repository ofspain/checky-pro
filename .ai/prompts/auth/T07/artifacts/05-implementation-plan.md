# auth · T07 — Phase 5: Implementation Plan

Plans against `artifacts/04-frozen-task-brief.md` only. Every file traces to that brief's Files to
Create / Files to Modify. No new exception type (reuses `AccountService.VerificationTokenRejectedException`
from T06); no new problem type (reuses `ProblemTypes.INVALID_TOKEN`); no new test file for
`RefreshTokenTracker` (confirmed `RefreshTokenTrackerTest.java` already exists — extended, not
created, at Phase 10).

## Files to create

1. `services/auth/src/main/java/com/themistra/auth/account/dto/PasswordResetRequest.java`
2. `services/auth/src/main/java/com/themistra/auth/account/dto/PasswordResetConfirmRequest.java`

## Files to modify

1. `services/auth/src/main/java/com/themistra/auth/account/VerificationTokenService.java` — one
   new method (`consumeForPurpose`); `consume`/`verify`/`issue` untouched, per the frozen brief.
2. `services/auth/src/main/java/com/themistra/auth/account/AccountService.java` — generalized
   private helper; two new public methods; one-line fix to `activateFromVerificationToken`; new
   `RefreshTokenTracker` constructor dependency.
3. `services/auth/src/main/java/com/themistra/auth/token/RefreshTokenTracker.java` — one new
   method.
4. `services/auth/src/main/java/com/themistra/auth/account/AccountController.java` — two new
   endpoints.
5. `services/auth/src/main/java/com/themistra/auth/common/PublicEndpoints.java` — two new entries.
6. `services/auth/src/main/java/com/themistra/auth/account/dto/RegistrationAcknowledgement.java` —
   one new static factory; `standard()` untouched.

## Public methods (signatures)

**`VerificationTokenService`** (new method, alongside the unchanged `consume`/`verify`/`issue`):
```java
public Optional<UUID> consumeForPurpose(String rawToken, VerificationToken.Purpose purpose)
```
Self-contained — does not call or refactor the existing `consume()` (frozen brief: "unchanged").
Returns empty (no mutation) on: token not found, purpose mismatch, account unusable, or the atomic
`markConsumed` affecting zero rows. Same uniform-shape contract as `consume()`, with one added
check.

**`AccountService`** (new/changed members):
```java
@Transactional
public void requestPasswordReset(String email)

@Transactional
public void resetPassword(String rawToken, String newPassword)
```
`resetPassword` returns `void`, not `AccountResponse` — nothing consumes the return value (the
controller always responds `204` regardless), simpler than `activateFromVerificationToken`'s
existing `AccountResponse` return, which *is* similarly discarded by its own controller method but
wasn't worth changing retroactively.

`activateFromVerificationToken`'s body changes by exactly one line:
```java
// before: verificationTokenService.consume(rawToken)
// after:  verificationTokenService.consumeForPurpose(rawToken, VerificationToken.Purpose.EMAIL_VERIFY)
```

**`AccountController`** (two new handlers):
```java
@PostMapping("/password-reset-request")
public RegistrationAcknowledgement passwordResetRequest(@Valid @RequestBody PasswordResetRequest request)

@PostMapping("/password-reset")
public ResponseEntity<Void> passwordReset(@Valid @RequestBody PasswordResetConfirmRequest request)
```
`passwordResetRequest` returns the bare DTO (no `ResponseEntity` wrapper) — Spring defaults to
`200`, matching `resendVerification`'s existing pattern exactly (Finding 5's resolution).
`passwordReset` mirrors `verifyEmail`'s exact shape: `204` on success, the rejection exception
propagates uncaught for `AccountExceptionHandler` to translate (no local catch).

**`RefreshTokenTracker`** (new method, alongside the unchanged existing ones):
```java
@Transactional
public void revokeAllForPrincipal(String principalName, String reason)
```
Loads every unrevoked family for the principal via the existing
`familyRepository.findByPrincipalNameAndRevokedAtIsNull(principalName)` and calls the existing
`family.revoke(reason, now)` on each — no explicit `.save()` needed, since these are
JPA-managed entities within the transaction and `trackRotation`'s existing code already relies on
the same dirty-checking behavior (`family.rotateTo(...)` with no explicit save call).

**`PasswordResetRequest`** (record, `account.dto`):
```java
record PasswordResetRequest(@NotBlank @Email String email)
```

**`PasswordResetConfirmRequest`** (record, `account.dto`):
```java
record PasswordResetConfirmRequest(@NotBlank String token, @NotBlank String newPassword)
```
Overrides `toString()` to omit `newPassword` (Finding 7) — the `token` field is *not* redacted,
consistent with `VerifyEmailRequest`'s existing precedent of not redacting its own token field.

**`RegistrationAcknowledgement`** (new static factory, existing `standard()` unchanged):
```java
public static RegistrationAcknowledgement forPasswordReset()
```
Returns password-reset-appropriate wording — explicitly not "...verify your account."

## Private methods

**`AccountService`:**
- `issueAndEmitVerificationEmail(Account, VerificationToken.Purpose, String purposeLabel)` —
  **generalized** from the existing `issueAndEmitVerificationEmail(Account)` (which was hardcoded
  to `EMAIL_VERIFY`/`"verify_email"`). All three callers pass their own purpose:
  - `register(...)`: `(saved, EMAIL_VERIFY, "verify_email")` (unchanged behavior, new call shape).
  - `resendVerificationIfPending(...)`: `(account, EMAIL_VERIFY, "verify_email")` (unchanged
    behavior, new call shape).
  - `requestPasswordReset(...)` (new): `(account, PASSWORD_RESET, "password_reset")`.

  This is a same-purpose generalization of code already being modified in this file, not
  "unrelated refactoring" — it directly serves R13's requirement to emit the same event shape for
  a new purpose without duplicating the issue+publish logic a third time.
- `isResetEligible(Account)` — `status == ACTIVE || status == LOCKED` (R13's filter, deliberately
  different from `resendVerificationIfPending`'s existing `PENDING_VERIFICATION`-only filter).

**`requestPasswordReset(String email)` flow:**
1. `accountRepository.findByEmail(normalize(email))` — empty → no-op, return.
2. `.filter(this::isResetEligible)` — wrong status → no-op, return.
3. Match → `issueAndEmitVerificationEmail(account, PASSWORD_RESET, "password_reset")`.

**`resetPassword(String rawToken, String newPassword)` flow:**
1. `verificationTokenService.consumeForPurpose(rawToken, PASSWORD_RESET)` → empty →
   `throw new VerificationTokenRejectedException()` (covers: not found, expired, used, wrong
   purpose — Finding 1).
2. `accountRepository.findByAccountUuid(accountUuid)` — fresh read (Finding 4) — empty →
   `throw new VerificationTokenRejectedException()` (defensive, same reasoning as T06's equivalent
   fix).
3. Status check: not `ACTIVE` and not `LOCKED` → `throw new VerificationTokenRejectedException()`
   (covers `PENDING_VERIFICATION`, `DELETED`, `SUSPENDED` — Findings 2/3 unified into one
   pre-check, `Account.changePasswordHash`'s own guard never reached).
4. If status was `LOCKED` → `account.unlock()` (Finding 8).
5. `account.changePasswordHash(passwordEncoder.encode(newPassword))`.
6. `refreshTokenTracker.revokeAllForPrincipal(account.getAccountUuid().toString(),
   "PASSWORD_RESET")`.
7. `recordAudit("password.reset", account.getAccountUuid(), account.getAccountUuid())` (existing
   private helper, unchanged — `actorUuid` = the account's own UUID, self-service pattern).

## Entities used

- `Account` (existing) — `getStatus()`, `unlock()`, `changePasswordHash(String)`; no new entity.
- `VerificationToken` (T05, existing) — read via `consumeForPurpose`, no entity change.
- `RefreshTokenFamily` (existing, `token` module) — `revoke(String, Instant)`, no entity change.

## Repositories used

- `AccountRepository` (existing) — `findByEmail`, `findByAccountUuid`.
- `VerificationTokenRepository` (T05, existing, via `VerificationTokenService` only) — no direct
  use from `account`.
- `RefreshTokenFamilyRepository` (existing, `token` module, package-private) —
  `findByPrincipalNameAndRevokedAtIsNull`, used only from within `RefreshTokenTracker` (same
  module) — `AccountService` never touches it directly, preserving the "repositories accessed only
  through their module's own service" convention even across the new `account → token` boundary.

## Services used

- `VerificationTokenService` (T05, existing bean, new method).
- `RefreshTokenTracker` (existing bean, `token` module) — **new dependency for `AccountService`**.
- `AuditService` (existing, via `AccountService`'s existing `recordAudit` helper).
- `OutboxPublisher`, `EventTopics` (existing, unchanged).

## Unit tests required

All unit-level (plain JUnit 5 + Mockito, no Spring context, fixed `Clock`), consistent with every
prior task. No integration/`MockMvc` test authorized (same as T06).

**`VerificationTokenServiceTest` (existing, extended):**
- `shouldConsumeTokenWhenPurposeMatches`.
- `shouldRejectTokenWhenPurposeDoesNotMatch` — an `EMAIL_VERIFY` token requested as
  `PASSWORD_RESET` (or vice versa) returns empty; the token remains unconsumed (a second,
  correctly-purposed consume attempt still succeeds).

**`AccountServiceTest` (existing, extended):**
- `shouldEmitPasswordResetEventOnlyWhenEmailExists` (named) — nonexistent email and
  wrong-status-but-existing email (`PENDING_VERIFICATION`, `DELETED`, `SUSPENDED`) all produce zero
  `VerificationTokenService`/`OutboxPublisher` interactions; `ACTIVE` and `LOCKED` both do.
- `shouldResetPasswordAndRevokeAllFamiliesWithValidToken` (named) — password hash updated (via the
  mocked `PasswordEncoder`), `refreshTokenTracker.revokeAllForPrincipal` called with the account's
  UUID string and `"PASSWORD_RESET"`, audit recorded with `actorUuid` = account UUID,
  `outcome = SUCCESS`.
- `shouldRejectEmailVerifyTokenUsedForPasswordReset` (Finding 1, named in the frozen brief).
- `shouldUnlockAccountOnSuccessfulPasswordReset` — a `LOCKED` account ends `ACTIVE`.
- `shouldRejectPasswordResetForPendingVerificationAccount`, `...ForDeletedAccount`,
  `...ForSuspendedAccount` — all via the unified status pre-check, all uniform.
- `shouldRejectPasswordResetWhenAccountDisappearsAfterConsume` — mirrors T06's equivalent
  defensive test.
- `shouldNotLeakNewPasswordInLogsOrExceptions` (or folded into the above tests as an assertion) —
  no mock/captor ever observes the raw `newPassword` value outside the `PasswordEncoder.encode`
  call itself.
- `activateFromVerificationTokenStillWorksAfterPurposeAwareConsumeSwap` — regression test
  confirming T06's existing named test's *behavior* still holds after the one-line change
  (T06's own `shouldActivateAccountWithValidVerificationToken` test also needs its stub updated
  from `consume(...)` to `consumeForPurpose(..., EMAIL_VERIFY)` — an update to an existing test,
  not a new file).
- `shouldRejectPasswordResetTokenUsedForEmailVerification` — the T06-side regression test required
  by the frozen brief.

**`AccountControllerTest` (existing, extended):**
- `passwordResetRequestReturnsOkWithAcknowledgement` — `200`, `RegistrationAcknowledgement`.
- `passwordResetReturnsNoContentOnSuccess` — `204`.
- `passwordResetPropagatesRejectionForTheExceptionHandlerToTranslate` — mirrors
  `verifyEmailPropagatesRejectionForTheExceptionHandlerToTranslate`'s existing pattern.

**`RefreshTokenTrackerTest` (existing, extended):**
- `revokeAllForPrincipalRevokesEveryUnrevokedFamily`.
- `revokeAllForPrincipalDoesNotTouchAnotherPrincipalsFamilies` — isolation check.
- `revokeAllForPrincipalIsANoOpWhenNoFamiliesExist`.

**`ArchitectureTest` (existing, no code change, re-run only):**
- Confirm it still passes with `AccountService`'s new `RefreshTokenTracker` dependency (Finding
  12) — no new rule needed, just verification.

## Execution order

1. `account/dto/RegistrationAcknowledgement.java` — new factory (no dependencies).
2. `account/VerificationTokenService.java` — `consumeForPurpose` (depends on existing infra only).
3. `token/RefreshTokenTracker.java` — `revokeAllForPrincipal` (depends on existing repo/entity
   only; independently compilable from the still-broken `SecurityChainsConfig`/
   `ReuseDetectingAuthorizationService`, confirmed at Phase 0).
4. `account/dto/PasswordResetRequest.java`, `PasswordResetConfirmRequest.java` — new DTOs.
5. `account/AccountService.java` — generalize the private helper; add `requestPasswordReset`,
   `resetPassword`; fix `activateFromVerificationToken`'s `consume` call; add the
   `RefreshTokenTracker` constructor dependency. Depends on steps 1–4.
6. `common/PublicEndpoints.java` — two new entries.
7. `account/AccountController.java` — two new endpoints. Depends on steps 4–6.
8. Tests (`VerificationTokenServiceTest`, `AccountServiceTest`, `AccountControllerTest`,
   `RefreshTokenTrackerTest` extensions) — Phase 10, not this phase.
9. Compile/test verification, following established precedent: direct `javac` against the module's
   resolved classpath (the unrelated `token`-package compile failure remains unfixed), then execute
   via the JUnit Platform `Launcher` API.
