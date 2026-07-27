# auth · T10 — Phase 2: Task Implementation Brief

## Task

Add tests proving duplicate registration, invalid verification tokens, and invalid reset tokens
each produce identical/uniform responses — closing the specific coverage gaps Phase 1 confirmed,
not re-proving what already exists.

## Purpose

R2/R5/R15 (enumeration safety, L5) are largely already covered by existing tests across three
layers (`AccountControllerTest`, `AccountServiceTest`, `VerificationTokenServiceTest`) built up
across T02/T05/T06/T07. Phase 1 found the coverage is real but has two concrete gaps: (1) R5's
exact named test exists but targets `verify`/`consume` — methods with zero production callers
today, superseded by `consumeForPurpose` since T07 — not the method the live code path actually
uses; (2) nothing anywhere directly compares an invalid-verification-token outcome to an
invalid-reset-token outcome side by side, even though both already share one exception class and
one handler method.

## Scope

**In:**
- A new test (or tests) proving `VerificationTokenService.consumeForPurpose(..., EMAIL_VERIFY)`'s
  own boundary set (not-found, expired, already-used, deleted-account, suspended-account) is
  uniform — mirroring the existing five-test pattern already covering
  `consumeForPurpose(..., PASSWORD_RESET)`.
- One new test explicitly cross-comparing an invalid-verification-token response and an
  invalid-reset-token response, proving L5's cross-surface consistency (AC4) rather than leaving
  it as an implicit consequence of shared plumbing.

**Out:**
- R2 (duplicate registration) — `registerReturnsTheIdenticalAcknowledgementOnDuplicateEmail_enumerationSafety`
  (`AccountControllerTest.java`) already fully satisfies this requirement's substance. No new test,
  no rename — renaming an already-correct, already-clearly-named test purely to match
  `package.md`'s drifted naming would be unrelated churn, not in scope for this task.
- `PasswordPolicy`-related enumeration behavior (T09's scope, already closed).
- Login and password-reset-request uniformity — L5 lists them, but neither the task statement nor
  this task's scoped requirement IDs (R2, R5, R15) name them.
- Any production code change — this is a test-only task; `AccountController`, `AccountService`,
  `AccountExceptionHandler`, and `VerificationTokenService` are all already structurally correct
  for R2/R5/R15.
- Removing or refactoring the now-productionally-unused `verify`/`consume` methods or their
  existing test — confirmed via grep that no production code calls them, but removing dead code is
  not this task's job and risks breaking an intentionally-retained public API surface.

## Business Rules

- R2 — duplicate-email registration returns the identical `202` acknowledgement as a new
  registration. Already fully tested; regression-only from this task's perspective.
- R5 — every invalid-verification-token reason (not found, expired, used, wrong purpose,
  deleted/suspended account) produces a uniform response. Tested at the exception-type and
  response-shape levels already; the `consumeForPurpose`-specific boundary gap is this task's
  target.
- R15 — every invalid-reset-token reason produces a uniform response, on the same terms as R5.
  Already thoroughly tested at the `consumeForPurpose` level (five separate tests); this task adds
  the cross-surface comparison against R5, not new boundary coverage on the reset side.

## Locked Decisions

- L5 — enumeration-safe responses. This task strengthens the *evidence* for L5's registration,
  email-verification, and password-reset-confirmation guarantees; it does not change any of the
  three endpoints' actual behavior.

## Dependencies

- `VerificationTokenService.consumeForPurpose(String rawToken, VerificationToken.Purpose purpose)`
  — existing, unchanged signature; test target for the boundary-gap fix.
- `AccountService.VerificationTokenRejectedException` — existing, unchanged; used by the
  cross-surface comparison test as the shared type both `activateFromVerificationToken` and
  `resetPassword` throw.
- `AccountExceptionHandler.onVerificationTokenRejected` — existing, unchanged; the mapping both
  surfaces share, exercised by the cross-surface comparison test if written at the handler layer.

## Inputs

Test-only inputs: raw tokens (valid/expired/used/wrong-purpose), `Account` fixtures in each
relevant status (`PENDING_VERIFICATION`, `ACTIVE`, `LOCKED`, `SUSPENDED`, `DELETED`) — all
constructible via existing test helpers (`stubToken`, `usableAccount`) already present in
`VerificationTokenServiceTest.java`.

## Outputs

No production outputs change. Test outputs are assertions only.

## State Changes

None — test-only task, no persisted state, no new outbox events, no new audit events.

## Files to Create

None.

## Files to Modify

- `services/auth/src/test/java/com/themistra/auth/account/VerificationTokenServiceTest.java` — add
  boundary coverage for `consumeForPurpose(..., EMAIL_VERIFY)`.
- `services/auth/src/test/java/com/themistra/auth/account/AccountExceptionHandlerTest.java` (or
  `AccountServiceTest.java` — exact location is a Phase 5 planning decision) — add the cross-surface
  comparison test.

## Files NOT to Modify

- `AccountController.java`, `AccountService.java`, `AccountExceptionHandler.java`,
  `VerificationTokenService.java` — no production code change.
- `AccountControllerTest.java` — R2's existing coverage is already sufficient; no addition needed.
- Anything under `spec/`.

## Acceptance Criteria

| ID | Criterion |
|---|---|
| AC1 | Duplicate-email registration returns the identical `202` acknowledgement as new registration (R2) — already satisfied, regression-only |
| AC2 | Every `consumeForPurpose(..., EMAIL_VERIFY)` rejection reason (not-found, expired, used, deleted, suspended) returns `Optional.empty()` uniformly (R5) |
| AC3 | Every `consumeForPurpose(..., PASSWORD_RESET)` rejection reason returns `Optional.empty()` uniformly (R15) — already satisfied, regression-only |
| AC4 | An invalid-verification-token response and an invalid-reset-token response are directly shown to be identical to each other, not merely each internally uniform (L5 cross-surface consistency) |

## Required Tests

- New: a `consumeForPurpose(..., EMAIL_VERIFY)` boundary test in `VerificationTokenServiceTest.java`,
  mirroring the existing `PASSWORD_RESET`-side pattern (not-found, expired, already-used,
  deleted-account, suspended-account) — closes AC2's gap.
- New: one test directly comparing the `ProblemDetail` (or exception instance/type) produced by an
  invalid-verification-token rejection against one produced by an invalid-reset-token rejection,
  asserting they are identical — closes AC4.
- Regression: every existing test named in Phase 0/1 (six `AccountServiceTest` tests, two
  `AccountControllerTest`/`AccountExceptionHandlerTest` tests, six `VerificationTokenServiceTest`
  tests) continues passing unmodified.

## Constraints

- **No production code changes** — this is strictly a test-addition task; any production change
  would be scope creep.
- **Test conventions** — plain JUnit 5 + Mockito + AssertJ, no Spring context, fixed `Clock`,
  matching every existing test in this module (no `MockMvc`/`@WebMvcTest`).
- **No unrelated churn** — do not rename, restructure, or "clean up" existing passing tests
  (including the `verify`/`consume`-based R5 named test) just because Phase 1 found their naming
  imperfect; only add what closes the two confirmed gaps.
- **Exact file placement for the cross-surface test** (`AccountExceptionHandlerTest.java` vs.
  `AccountServiceTest.java`) is left to Phase 5's Implementation Plan — either is defensible and
  the choice doesn't affect test correctness.

## Open Questions

No blockers.
