STATUS: FROZEN

# auth · T10 — Phase 4: Frozen Task Brief

Human-approved. Folds every Phase 3 (Kimi) finding into a final decision. Downstream phases may
not renegotiate anything below.

## Phase 3 findings — disposition

| # | Sev | Finding | Disposition |
|---|---|---|---|
| 1 | Medium | AC4's "identical response" comparison criteria left ambiguous ("`ProblemDetail` (or exception instance/type)") | **ACCEPTED, amended.** Pinned to the observable HTTP contract: same HTTP status, same `application/problem+json` content type (implicit — `ProblemDetail` always serializes this way), and equal `ProblemDetail` `status`/`type`/`title` fields, with no leaked account/token/state details. Test sits at the handler layer (`AccountExceptionHandlerTest`), where both surfaces already share `onVerificationTokenRejected`. |
| 2 | Medium | AC2's boundary set omits "wrong purpose" for `consumeForPurpose(..., EMAIL_VERIFY)` | **Factually incorrect as evidenced, ACCEPTED as wording only.** `shouldRejectPasswordResetTokenWhenConsumedForEmailVerify` (`VerificationTokenServiceTest.java:353-361`) already proves exactly this case — a `PASSWORD_RESET`-purpose token rejected when `EMAIL_VERIFY` is requested. No new test needed. AC2 is amended to explicitly credit this existing test as covering the wrong-purpose reason, so the acceptance criterion doesn't read as if that reason is unverified. |
| 3 | Low | R2 coverage "asserted only at the controller layer," service-layer duplicate-vs-new exception emission "not asserted" | **REJECTED — factually incorrect.** `registerRejectsKnownDuplicateAfterEncodingAndConstructingTheAccount` (`AccountServiceTest.java:142`, T09) already asserts `AccountService.register` throws `DuplicateEmailException` for a duplicate email; `registerMapsConstraintRaceToDuplicateEmail` (line 217) covers the concurrent-insert race variant. Both exist at the service layer, independent of the controller test. AC1 is amended to cite both. |
| 4 | Low | `package.md` §8's exact named test (`shouldReturnSameAcknowledgementForDuplicateAndNewRegistration`) doesn't exist by that name; the checklist item "All acceptance criteria have a passing named test from §8" is at risk under a strict literal reading | **ACCEPTED, wording only — decision unchanged from Phase 2.** No rename, no alias test — `registerReturnsTheIdenticalAcknowledgementOnDuplicateEmail_enumerationSafety` already fully satisfies R2's substance, and `package.md`'s naming is already known to drift from current state (same category of documentation staleness logged at T09 Phase 1). Phase 12 (this same pipeline) will cite the existing test explicitly by name against R2, closing the traceability risk without a rename. Recorded here as a final, explicit decision rather than an implicit one. |
| 5 | Low | Cross-surface comparison test's file location left to Phase 5, with an unverified hedge about Spring context | **ACCEPTED, decided now.** `AccountExceptionHandlerTest.java`. Confirmed via `handler = new AccountExceptionHandler()` (line 19) that this file already requires zero Spring context for all eight of its existing tests — Kimi's hedge about needing Spring/bean wiring was incorrect, but the recommended location is correct and now locked in, closing the ambiguity before Phase 5. |
| 6 | Low | AC2 conflates token-level reasons (not found, expired, used, wrong purpose) with account-level reasons (deleted, suspended account) | **ACCEPTED.** AC2 split into two explicit sub-lists below. |
| 7 | Low | "Invalid reset tokens" could be misread to include `POST /accounts/password-reset-request` | **ACCEPTED.** Explicit scope sentence added below. |

All Phase 3 findings are resolved. No open questions remain.

---

## Task

Add tests closing two confirmed coverage gaps for R2/R5/R15 (enumeration safety, L5): a
`consumeForPurpose(..., EMAIL_VERIFY)` boundary test, and a cross-surface comparison proving an
invalid-verification-token response and an invalid-reset-token response are identical to each
other, not merely each internally uniform.

## Purpose

Unchanged from Phase 2 — R2/R5/R15 are already substantially covered across three test files built
up since T02/T05/T06/T07; this task closes the two gaps Phase 1 confirmed and Phase 3 sharpened,
not a from-scratch build.

## Scope

