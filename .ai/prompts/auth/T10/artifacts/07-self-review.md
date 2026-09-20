# auth · T10 — Phase 7: Self-Review

Reviewing the Phase 6 diff (`VerificationTokenServiceTest.java`,
`AccountExceptionHandlerTest.java`) against the frozen brief and `agents.md`. No rewriting —
findings only, fixes are Phase 9's job.

---

### Finding 1 — The new cross-surface comparison test is mechanically identical to its existing sibling

**Severity:** Medium

**Issue:** `onVerificationTokenRejectedResponseIsIdenticalForVerifyEmailAndPasswordResetSurfaces`
was added to make AC4 (invalid-verification-token and invalid-reset-token responses are identical)
"directly proven rather than inferred" (frozen brief Finding 1/5's resolution). But
`AccountService.VerificationTokenRejectedException` has a single no-arg constructor with no field
distinguishing which call site (or which "surface") produced it — `new
VerificationTokenRejectedException()` is indistinguishable from any other instance of itself. As a
result, this new test's body — construct two default instances, pass each through
`handler.onVerificationTokenRejected(...)`, assert the two `ProblemDetail`s match — is
computationally identical to the pre-existing
`onVerificationTokenRejectedResponseIsIdenticalRegardlessOfConstructionSite`, which does the exact
same thing. Neither test can distinguish "two instances from the same surface" from "two instances
from different surfaces," because the exception type carries no origin information at all. The new
test is not incorrect and does pass, but it has effectively zero incremental mutation-testing
value over its sibling — any regression that would fail one would fail the other identically.

**Evidence:**
- `AccountService.java` — `VerificationTokenRejectedException()` no-arg constructor, no
  distinguishing field or parameter.
- `AccountExceptionHandlerTest.java` — the two tests' bodies are structurally identical
  (`new VerificationTokenRejectedException()` twice, `handler.onVerificationTokenRejected(...)`
  twice, four field-equality assertions), differing only in variable names and comments.

**Recommendation:** Not necessarily a defect to fix — the test's real value is as *named
documentation* of a design guarantee (R5 and R15 deliberately share one exception type specifically
so no code path can diverge the two surfaces' responses), which is worth having on record even
though it can't currently fail independently of its sibling. Flagging for Phase 9 to decide: keep
as intentional "insurance" documentation (recommended — it costs nothing and clearly states an
intent the sibling test's comment doesn't), or note explicitly in the test's own comment that its
current inability to diverge from the sibling is itself the point, not an oversight.

---

### Finding 2 — The "not found" case relies on implicit Mockito default behavior, inconsistent with its named sibling's explicit style

**Severity:** Low

**Issue:** In the new
`shouldNotRevealAccountExistenceForInvalidVerificationTokenViaConsumeForPurpose` test, the
"not found" case (`consumeForPurpose("nonexistent-verify-token", emailVerify)`) does not stub
`tokenRepository.findByTokenHash(...)` at all — it relies on Mockito's built-in default answer for
`Optional`-returning methods (`Optional.empty()`, not `null`). This is correct and already proven
by the passing test run, but it's inconsistent with the named test this one is explicitly written
to echo (`shouldNotRevealAccountExistenceForInvalidVerificationToken`, line 93), which explicitly
stubs `when(tokenRepository.findByTokenHash(any())).thenReturn(Optional.empty())` for its own
not-found case.

**Evidence:**
- `VerificationTokenServiceTest.java`, new test's "Not found" case — no `findByTokenHash` stub.
- `VerificationTokenServiceTest.java:95` — the sibling test's not-found case, explicit stub.

**Recommendation:** Optional stylistic alignment — add the explicit stub to match the sibling's
style, purely for a future reader's clarity (relying on an unstated Mockito default is a step
harder to verify at a glance than an explicit `thenReturn`). Not a correctness issue.

---

## Areas reviewed with no findings

- **Correctness / boundary conditions:** all five new cases in the `VerificationTokenServiceTest`
  test correctly mirror the exact stubbing shape of their `PASSWORD_RESET`-side siblings
  (`consumeForPurpose`'s pre-check-before-mutation guarantee for account-level reasons; the
  DB-level `markConsumed` filter for token-level reasons) — verified against
  `VerificationTokenService.consumeForPurpose`'s actual control flow, not assumed.
- **Null-safety:** no new null-handling paths; `consumeForPurpose` itself is unchanged
  (`Objects.requireNonNull` guards on `rawToken`/`purpose`, both always non-null in every new test
  case).
- **Thread-safety:** stateless tests, no new mutable shared state.
- **Transaction boundaries:** unaffected — no production code touched.
- **Module boundaries:** both changed files stay within `account`/`account` test package; no new
  imports beyond what each file already had.
- **Idempotency:** not applicable — test-only, no persisted state.
- **Money types:** not applicable.
- **Enumeration-safety/secret-handling:** both new tests exist specifically to strengthen proof of
  enumeration-safety (R5/R15/L5/AC4); neither introduces any new leak — no raw token or account
  detail is logged or asserted into a failure message.
- **Readability/complexity:** the new `VerificationTokenServiceTest` test (45 lines, 5 cases) is
  within this module's established style — comparable in length/shape to its own named sibling (58
  lines). The new `AccountExceptionHandlerTest` test is short and clear.

## Specification references

- Task: `spec/auth-service/tasks.md`, task 10.
- Requirements: R5 (AC2a/AC2b), R15 (unaffected, regression-only), L5 (AC4 — see Finding 1's nuance
  on what "directly proven" actually means given the exception type's design).
