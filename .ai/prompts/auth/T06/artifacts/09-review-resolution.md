# auth · T06 — Phase 9: Review Resolution

**Human Approval gate.** Decisions below made by femi, applied by the model. Self-review
(`07-self-review.md`) found Findings 1–4; Kimi's independent review (`08-independent-review.md`)
confirmed 1, 3, 4, 5 (Kimi's numbering) and added two more in the same category (Kimi 2, 6) plus a
Phase-10 reminder (Kimi 7).

---

## Accepted and fixed

### Self-review 1 / Kimi 1 — `AccountServiceTest.java` no longer compiles

**Reason accepted:** confirmed by direct compilation — a hard, existing-test build break, not a
hypothetical.

**Change made:** added `@Mock private VerificationTokenService verificationTokenService;` and
included it in `setUp()`'s constructor call. Added a shared, `lenient()` stub for
`verificationTokenService.issue(...)` so every test reaching `register`'s success path gets a
valid, non-null `VerificationTokenResult` without each test repeating the stub (mirrors T05's
`usableAccount()` fixture-builder pattern).

### Kimi 2 — `registerNeverPublishesAnEvent` asserted the opposite of the new required behavior

**Reason accepted (folded in alongside Finding 1, same fix):** merely fixing the constructor call
would have left a test that compiles but fails logically — Kimi correctly identified that fixing
compilation without fixing the assertion would just trade one red build for another.

**Change made:** replaced with `registerIssuesVerificationTokenAndEmitsEmailRequestedEvent`,
asserting `verificationTokenService.issue(...)` is called and `outboxPublisher.publish(...)` is
called with aggregate type `"verification-token"`, aggregate ID = the new account's UUID, and an
`EmailRequestedEventPayload` carrying purpose `"verify_email"` and the stubbed raw token — plus a
regression check that `auth.user.registered` (a *different* event) still isn't published at
signup time.

### Self-review 2 / Kimi 4 — `register`'s javadoc contradicted its own code

**Change made:** rewrote the javadoc to state that registration now issues a token and emits
`auth.email.requested` in the same transaction, and clarified that `auth.user.registered` fires
from either activation path, never at signup.

### Self-review 3 / Kimi 5 — `activateEmail(UUID, UUID)`'s javadoc was stale

**Change made:** rewrote to describe it as the admin-initiated path, explicitly cross-referencing
`activateFromVerificationToken` as the (now-existing) self-service counterpart, and noting they're
intentionally kept distinct rather than merged.

### Kimi 6 — `AccountController`'s class-level javadoc didn't list the new public routes

**Reason accepted:** same category as self-review 2/3 (stale documentation), trivial to fix
alongside them.

**Change made:** rewrote the class javadoc to enumerate all three public POST routes.

---

## Logged, not fixed

### Self-review 4 / Kimi 3 — `activateFromVerificationToken` can leak account existence via `AccountNotFoundException`

**Reason deferred (explicit human instruction: "log finding 4"):** real, but currently unreachable
given `verification_tokens.account_id ON DELETE CASCADE` — a defensive-correctness improvement,
not a live defect. Recommendation on record for a future pass (this task's Phase 9, a later task,
or a dedicated hardening pass): replace the `getAccount(accountUuid)` call in
`activateFromVerificationToken` with `accountRepository.findByAccountUuid(accountUuid)` and treat
`Optional.empty()` identically to a non-`PENDING_VERIFICATION` account — i.e., throw
`VerificationTokenRejectedException` instead of letting `AccountNotFoundException` reach
`AccountExceptionHandler`'s separate `404` mapping.

### Kimi 7 — No T06-specific tests exist yet beyond the two just-fixed regression tests

**Reason deferred:** by design, this pipeline splits implementation/fix work from full test
generation (Phase 10) — same precedent as every prior task in this chain. The frozen brief's full
Required Tests list (verify-email success/failure paths, resend-verification match/no-match,
`EventTopics` routing, `AccountExceptionHandler`'s direct unit test) remains Phase 10's job.

---

## Verification

`AccountServiceTest` (12 tests, including the new/replaced one) and `AccountControllerTest`
(existing, unmodified — confirmed it still compiles and passes against the changed
`AccountService`) both compiled and ran via the established `javac` + JUnit Platform `Launcher`
method (the pre-existing, unrelated `token` package failure still blocks a real `mvn test` run).

**Result: 14/14 tests successful, 0 failed, 0 skipped, ~700ms.**
