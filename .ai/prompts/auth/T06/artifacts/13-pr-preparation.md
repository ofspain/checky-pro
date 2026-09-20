# auth · T06 — Phase 13: PR / Commit Preparation

Phase 12 verdict was **PASS**. Prepared for merge below. Branch off `main`; `main` stays
deployable — this task adds two new public endpoints and wires previously-inert domain code
(T05's `VerificationTokenService`, T03's `PasswordPolicy` remains untouched) into real request
paths for the first time in this chain.

## Commit title

```
Add self-service email verification endpoints (T06)
```

## Commit message

```
Add self-service email verification endpoints (T06)

Extend AccountController with POST /accounts/verify-email and POST
/accounts/resend-verification, both public. Wire T05's
VerificationTokenService into AccountService for the first time:
registration now issues a token and emits auth.email.requested (R3);
verify-email redeems it and activates the account (R4); every
rejection reason - not found, expired, used, or wrong account status -
produces one uniform 400/INVALID_TOKEN response (R5). Update
EventTopics with the verification-token -> auth.email.requested
mapping (R44).

Two explicit, human-approved deviations from the literal spec, both
recorded at their approval gates, not silent:
- R6 says "authenticated caller" for resend-verification, but a
  PENDING_VERIFICATION account cannot yet obtain a token. Decided at
  Phase 0: public and email-identified instead, mirroring the existing
  password-reset-request shape (uniform ack regardless of match).
- EmailRequestedEventPayload carries the raw verification token -
  Notification Service has no other channel to obtain it. Formalized
  as a narrow, LOCKED exception to agents.md's credential-in-transit
  rule at Phase 4, mitigated by an overridden toString() that excludes
  the token and reliance on existing bounded outbox/Kafka retention.

The account-status check in activateFromVerificationToken happens
before Account.activateEmail() is called, not after - calling it on a
non-PENDING_VERIFICATION account throws InvalidAccountStateException,
a distinguishing exception that would have broken R5's uniformity
(caught in design review, Phase 3 Finding 2).

A defect logged as deferred at Phase 9 (AccountNotFoundException
leaking a 404 in a defensive, normally-unreachable path) was fixed for
real at Phase 11 once a regression test made the cost of leaving it
concrete, rather than carried into final verification.

28 unit tests cover both named tests, every rejection reason on both
the verify and consume paths, self-service audit actor identity,
outbox aggregate-ID/type correctness, email normalization, duplicate-
registration skip behavior, and raw-token non-leakage.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

## Files changed

**Production:**
- `services/auth/src/main/java/com/themistra/auth/account/AccountController.java` (modified —
  two new endpoints)
- `services/auth/src/main/java/com/themistra/auth/account/AccountService.java` (modified —
  `register` emits an event; two new methods; one nested exception)
- `services/auth/src/main/java/com/themistra/auth/account/AccountExceptionHandler.java`
  (modified — one new mapping)
- `services/auth/src/main/java/com/themistra/auth/account/dto/VerifyEmailRequest.java` (new)
- `services/auth/src/main/java/com/themistra/auth/account/dto/ResendVerificationRequest.java` (new)
- `services/auth/src/main/java/com/themistra/auth/account/event/EmailRequestedEventPayload.java`
  (new)
- `services/auth/src/main/java/com/themistra/auth/events/EventTopics.java` (modified — one new
  mapping entry)
- `services/auth/src/main/java/com/themistra/auth/common/PublicEndpoints.java` (modified — two
  new entries)
- `services/auth/src/main/java/com/themistra/auth/common/ProblemTypes.java` (modified — one new
  constant)

**Tests:**
- `services/auth/src/test/java/com/themistra/auth/account/AccountServiceTest.java` (modified —
  constructor fix plus 8 new/replaced tests)
- `services/auth/src/test/java/com/themistra/auth/account/AccountControllerTest.java` (modified
  — 3 new tests)
- `services/auth/src/test/java/com/themistra/auth/account/AccountExceptionHandlerTest.java` (new
  — 2 tests)
- `services/auth/src/test/java/com/themistra/auth/events/EventTopicsTest.java` (modified — 1 new
  test)

**Process artifacts** (`.ai/prompts/auth/T06/artifacts/`): `00-repository-understanding.md`
through `12-specification-verification.md` — full phase trail, including the Phase 0 human
decision on R6, the Phase 3/8/11 Kimi reviews, the Phase 4/9 human-approval resolutions, and the
live production fix applied during Phase 11's test-gap triage.

## Summary

Implements `tasks.md` task 6: the self-service email-verification flow that makes T05's
verification-token service reachable for the first time, replacing the admin-only stand-in that's
existed since T02. Three things worth a reviewer's attention: (1) two deliberate, narrow
deviations from the literal spec text (R6's authentication model, the raw token in the event
payload) — both flagged and human-approved at their respective phase gates, documented in-code at
the exact decision points, not buried; (2) the account-status pre-check in
`activateFromVerificationToken` exists specifically to prevent a real enumeration-safety leak
(calling the existing guarded `activateEmail()` on the wrong status would throw a distinguishing
exception) — this was caught in adversarial design review before any code was written, not found
in production; (3) one defect (a narrower version of the same leak class, in a defensive/
unreachable path) was initially deferred and then fixed within this same PR once test-writing
made its cost concrete — the defer-then-fix sequence is visible in the phase artifacts rather than
squashed away.

## Testing performed

Same situation as every task in this chain: `mvn -pl services/auth test` cannot run to completion
due to the pre-existing, unrelated `token` package compile failure (tracked since T03, still
unfixed, not touched by this branch). Verified by compiling the new/changed test classes and their
real transitive dependency chain directly with `javac` against the module's resolved test-scope
classpath, then executing via the JUnit Platform `Launcher` API — the same engine Surefire
delegates to.

**Result: 28/28 tests passing**, ~600ms, no Spring context, no database. Two Mockito mistakes were
caught and fixed while writing tests this task (an existing-test compile break from the changed
`AccountService` constructor, and a repeat of T05's nested-stubbing gotcha — this time triggered
by a `Mockito.spy` rather than a plain mock, confirming the gotcha applies to any Mockito-managed
object, not just `@Mock` fields).

Kimi's independent code review (Phase 8) and test review (Phase 11) both ran against this
implementation. Phase 8 found 2 HIGH, 3 MEDIUM, 2 LOW findings — all folded in or explicitly
resolved. Phase 11 found 6 gaps, one of which (Gap 2) was checked against the actual code and
rejected as factually incorrect rather than applied blindly; the rest were folded in, including a
live production fix for a previously-deferred defect. Full requirement-to-evidence-to-test
traceability is in `12-specification-verification.md`.

## Specification references

- **Task:** `spec/auth-service/tasks.md`, task 6 — "Self-service verification endpoints."
- **Requirements:** R3, R4, R6 (modified per Phase 0 human decision), R44 (`requirements.md`); R5
  and L11 widened in at Phase 1 as directly operative.
- **LOCKED decisions:** L5 (`design.md` §4a); a T06-specific, human-approved LOCKED exception for
  the raw token in `EmailRequestedEventPayload` (Finding 1, recorded in full at Phase 4).
- **Named tests:** `shouldActivateAccountWithValidVerificationToken`,
  `shouldResendVerificationOnlyForPending accounts`, `shouldEmitVerifyEmailEventOnRegistration`,
  `shouldRouteEmailRequestedEventsToAuthEmailRequestedTopic` (`package.md` §8) — all four
  implemented and passing; the second preserved verbatim via `@DisplayName` since its literal text
  isn't a legal Java identifier.
