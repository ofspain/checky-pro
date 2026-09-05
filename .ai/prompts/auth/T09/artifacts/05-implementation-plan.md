# auth · T09 — Phase 5: Implementation Plan

Plans the frozen brief (`04-frozen-task-brief.md`). No code below — signatures and call-order only.

## Files to create

None — the frozen brief authorizes no new files.

## Files to modify

1. `services/auth/src/main/java/com/themistra/auth/account/AccountService.java`
2. `services/auth/src/main/java/com/themistra/auth/account/dto/RegisterAccountRequest.java`
3. `services/auth/src/test/java/com/themistra/auth/account/AccountServiceTest.java`
4. `services/auth/src/test/java/com/themistra/auth/account/AccountControllerTest.java`
5. `services/auth/src/test/java/com/themistra/auth/account/dto/RegisterAccountRequestValidationTest.java`
   — confirmed existing (not hypothetical); its `passwordBoundaries()` test (lines 45-50) asserts
   `@Size`-driven rejection at 11/129-char boundaries, which becomes false once `@Size` is removed
   from production. This test must be removed or rewritten to assert the length bound is no longer
   bean-validation-enforced (only `@NotBlank` remains at this layer).

## Public methods (signatures)

No public signature changes anywhere. `AccountService.register(RegisterAccountRequest)` and
`AccountService.resetPassword(String, String)` keep their existing signatures — only their method
bodies change (new statements inserted, existing statements reordered). `RegisterAccountRequest`'s
record component list (`email`, `password`) is unchanged — only the constraint annotation on
`password` is removed.

## Private methods

None added. No new private helpers — the `passwordPolicy.validate(...)` call is inserted inline
in each method, matching `changePassword`'s existing (T08) inline-call shape; introducing a shared
private helper for a two-call-site, single-line invocation would be premature abstraction.

## Entities used

