# auth · T06 — Phase 7: Self-Review

Findings only, against the frozen brief (`04-frozen-task-brief.md`) and `agents.md`. No fixes
applied here — Phase 9 handles remediation after independent review (Phase 8).

---

## Finding 1 — `AccountServiceTest.java` no longer compiles (HIGH)

**Issue:** `AccountService`'s constructor gained a new `VerificationTokenService` parameter
(inserted before `Clock`). The existing `AccountServiceTest.setUp()` still calls the old 5-argument
constructor. This is not a hypothetical risk — I compiled it directly and confirmed:

```
error: constructor AccountService in class AccountService cannot be applied to given types;
  required: AccountRepository,PasswordEncoder,OutboxPublisher,AuditService,VerificationTokenService,Clock
  found:    AccountRepository,PasswordEncoder,OutboxPublisher,AuditService,Clock
```

**Severity:** HIGH — this isn't "missing new test coverage" (Phase 10's job); it's an *existing,
already-committed* test file that the module can no longer compile at all. Every other test in the
module is blocked from running via Maven until this is fixed, on top of the pre-existing unrelated
`token` package issue.

**Evidence:** `services/auth/src/test/java/com/themistra/auth/account/AccountServiceTest.java:59-60`;
`services/auth/src/main/java/com/themistra/auth/account/AccountService.java:42-44`.

**Recommendation:** `AccountServiceTest.setUp()` needs a mocked `VerificationTokenService` added
to its constructor call. Whether this is done now (Phase 9, since it's arguably a defect the
implementation caused, not new test-writing) or deferred to Phase 10 is a process question for the
human — but it must be fixed before Phase 12 can verify a passing test suite either way.

---

## Finding 2 — `register`'s javadoc directly contradicts its own code (MEDIUM)

**Issue:** The method's javadoc (lines 58–61) still reads: *"No event is published here... The
`auth.email.requested` event that would trigger the verification email belongs to the not-yet-built
verification-token flow."* This was true before this task; it is false now — line 78 calls
`issueAndEmitVerificationEmail(saved)`, which does exactly what the comment says doesn't happen.

**Severity:** MEDIUM — not a runtime bug, but actively misleading to the next person reading this
method, in a security-relevant area (event emission correctness).

**Evidence:** `AccountService.java:58-61` (stale claim) vs. `:78` (the actual, contradicting call).

**Recommendation:** Update or remove the stale paragraph; the surrounding "no event... per
target-design §9" framing needs rewriting to reflect that registration now does emit
`auth.email.requested`.

---

## Finding 3 — `activateEmail(UUID, UUID)`'s javadoc is also stale (MEDIUM)

**Issue:** This method's javadoc (lines 125–129) still says: *"Interim (D-024): the intended
self-service flow... is not yet built. Until it is, this is reachable only via an authenticated
ADMIN endpoint."* The self-service flow now exists (`activateFromVerificationToken`, added by this
task). The admin path remains intentionally unchanged and still valid to keep — but the comment
claiming the self-service flow "is not yet built" is now incorrect.

**Severity:** MEDIUM — same class of issue as Finding 2: stale documentation in a security-relevant
area, not a functional defect.

**Evidence:** `AccountService.java:125-129` vs. the new `activateFromVerificationToken` method at
`:92-106`, in the same file.

**Recommendation:** Update the javadoc to note that the self-service path now exists
(`activateFromVerificationToken`) and that this method remains the admin-initiated variant
(distinguishable audit `actorUuid`, by design — not a duplicate to be merged).

---

## Finding 4 — `activateFromVerificationToken` can leak account-existence via `AccountNotFoundException` in a defensive edge case (LOW/MEDIUM)

**Issue:** After `verificationTokenService.consume(rawToken)` resolves an account UUID, line 97
calls the shared private `getAccount(accountUuid)`, which throws `AccountNotFoundException` if the
UUID isn't found — a *different*, distinguishing exception mapped by `AccountExceptionHandler` to
`404` "Account not found," not the uniform `400`/`INVALID_TOKEN` R5 requires. In the current schema,
`verification_tokens.account_id` has `ON DELETE CASCADE` to `accounts`, so a token belonging to a
since-deleted account would itself already be gone — this path is not reachable through normal
operation today. But it's a *latent* violation of R5's "verify-email has exactly two possible
outcomes" guarantee: any future change to that constraint, a data-integrity bug, or a differently-
wired caller of this method would silently reintroduce a distinguishing response here.

**Severity:** LOW/MEDIUM — currently unreachable given the schema's `ON DELETE CASCADE`, but the
method's own correctness shouldn't depend on that external guarantee holding forever, especially in
a method whose entire purpose is enumeration safety.

**Evidence:** `AccountService.java:97` (`getAccount(accountUuid)`, can throw
`AccountNotFoundException`); `AccountExceptionHandler.java:17-23` (that exception's *separate*,
distinguishing `404` mapping); `design.md`'s DDL for `verification_tokens.account_id ... ON DELETE
CASCADE` (T05 Phase 0 notes).

**Recommendation:** Either catch `AccountNotFoundException` here and rethrow as
`VerificationTokenRejectedException`, or use a lookup path that returns `Optional`/throws the
uniform exception directly, so `activateFromVerificationToken`'s failure contract doesn't depend on
a foreign-key constraint elsewhere in the schema never changing.

---

## Dimensions checked with no findings

- **Transaction boundaries:** `activateFromVerificationToken` and `resendVerificationIfPending` are
  both `@Transactional`, correctly wrapping T05's `consume`/`issue` (which join rather than open a
  new transaction) together with the subsequent state change/event emission — `issue()`'s token
  insert and the outbox write cannot commit independently of each other.
- **Module boundaries:** all new code stays within `account`, `events`, and `common`; no new
  cross-module dependency.
- **Thread-safety:** `AccountService` remains a stateless singleton; no new mutable instance state.
- **Enumeration safety (aside from Finding 4):** `resendVerificationIfPending` has no return value
  for the controller to branch on; `verifyEmail`'s only two outcomes are `204` and the single
  `400`/`INVALID_TOKEN` mapping.
- **Secret-handling:** `EmailRequestedEventPayload.toString()` correctly omits the raw token; no
  log statement anywhere in the changed files references a raw token.
- **Null-safety:** no manual null checks added, but this is consistent with `AccountService`'s
  existing convention (e.g. `findLoginView`) of relying on `@Valid`/`@NotBlank` at the controller
  boundary rather than defensive checks inside the service — not a new inconsistency.
- **Money types:** N/A.
