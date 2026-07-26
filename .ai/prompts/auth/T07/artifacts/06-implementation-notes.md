# auth · T07 — Phase 6: Implementation Notes

## What changed

**Created:**
- `account/dto/PasswordResetRequest.java` — `@NotBlank @Email String email`.
- `account/dto/PasswordResetConfirmRequest.java` — `@NotBlank String token`,
  `@NotBlank String newPassword`; overrides `toString()` to omit `newPassword` (Finding 7).

**Modified:**
- `account/VerificationTokenService.java` (T05) — new `consumeForPurpose(rawToken, Purpose)`
  method, self-contained, does not call or refactor the existing `consume`/`verify`/`issue`.
- `account/AccountService.java`:
  - New `RefreshTokenTracker` constructor dependency.
  - `issueAndEmitVerificationEmail` generalized from an `EMAIL_VERIFY`-only helper to accept
    `(Account, Purpose, String purposeLabel)`; `register` and `resendVerificationIfPending`
    updated to pass their purpose explicitly.
  - `activateFromVerificationToken` (T06): one-line fix — `consume(rawToken)` →
    `consumeForPurpose(rawToken, Purpose.EMAIL_VERIFY)` — closes the mirror-image bug discovered
    during Phase 3 design review, in already-shipped code.
  - New `requestPasswordReset(String email)` — issues + emits only for `ACTIVE`/`LOCKED` accounts.
  - New `resetPassword(String rawToken, String newPassword)` — purpose-checked consume → fresh
    account read → status pre-check → unlock if `LOCKED` → password hash update → revoke all
    families → audit.
  - New private `isPasswordResetEligible(Account)` helper (`ACTIVE || LOCKED`).
- `account/AccountController.java` — two new endpoints; class javadoc updated to list all five
  public routes.
- `common/PublicEndpoints.java` — two new entries.
- `token/RefreshTokenTracker.java` — new `revokeAllForPrincipal(String, String)` method, using the
  existing (previously unused) `findByPrincipalNameAndRevokedAtIsNull` finder and the existing
  `RefreshTokenFamily.revoke(...)` mutator; no explicit save call, relying on the same
  dirty-checking behavior `trackRotation` already depends on.
- `account/dto/RegistrationAcknowledgement.java` — new `forPasswordReset()` static factory;
  `standard()` untouched.

No other files touched.

## Mapping to the plan

Matches `artifacts/05-implementation-plan.md` exactly — method signatures, the private-method flow
for `requestPasswordReset`/`resetPassword`, the generalized helper's new signature, and the file
list all as planned.

## Mapping to acceptance criteria (frozen brief §Acceptance Criteria)

- **R12/Finding 5:** `passwordResetRequest` returns the bare `RegistrationAcknowledgement` (no
  `ResponseEntity` wrapper) → Spring defaults to `200`, matching `resendVerification` exactly.
- **R13:** `isPasswordResetEligible` gates issuance/emission to `ACTIVE`/`LOCKED` only — the
  deliberately different filter from `resendVerificationIfPending`'s `PENDING_VERIFICATION`-only
  check.
- **R14/Finding 8:** `resetPassword` calls `account.unlock()` when the pre-reset status is
  `LOCKED`, before `changePasswordHash` — a successful reset always leaves the account `ACTIVE`.
  `refreshTokenTracker.revokeAllForPrincipal(accountUuid, "PASSWORD_RESET")` revokes every family.
  `recordAudit("password.reset", accountUuid, accountUuid)` — self-service actor pattern, matching
  T06 Finding 5.
- **R15/Findings 1-4:** `consumeForPurpose` returns empty on purpose mismatch (Finding 1) without
  any mutation; `resetPassword` re-reads the account fresh after consume (Finding 4) and checks
  status *before* calling any guarded entity method (Findings 2/3 unified) — `changePasswordHash`'s
  own `DELETED`-only guard is structurally unreachable from this call path.
- **T06 regression closed:** `activateFromVerificationToken` now uses `consumeForPurpose(...,
  EMAIL_VERIFY)` — a `PASSWORD_RESET` token can no longer activate an account.
- **L11:** both new paths registered in `PublicEndpoints.METHOD_SCOPED`.
- **Finding 12 (module boundary):** `AccountService` now imports `RefreshTokenTracker`
  (`account → token`) — compiles cleanly, verified independently of the still-broken
  `SecurityChainsConfig`/`ReuseDetectingAuthorizationService` (see Build verification below);
  `ArchitectureTest` re-verification is Phase 7/10's job per the frozen brief.
- **Finding 7:** `PasswordResetConfirmRequest.toString()` omits `newPassword`.

## Deviations from the plan

None. Implementation matches the plan's signatures and flow descriptions exactly.

## Build verification

Same situation as every prior task: `mvn -pl services/auth compile` still fails on the
pre-existing, unrelated `token` package issue (`SecurityChainsConfig`,
`ReuseDetectingAuthorizationService` — unchanged, untouched by this task). Verified the eight
new/changed production files independently via targeted `javac` against the module's resolved
dependency classpath — this pulls in only the transitive chain these files actually reference
(`account.*`, `common.*`, and `token.RefreshTokenTracker`/`RefreshTokenFamily`/
`RefreshTokenFamilyRepository`, none of which touch the broken files). **Compiled with zero
errors**, including the new `account → token` cross-module dependency (Finding 12) — confirming
Phase 0's compile-safety check held.

**Not run in this phase** (Phase 10 scope, per guardrails): unit/controller tests for the new
methods/endpoints, and therefore `mvn test`. Per the frozen brief's explicit expectation, the
`AccountService` constructor change (new `RefreshTokenTracker` parameter) will break the existing
`AccountServiceTest` the same way T06's `VerificationTokenService` addition did — not fixed here,
expected to be caught at Phase 7 self-review.
