# auth · T10 — Phase 5: Implementation Plan

Plans the frozen brief (`04-frozen-task-brief.md`). No code below — signatures and test structure
only.

## Files to create

None — the frozen brief authorizes no new files.

## Files to modify

1. `services/auth/src/test/java/com/themistra/auth/account/VerificationTokenServiceTest.java`
2. `services/auth/src/test/java/com/themistra/auth/account/AccountExceptionHandlerTest.java`

## Public methods (signatures)

No production signature changes anywhere — test-only task. No new public test-helper methods
either; both new tests reuse existing private helpers already in each file
(`stubToken(rawToken, purpose, expiresAt, usedAt)` and `usableAccount(status)` in
`VerificationTokenServiceTest`; direct `new AccountService.VerificationTokenRejectedException()`
construction, already the established pattern in `AccountExceptionHandlerTest`).

## Private methods

None added.

## Entities used

- `Account` (mocked via the existing `usableAccount(AccountStatus)` helper) — `DELETED` and
  `SUSPENDED` fixtures needed for AC2b.
- `VerificationToken` (constructed via the existing `stubToken(...)` helper, `EMAIL_VERIFY` purpose)
  — not-found (no stub), expired, and already-used fixtures needed for AC2a.

## Repositories used

`VerificationTokenRepository` (mocked) — `findByTokenHash` (via `stubToken`) and `markConsumed`
(stubbed per-case, matching the existing `PASSWORD_RESET`-side test pattern at
`VerificationTokenServiceTest.java:380-408`). `AccountRepository` (mocked) — `findById` (via
`usableAccount`).

## Services used

`VerificationTokenService.consumeForPurpose` — the method under test for the first new test.
`AccountExceptionHandler.onVerificationTokenRejected` — the method under test for the second (a
plain Mockito-free direct call, matching this file's existing pattern of `handler =
new AccountExceptionHandler()`, no Spring context).

## Unit/integration tests required

All plain JUnit 5 + Mockito + AssertJ, no Spring context — matching every existing test in both
files.

**`VerificationTokenServiceTest.java`** — one new consolidated test, mirroring the style of the
existing `EMAIL_VERIFY`-side named test `shouldNotRevealAccountExistenceForInvalidVerificationToken`
(line 93, which tests the superseded `verify`/`consume` API) but targeting `consumeForPurpose`
instead — the method the current production call path (`AccountService.activateFromVerificationToken`,
T07) actually uses:

- `shouldNotRevealAccountExistenceForInvalidVerificationTokenViaConsumeForPurpose` — five cases in
  one test method (matching the existing named test's own internal structure of covering multiple
  reasons in sequence), each asserting `consumeForPurpose(rawToken, EMAIL_VERIFY)).isEmpty()`:
  1. **Not found** — no `findByTokenHash` stub (defaults to `Optional.empty()`).
  2. **Expired** — `stubToken(rawToken, EMAIL_VERIFY, NOW.minusSeconds(1), null)`, account usable
     (`usableAccount(ACTIVE)`), `markConsumed(...)` stubbed to return `0` — mirrors the
     `PASSWORD_RESET`-side `shouldRejectExpiredPasswordResetTokenViaConsumeForPurpose` pattern
     exactly (AC2a).
  3. **Already used** — `stubToken(rawToken, EMAIL_VERIFY, NOW.plusSeconds(3600), NOW.minusSeconds(1))`,
     account usable, `markConsumed(...)` stubbed to return `0` — mirrors
     `shouldRejectAlreadyUsedPasswordResetTokenViaConsumeForPurpose` (AC2a).
  4. **Deleted account** — `stubToken(rawToken, EMAIL_VERIFY, NOW.plusSeconds(3600), null)`,
     `usableAccount(DELETED)`, asserts `consumeForPurpose(...)` is empty and
     `verify(tokenRepository, never()).markConsumed(...)` — the pre-check must reject before any
     mutation is attempted, mirroring `shouldRejectConsumeForPurposeWhenAccountIsUnusable`'s
     never-markConsumed assertion (AC2b).
  5. **Suspended account** — same shape as (4) with `usableAccount(SUSPENDED)` (AC2b).
  - Wrong-purpose is *not* included (frozen brief Finding 2's resolution — already covered by
    `shouldRejectPasswordResetTokenWhenConsumedForEmailVerify`, cited in a comment, not duplicated).

**`AccountExceptionHandlerTest.java`** — one new test:

- `onVerificationTokenRejectedResponseIsIdenticalForVerifyEmailAndPasswordResetSurfaces` — construct
  two `AccountService.VerificationTokenRejectedException` instances (standing in for one thrown by
  `activateFromVerificationToken` and one thrown by `resetPassword` — both are the same exception
  class by construction, per `AccountService.java`), call
  `handler.onVerificationTokenRejected(...)` on each, and assert the two resulting `ProblemDetail`s
  have equal `getStatus()`, `getType()`, `getTitle()`, and both have `getDetail() == null` — proves
  AC4 directly rather than leaving it as an inferred consequence of shared plumbing. Comment should
  note this test is deliberately similar in shape to the existing
  `onVerificationTokenRejectedResponseIsIdenticalRegardlessOfConstructionSite` (which proves
  uniformity *within* one surface) — this one proves it *across* both surfaces.

Regression: run every existing test in both files unmodified — no changes to any existing test
method, per the frozen brief's Constraints (no unrelated churn).

## Execution order

1. `VerificationTokenServiceTest.java` — add the one new consolidated `consumeForPurpose(...,
   EMAIL_VERIFY)` boundary test (AC2a/AC2b), placed near the existing `PASSWORD_RESET`-side
   `consumeForPurpose` tests (after `shouldRejectConsumeForPurposeWhenAccountIsUnusable`, line 421)
   for locality with its sibling tests.
2. `AccountExceptionHandlerTest.java` — add the one new cross-surface comparison test (AC4), placed
   near the existing `onVerificationTokenRejectedResponseIsIdenticalRegardlessOfConstructionSite`
   test for locality.
3. Compile + run the two changed files (plus their unchanged siblings, for the regression check)
   via the established `javac` + JUnit Platform Launcher workaround — this is Phase 6's own
   verification step, listed here only to confirm the plan accounts for it; no code is written in
   this phase.

No schema/migration step — this task touches no persisted schema and no production code at all
(matches the frozen brief's "State Changes: None").
