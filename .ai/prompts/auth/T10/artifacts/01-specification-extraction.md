# auth · T10 — Phase 1: Specification Extraction

## Business Rules

- **R2.** If the normalized email already exists at `POST /accounts`, the system must return the
  identical `202 Accepted` registration acknowledgement as for a newly created account — no
  distinguishing status, body, or timing-observable signal.
- **R5.** If a verification token is invalid, expired, already used, or belongs to a
  deleted/suspended account, the system must return a uniform failure response that reveals no
  information about account existence or state — every one of those distinct reasons must produce
  the same response.
- **R15.** If a password-reset token is invalid, expired, already used, or belongs to a
  deleted/suspended account, the system must return a uniform failure response — the requirements
  text's exact phrase is "indistinguishable from a valid token," which taken completely literally
  would mean indistinguishable from a *successful* reset (impossible, since one is a `400` failure
  and the other a `204` success). Read in parallel with R5's clearer wording ("reveals no
  information about account existence or state") and L5's general framing, this is understood to
  mean: indistinguishable across the different *invalid* reasons, not indistinguishable from
  success. Flagged, not silently reinterpreted — see Open Questions.

## Locked Decisions

- **L5.** Enumeration-safe responses: login, registration, password-reset request,
  password-reset confirmation, and email verification must return uniform responses that don't
  reveal email/account existence, account state, or token validity. T10 tests three of these five
  surfaces — registration, email verification, and password-reset confirmation — matching exactly
  what the task statement names. Login and password-reset-request are out of scope for this task
  (not named in `tasks.md` task 10's text, and no requirement/named-test for them is in this
  task's scoped header).

## Files involved

**Existing files to extend (tests only — this is a test-only task, no production code named or
implied by the task statement):**
- `services/auth/src/test/java/com/themistra/auth/account/AccountControllerTest.java` — already
  has `registerReturnsTheIdenticalAcknowledgementOnDuplicateEmail_enumerationSafety` (R2, fully
  covers duplicate registration at the controller/response level already).
- `services/auth/src/test/java/com/themistra/auth/account/AccountExceptionHandlerTest.java` —
  already has `onVerificationTokenRejectedResponseIsIdenticalRegardlessOfConstructionSite` (proves
  `VerificationTokenRejectedException`'s response is uniform across different internal
  `AccountService` call sites, standing in for different rejection reasons).
- `services/auth/src/test/java/com/themistra/auth/account/AccountServiceTest.java` — already has
  six tests proving each rejection reason (verify-email side: token-not-found, wrong status,
  account-disappeared; reset side: token-not-found, account-disappeared, ineligible status) throws
  the single `VerificationTokenRejectedException` type, never a distinguishing one.

**Files NOT touched by this task (confirmed by Phase 0):**
- No production code — `AccountController.java`, `AccountService.java`,
  `AccountExceptionHandler.java` are all already correctly structured for R2/R5/R15; this task adds
  test coverage only.
- `spec/` — never modified.

## Dependencies

- `AccountService.VerificationTokenRejectedException` — the single exception type shared by both
  `activateFromVerificationToken` (R5) and `resetPassword` (R15) rejection paths. No signature or
  behavior change expected.
- `AccountService.DuplicateEmailException` — caught locally in `AccountController.register`, never
  reaches `AccountExceptionHandler`.
- `AccountExceptionHandler.onVerificationTokenRejected` — the single handler method mapping
  `VerificationTokenRejectedException` to a fixed `400`/`INVALID_TOKEN` `ProblemDetail`, reused by
  both R5 and R15's rejection paths.
- No new config keys, no new outbox events, no new audit event types.

## Acceptance Criteria

| # | Criterion | Requirement |
|---|---|---|
| AC1 | A duplicate-email registration returns an identical `202` acknowledgement to a new registration | R2 |
| AC2 | Every distinct invalid-verification-token reason (not found, expired, already used, wrong purpose, deleted/suspended account) produces an identical response | R5 |
| AC3 | Every distinct invalid-reset-token reason (not found, expired, already used, wrong purpose, deleted/suspended account) produces an identical response | R15 |
| AC4 | An invalid-verification-token response and an invalid-reset-token response are, at minimum, both mapped through the same exception type and handler method — the two failure surfaces are not accidentally divergent from each other | R5/R15, L5 (cross-surface consistency; see Phase 0's flagged gap — no existing test compares them side-by-side) |

## Tests required

**Named tests from the header:**
- `shouldReturnSameAcknowledgementForDuplicateAndNewRegistration` (R1/R2 per `package.md` §8) —
  doesn't exist under this literal name; closest existing equivalent:
  `registerReturnsTheIdenticalAcknowledgementOnDuplicateEmail_enumerationSafety`
  (`AccountControllerTest.java`), which already covers the same ground.
- `shouldNotRevealAccountExistenceForInvalidVerificationToken` (R5 per `package.md` §8) — **already
  exists verbatim**, at `VerificationTokenServiceTest.java:93`. Thoroughly covers not-found,
  expired, used, deleted-account, and suspended-account via both `verify()` and `consume()`. Gap:
  it exercises the purpose-blind `verify`/`consume` API, not `consumeForPurpose` — the method T07
  changed `AccountService.activateFromVerificationToken` to actually call. The named test exists
  but not against the current production call path's method.

**Implied but not listed in `package.md` §8** (the task statement explicitly requires it; widened
per this phase's instruction to widen only when the task clearly requires it):
- An analogous consolidated reset-token uniformity test for R15 — `package.md` §8 has no named
  test for this at all (a documentation gap, not a signal R15 is out of scope; R15 is in this
  task's scoped requirement IDs and the task statement explicitly names "invalid reset tokens").
  Unlike R5's situation, this isn't really a coverage gap so much as a *naming/consolidation* one:
  `consumeForPurpose(..., PASSWORD_RESET)` already has five separate, thorough tests in
  `VerificationTokenServiceTest.java` (wrong-purpose, race/account-becomes-unusable, expired,
  already-used, unusable-account-upfront — lines 335-421), each independently proving
  `Optional.empty()` uniformity; there's no single test consolidating them the way the `EMAIL_VERIFY`
  side has one.

**Boundary tests implied by R5/R15's exact wording** ("invalid, expired, already used, or belongs
to a deleted/suspended account"), cross-checked against what already exists across both layers:
- At the `AccountService`/`AccountController` layer: invalid/not-found token, wrong account status,
  and account-disappeared-after-consume are all covered for both verify-email and reset (six
  existing tests, `AccountServiceTest.java`, all asserting the single
  `VerificationTokenRejectedException` type).
- At the `VerificationTokenService` layer: `EMAIL_VERIFY`'s `verify`/`consume` API has full
  boundary coverage (one consolidated named test); `PASSWORD_RESET`'s `consumeForPurpose` also has
  full boundary coverage (five separate tests) — expired and already-used are directly exercised on
  both sides, contrary to my own Phase 0 first-pass assessment, which incorrectly assumed no test
  distinguished them (corrected in Phase 0's artifact before writing this one).
- **Genuine, confirmed gap:** no test anywhere exercises `consumeForPurpose(..., EMAIL_VERIFY)`'s
  own not-found/expired/used/deleted/suspended boundary set the way the purpose-blind `verify`/
  `consume` test does — the closest thing is `shouldRejectTokenWhenPurposeDoesNotMatchAndLeaveItUnconsumed`
  (only proves the purpose-mismatch case, not the full reason set) and the six `AccountServiceTest`
  tests (which only see the already-folded `Optional.empty()`, not `VerificationTokenService`'s own
  boundary behavior for that specific method).

## Open Questions

- **Q1 (wording, non-blocking).** R15's literal text ("uniform failure response indistinguishable
  from a valid token") is almost certainly imprecise phrasing meaning "indistinguishable across
  invalid reasons," not literally indistinguishable from a successful reset. Proceeding under that
  reading (consistent with R5's parallel, clearer wording and L5's general framing). Flagged for
  the spec author, not fixed (never modify `spec/`).
- **Q2 (documentation gap, non-blocking).** `package.md` §8 has no named test at all for R15's
  reset-token uniformity, unlike R5's verification-token uniformity. Proceeding with R15 fully in
  scope regardless, since it's in this task's scoped requirement IDs and the task statement
  explicitly names it — the missing named test is a `package.md` gap, not a signal to skip R15.
- **Q3 (genuine, deferred to Phase 2 design).** `shouldNotRevealAccountExistenceForInvalidVerificationToken`
  already exists verbatim (R5) but tests a superseded method (`verify`/`consume` instead of
  `consumeForPurpose`). Whether T10 should add a `consumeForPurpose`-targeted equivalent under a
  new name, or whether R2's named test should be introduced/renamed onto
  `registerReturnsTheIdenticalAcknowledgementOnDuplicateEmail_enumerationSafety`'s existing
  coverage — not decided here, this is a design/implementation-planning question.
- **Q4 (genuine, deferred to Phase 2 design).** Whether AC4 (cross-surface consistency between
  invalid-verification-token and invalid-reset-token responses) needs a dedicated new test, given
  both already share one exception class and one handler method — or whether that shared structure
  is itself sufficient proof and a dedicated comparison test would be redundant. Not decided here.

No genuine blockers — all four questions above have a reasonable default and none halts progress
into Phase 2.
