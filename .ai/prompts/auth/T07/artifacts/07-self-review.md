# auth · T07 — Phase 7: Self-Review

Findings only, against the frozen brief (`04-frozen-task-brief.md`) and `agents.md`. No fixes
applied here — Phase 9 handles remediation after independent review (Phase 8).

---

## Finding 1 — `AccountServiceTest.java` no longer compiles (HIGH)

**Issue:** `AccountService`'s constructor gained a new `RefreshTokenTracker` parameter. The
existing `AccountServiceTest.setUp()` still constructs it with the old 6-argument signature.
Confirmed by direct compilation:

```
error: constructor AccountService in class AccountService cannot be applied to given types;
  required: AccountRepository,PasswordEncoder,OutboxPublisher,AuditService,VerificationTokenService,RefreshTokenTracker,Clock
  found:    AccountRepository,PasswordEncoder,OutboxPublisher,AuditService,VerificationTokenService,Clock
```

**Severity:** HIGH — blocks compilation of the module's existing test suite, same class of issue
as T06's equivalent finding. Expected per the frozen brief's own Constraints section; not a
surprise, but still real and still blocking.

**Evidence:** `services/auth/src/test/java/com/themistra/auth/account/AccountServiceTest.java:63-65`;
`services/auth/src/main/java/com/themistra/auth/account/AccountService.java:44-47`.

**Recommendation:** Add a mocked `RefreshTokenTracker` to the test's `setUp()` constructor call.

---

## Finding 2 — Four existing tests stub the wrong method after the `activateFromVerificationToken` fix (HIGH)

**Issue:** `activateFromVerificationToken` now calls `consumeForPurpose(rawToken, EMAIL_VERIFY)`
instead of `consume(rawToken)` (the intentional T06-regression fix, Phase 4). Four existing tests
still stub the old method:

```
AccountServiceTest.java:189  shouldActivateAccountWithValidVerificationToken
AccountServiceTest.java:213  shouldRejectVerificationWhenTokenConsumeReturnsEmpty
AccountServiceTest.java:233  shouldRejectVerificationWhenAccountIsNotPendingVerification
AccountServiceTest.java:251  shouldRejectVerificationWhenAccountDisappearsAfterConsume
```

Once Finding 1's constructor fix lands and the file compiles again, all four will fail for real —
not a compile error this time, but a genuine assertion failure: with `consumeForPurpose` unstubbed,
Mockito's default answer for an `Optional`-returning method is `Optional.empty()`, so
`activateFromVerificationToken` would throw `VerificationTokenRejectedException` in every one of
these tests (including the three that currently expect that exact exception for *other* reasons,
and the one that expects success). Under `MockitoExtension`'s strict-stubbing mode, the now-unused
`consume(...)` stubs would *also* independently fail as `UnnecessaryStubbingException`.

**Severity:** HIGH — a broader version of T06's single-test regression (Kimi's Finding 2 there);
this is four tests, not one, all silently wrong in the same way.

**Evidence:** `AccountServiceTest.java:189,213,233,251` (all `verificationTokenService.consume(...)`
stubs); `AccountService.java:102-104` (`activateFromVerificationToken`'s actual call).

**Recommendation:** Update all four stubs from `consume(...)` to
`consumeForPurpose(..., VerificationToken.Purpose.EMAIL_VERIFY)`.

---

## Dimensions checked with no findings

- **Correctness (purpose-check placement):** `consumeForPurpose`'s purpose check
  (`VerificationTokenService.java:124-126`) happens strictly before any mutation and before the
  account-usability check — a wrong-purpose token is never marked used, matching Finding 1 of the
  design review exactly.
- **Correctness (uniform failure shape):** every `consumeForPurpose` failure path — not found,
  wrong purpose, unusable account, lost the atomic-update race — returns the identical
  `Optional.empty()`; the caller (`resetPassword`/`activateFromVerificationToken`) can't
  distinguish any of them, upholding R15/R5.
- **Correctness (`resetPassword` mutation ordering):** account-eligibility is checked once, on the
  freshly-read account, before any mutation; `unlock()` (only called when the pre-check found
  `LOCKED`) and `changePasswordHash` both then succeed unconditionally — neither call's own guard
  can fail at that point, so no distinguishing exception can leak from either.
- **Transaction boundaries:** `resetPassword`'s full sequence (consume → re-read → status-check →
  unlock-if-needed → password-update → `refreshTokenTracker.revokeAllForPrincipal` → audit) is one
  `@Transactional` method; `RefreshTokenTracker.revokeAllForPrincipal` (its own `@Transactional`,
  default `REQUIRED` propagation) joins the same transaction rather than opening a new one —
  correct, consistent with how `VerificationTokenService`'s methods already join
  `AccountService`'s transactions.
- **Module boundaries:** `account`'s new dependency on `token.RefreshTokenTracker` compiles
  cleanly and independently of the still-broken `SecurityChainsConfig`/
  `ReuseDetectingAuthorizationService` (confirmed at Phase 0 and again at Phase 6).
  `RefreshTokenTracker.revokeAllForPrincipal` only touches its own module's repository
  (`RefreshTokenFamilyRepository`, package-private) — `AccountService` never reaches around it.
- **Idempotency:** `revokeAllForPrincipal` is naturally idempotent — the query already filters to
  `RevokedAtIsNull`, so a second call against the same principal simply finds nothing to revoke.
- **Enumeration safety / secret-handling:** `PasswordResetConfirmRequest.toString()` correctly
  omits `newPassword`; no log statement in any changed file references a raw password or token.
- **Consistency:** `resetPassword` and `requestPasswordReset` both reuse the same
  `isPasswordResetEligible` private helper for their respective `ACTIVE`/`LOCKED` gates, avoiding
  the two checks silently drifting apart over time.
- **Money types:** N/A.