**In:**
- A new `consumeForPurpose(..., EMAIL_VERIFY)` boundary test in `VerificationTokenServiceTest.java`
  covering: not-found, expired, already-used, deleted-account, suspended-account. Wrong-purpose is
  *not* re-tested here — already covered by the existing
  `shouldRejectPasswordResetTokenWhenConsumedForEmailVerify` (Finding 2's resolution).
- One new cross-surface test in `AccountExceptionHandlerTest.java` (Finding 5's resolution)
  directly comparing an invalid-verification-token `ProblemDetail` to an invalid-reset-token
  `ProblemDetail`, asserting equal `status`/`type`/`title` and no leaked detail (Finding 1's
  resolution).

**Out:**
- R2 (duplicate registration) — already fully covered by
  `registerReturnsTheIdenticalAcknowledgementOnDuplicateEmail_enumerationSafety`
  (`AccountControllerTest`) and `registerRejectsKnownDuplicateAfterEncodingAndConstructingTheAccount`
  / `registerMapsConstraintRaceToDuplicateEmail` (`AccountServiceTest`). No new test, no rename
  (Finding 3/4's resolution).
- **Reset-token uniformity refers specifically to `POST /accounts/password-reset` (the
  confirmation endpoint, `AccountService.resetPassword`), not to `POST
  /accounts/password-reset-request`** (Finding 7's resolution) — the request endpoint already
  returns a uniform acknowledgement for any email regardless of match, an entirely separate
  mechanism (no token involved) not touched by this task.
- Any production code change.
- Login and password-reset-request uniformity (not named by this task's scoped requirement IDs).
- Removing/refactoring the productionally-unused `verify`/`consume` methods or their existing test.

## Business Rules

- R2 — unchanged from Phase 2; already fully satisfied.
- R5 — every `consumeForPurpose(..., EMAIL_VERIFY)` rejection reason (not found, expired, used,
  wrong purpose, deleted/suspended account) is uniform. Wrong-purpose already proven; this task
  proves the remaining five reasons via one new test.
- R15 — unchanged from Phase 2; already fully satisfied at the `consumeForPurpose(...,
  PASSWORD_RESET)` level. This task adds the missing proof that R5 and R15's *outward* responses
  match each other, not just that each is internally uniform.

## Locked Decisions

- L5 — unchanged from Phase 2. This task strengthens evidence only; no behavior changes.

## Dependencies

Unchanged from Phase 2: `VerificationTokenService.consumeForPurpose`,
`AccountService.VerificationTokenRejectedException`,
`AccountExceptionHandler.onVerificationTokenRejected` — all existing, all unchanged.

## Inputs

Unchanged from Phase 2 — test-only inputs via existing helpers (`stubToken`, `usableAccount` in
`VerificationTokenServiceTest`; direct exception construction in `AccountExceptionHandlerTest`).

## Outputs

No production outputs change.

## State Changes

None.

## Files to Create

None.

## Files to Modify

- `services/auth/src/test/java/com/themistra/auth/account/VerificationTokenServiceTest.java` — one
  new boundary test for `consumeForPurpose(..., EMAIL_VERIFY)`.
- `services/auth/src/test/java/com/themistra/auth/account/AccountExceptionHandlerTest.java` — one
  new cross-surface comparison test (Finding 5's resolution — file decided, not deferred).

## Files NOT to Modify

- `AccountController.java`, `AccountService.java`, `AccountExceptionHandler.java`,
  `VerificationTokenService.java` — no production code change.
- `AccountControllerTest.java` — R2's existing coverage is sufficient (Finding 3/4's resolution).
- `AccountServiceTest.java` — no new test needed there either; the cross-surface test lives in
  `AccountExceptionHandlerTest.java` per Finding 5's resolution.
- Anything under `spec/`.

## Acceptance Criteria

| ID | Criterion |
|---|---|
| AC1 | Duplicate-email registration returns the identical `202` acknowledgement as new registration (R2) — satisfied by `registerReturnsTheIdenticalAcknowledgementOnDuplicateEmail_enumerationSafety` (`AccountControllerTest`), `registerRejectsKnownDuplicateAfterEncodingAndConstructingTheAccount`, and `registerMapsConstraintRaceToDuplicateEmail` (`AccountServiceTest`) — all pre-existing, cited not re-tested (Finding 3/4's resolution) |
| AC2a | Token-invalidity reasons for `consumeForPurpose(..., EMAIL_VERIFY)` — not found, expired, already used, wrong purpose — are uniform. Wrong purpose already covered by `shouldRejectPasswordResetTokenWhenConsumedForEmailVerify`; not-found/expired/used are this task's new test (Finding 6's resolution, split from account-level reasons) |
| AC2b | Account-ineligibility reasons for `consumeForPurpose(..., EMAIL_VERIFY)` — deleted account, suspended account — are uniform. This task's new test (Finding 6's resolution) |
| AC3 | Every `consumeForPurpose(..., PASSWORD_RESET)` rejection reason is uniform (R15) — already satisfied by five existing tests, regression-only |
| AC4 | An invalid-verification-token `ProblemDetail` and an invalid-reset-token `ProblemDetail` have equal `status`, `type`, and `title`, and neither leaks account/token/state detail — proven directly by one new test at the handler layer (Finding 1/5's resolution), not merely inferred from shared plumbing |

## Required Tests

- New: `consumeForPurpose(..., EMAIL_VERIFY)` boundary test, `VerificationTokenServiceTest.java` —
  not-found, expired, already-used, deleted-account, suspended-account (AC2a/AC2b). Mirrors the
  existing five-test `PASSWORD_RESET`-side pattern; may be one consolidated test (matching the
  existing `EMAIL_VERIFY`-side `verify`/`consume` named test's style) or several, at Phase 5's
  discretion — either satisfies AC2a/AC2b.
- New: cross-surface comparison test, `AccountExceptionHandlerTest.java` — construct a
  `VerificationTokenRejectedException` standing in for an invalid-verification-token rejection and
  one standing in for an invalid-reset-token rejection (both are the same class — the test's point
  is that the *same* handler method necessarily produces the *same* output for both, made explicit
  rather than left implicit), assert equal `status`/`type`/`title`, `detail` null for both (AC4).
- Regression: every existing test cited above (six `AccountServiceTest`, two
  `AccountControllerTest`/`AccountExceptionHandlerTest`, eight `VerificationTokenServiceTest`)
  continues passing unmodified.

## Constraints

Unchanged from Phase 2: no production code changes, existing test conventions (plain JUnit 5 +
Mockito + AssertJ, no Spring context — now *proven*, not just asserted, for
`AccountExceptionHandlerTest` per Finding 5), no unrelated churn/renaming of existing tests.

## Open Questions

No blockers. All Phase 3 findings resolved above.