- `Account` — unchanged. `register`'s existing static factory `Account.register(String email,
  String passwordHash)` is called at the same point as today, just earlier relative to
  `existsByEmail` (see Execution order below). Its returned instance's `getAccountUuid()` supplies
  the real, correlatable UUID for the policy check — no entity-side change (Finding 1's resolution,
  frozen brief).

## Repositories used

No repository interface changes. `AccountRepository.existsByEmail(String)` and
`AccountRepository.saveAndFlush(Account)` are called in the same relative order as today, just
after the new policy check instead of before it.

## Services used

- `PasswordPolicy.validate(String rawPassword, UUID accountUuid, UUID actorUuid)` — the only new
  collaborator call, at two call sites. Already a constructor-injected dependency of
  `AccountService` (wired since T08); no constructor change.

## Unit/integration tests required

All plain JUnit 5 + Mockito + AssertJ, no Spring context — matching every existing test in this
module (confirmed: no `MockMvc`/`@WebMvcTest` precedent anywhere).

**`AccountServiceTest.java`** — `passwordPolicy` is already a `@Mock` field wired into the
constructor call (T08); no setup change needed. One **existing test must be updated, not just new
ones added**:
- `registerRejectsKnownDuplicateWithoutTouchingEncoder` (currently line 142) asserts
  `verify(passwordEncoder, never()).encode(anyString())` for a duplicate email. This assertion
  becomes false under the new ordering: `Account.register(email, passwordEncoder.encode(...))` now
  runs *before* `existsByEmail`, so the encoder is always touched — this is the exact,
  human-approved trade-off from the frozen brief (Finding 1's resolution: accept one wasted BCrypt
  call on the duplicate-email path rather than change `Account.java`'s signature). Rename to
  `registerRejectsKnownDuplicateAfterEncodingAndConstructingTheAccount` (or similar) and flip the
  assertion to `verify(passwordEncoder).encode(RAW_PASSWORD)` (still `verify(accountRepository,
  never()).saveAndFlush(any())`, `verify(verificationTokenService, never()).issue(any(), any())`,
  `verify(outboxPublisher, never()).publish(any(), any(), any(), anyInt(), any())` — those remain
  true, only the encoder assertion inverts).

New test methods:
- `registerCallsPasswordPolicyValidateWithTheConstructedAccountUuidAndSubmittedPassword` — stub
  `passwordEncoder.encode(...)`, call `register(...)`, `verify(passwordPolicy).validate(eq(rawPassword),
  any(UUID.class), any(UUID.class))`, and assert the `accountUuid`/`actorUuid` arguments are equal
  to each other (self-service, matches `changePassword`'s existing pattern) via an `ArgumentCaptor`.
- `registerRejectsPolicyViolatingPasswordWithoutTouchingRepositoryOrOutbox` — stub
  `passwordPolicy.validate(...)` to `doThrow(new PasswordPolicy.PasswordPolicyViolationException(...))`,
  assert `register(...)` propagates it, then `verify(accountRepository, never()).existsByEmail(any())`,
  `verify(accountRepository, never()).saveAndFlush(any())`, `verify(outboxPublisher,
  never()).publish(any(), any(), any(), anyInt(), any())` — proves AC4 (policy check precedes the
  duplicate-email branch, no short-circuit before it).
- `registerRejectsPolicyViolatingPasswordEvenWhenEmailIsAlreadyRegistered` — same stub as above,
  but with an email that *would* have hit `existsByEmail` → true; asserts the same
  `PasswordPolicyViolationException` still propagates (not swallowed into the uniform `202`
  behavior `DuplicateEmailException` gets) — directly proves AC4's enumeration-safety claim: a
  policy violation is never silently converted into the duplicate-email uniform response.
- `resetPasswordCallsPasswordPolicyValidateBeforeAnyMutation` — stub
  `verificationTokenService.consumeForPurpose(...)` to return a `LOCKED` account's UUID (so
  `unlock()` would otherwise fire), stub `passwordPolicy.validate(...)` as a no-op, then assert via
  `InOrder inOrder = inOrder(passwordPolicy, passwordEncoder, refreshTokenTracker, auditService)`
  that `passwordPolicy.validate(...)` is verified before `passwordEncoder.encode(...)`,
  `refreshTokenTracker.revokeAllForPrincipal(...)`, and `auditService.record(...)` — mirrors T08's
  existing `InOrder` proof style exactly. (Proving `validate` precedes `Account.unlock()` itself
  requires either a spied `Account` or asserting the account's status transition side-effect;
  follow whichever pattern the existing `shouldResetPasswordAndRevokeAllFamiliesWithValidToken`
  test in this file already uses for observing `Account` state.)
- `resetPasswordRejectsPolicyViolatingPasswordWithoutMutatingAccountOrRevokingSessions` — stub
  `passwordPolicy.validate(...)` to throw, assert `resetPassword(...)` propagates it, then verify
  `passwordEncoder.encode(any())`, `refreshTokenTracker.revokeAllForPrincipal(any(), any())`, and
  `auditService.record(any())` are never called.
- Regression: run existing `register*` and `resetPassword`/`shouldResetPasswordAndRevokeAll...`
  tests unmodified — the new `passwordPolicy.validate(...)` call is a void-returning mock method
  with no stub configured, which Mockito's default answer treats as a no-op, so these should pass
  without any change.

**`AccountControllerTest.java`** — new tests mirroring the existing
`changePasswordPropagatesPolicyViolationForTheExceptionHandlerToTranslate` (T08) shape:
- `registerPropagatesPolicyViolationForTheExceptionHandlerToTranslate` — stub
  `accountService.register(any())` to `thenThrow(new PasswordPolicy.PasswordPolicyViolationException(...))`,
  assert `controller.register(...)` propagates it uncaught (`assertThatThrownBy`), confirming the
  controller's existing `try { ... } catch (DuplicateEmailException ignored)` block does not also
  swallow this distinct exception type.
- `passwordResetPropagatesPolicyViolationForTheExceptionHandlerToTranslate` — same shape for
  `accountService.resetPassword(any(), any())` / `controller.passwordReset(...)`.

**`RegisterAccountRequestValidationTest.java`**:
- Remove or rewrite `passwordBoundaries()` (lines 45-50) — the 11/129-char boundary assertions no
  longer hold once `@Size` is removed. Replace with either: deletion (length is no longer this
  layer's concern, `PasswordPolicyTest` already owns the boundary named test), or a rewritten
  assertion documenting that this layer now only rejects blank passwords, with a comment-free
  one-line justification if any reviewer would otherwise wonder why the boundary check vanished
  from this file. Preserve `validRequestPasses`, `noCompositionRules_longSimplePassphraseIsAllowed`,
  `blankAndMalformedEmailsRejected`, `blankPasswordRejected` unmodified — none depend on `@Size`.

## Execution order

1. `RegisterAccountRequest.java` — remove `@Size(min=12,max=128)` from `password()` (isolated,
   no dependents beyond its own test and `AccountService.register`'s new reliance on
   `PasswordPolicy` for the same bound).
2. `AccountService.java` — `register`: reorder to `normalize(email)` → `Account.register(email,
   passwordEncoder.encode(request.password()))` (unchanged call, just moved earlier) →
   `passwordPolicy.validate(request.password(), account.getAccountUuid(),
   account.getAccountUuid())` → `existsByEmail` check → `saveAndFlush`/catch → emit → return.
3. `AccountService.java` — `resetPassword`: insert `passwordPolicy.validate(newPassword,
   accountUuid, accountUuid)` immediately after the existing eligibility check
   (`isPasswordResetEligible`) and before the existing `if (account.getStatus() ==
   AccountStatus.LOCKED) { account.unlock(); }` block. Everything after stays in its current
   relative order (`unlock` → `changePasswordHash` → `revokeAllForPrincipal` → `recordAudit`).
4. `AccountServiceTest.java` — new tests (register-ordering pair, register-rejection pair,
   reset-ordering `InOrder` test, reset-rejection test), then confirm all pre-existing
   `register*`/`resetPassword*` tests still pass unmodified.
5. `AccountControllerTest.java` — two new propagation tests.
6. `RegisterAccountRequestValidationTest.java` — remove/rewrite `passwordBoundaries()`.
7. Compile + run the full changed-file set via the established `javac` +
   JUnit Platform Launcher workaround (module-wide `mvn test` still blocked by the pre-existing,
   unrelated `token` package break) — this is Phase 6's own verification step, listed here only to
   confirm the plan accounts for it; no code is written in this phase.

No schema/migration step — this task touches no persisted schema (matches the frozen brief's
"State Changes: None").
