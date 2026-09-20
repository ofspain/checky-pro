# auth · T15 — Phase 7: Self Review

Self-review of the one-file diff (`LoginFailureHandlerTest.java`, new method
`shouldReturnIndistinguishableResponseForLockedAndBadCredentials`, lines 198-224) against the
frozen brief and `agents.md`. No rewrite performed — findings only.

## Not applicable to this diff

- **Thread-safety, transaction boundaries, module boundaries, money types, idempotency** — plain
  JUnit + Mockito unit test, no Spring context, no persistence, no cross-module dependency, no
  monetary values involved. Stated explicitly rather than silently skipped.
- **Null-safety** — the null-`username` boundary is already covered by the existing
  `nullUsernameParameterAuditsWithNullUuidsAndNeverCallsLockoutService` test; frozen brief
  Constraints explicitly excludes it from this task's scope.
- **Secret-handling** — no credentials or secrets appear in this test; `"hash"` is a literal
  placeholder string matching every other test in this file, not a real hash.

## Findings

### Finding 1 — Inline comment implies a literal case that isn't actually stubbed

- **Issue:** The comment at line 222 ("also stands in for an expired-lock `LOCKED` account, which
  produces this same exception type") is correct reasoning but could read as claiming the baseline
  case directly exercises an expired-lock `LOCKED` `LoginView`. It doesn't — the baseline stubs
  `AccountStatus.ACTIVE`, not `LOCKED`. The equivalence relies on the reader independently knowing
  (from `AccountUserDetailsService.loadUserByUsername`) that an expired-lock `LOCKED` account also
  produces `BadCredentialsException`, making the exception-type match — not the account-status
  match — the thing that matters here.
- **Severity:** Low — informational/documentation clarity only, no behavioral gap. This exact
  disposition (baseline implicitly covers the expired-lock case) was explicitly decided at Phase 4
  (Finding 4), so the test's *coverage* is correct; only the comment's phrasing could mislead a
  future reader.
- **Evidence:** `LoginFailureHandlerTest.java:220-224`.
- **Recommendation:** Reword to something like "the exception type here (`BadCredentialsException`)
  is also what an expired-lock `LOCKED` account produces, per `AccountUserDetailsService` — not
  tested as a separate stubbed case, since it would exercise the identical branch."

### Finding 2 — Five sequential blocks instead of a data-driven loop

- **Issue:** Unlike `everyExceptionSubclassProducesTheSameRedirect`, which loops over an array,
  this test is five sequential inline blocks. This is longer (27 lines vs. that test's 15) for a
  similarly-shaped assertion.
- **Severity:** Low — a style observation, not a defect. A loop would need a small
  case-descriptor (status-or-empty, exception, whether to stub `isCurrentlyLocked`) since the
  `LOCKED` case needs an extra stub the other four don't — the asymmetry is real, not
  test-author convenience, so a loop would need a branch inside it anyway. Inline blocks keep each
  status→exception mapping visible at its own line, which is arguably the point being tested and
  benefits from staying visible rather than being folded into a table.
- **Evidence:** `LoginFailureHandlerTest.java:198-224` vs. `LoginFailureHandlerTest.java:177-196`.
- **Recommendation:** No change recommended — noting the trade-off for the record rather than
  proposing a rewrite that would reduce clarity for a one-time convenience.

## Confirmed consistent with the frozen brief (no new finding, verified not violated)

- The test does not assert on session state (`WebAttributes.AUTHENTICATION_EXCEPTION`) — correctly
  matches Phase 4's Finding 1 disposition (redirect-only scope, session-leak documented as residual
  risk, not tested).
- `PENDING_VERIFICATION` is not a stubbed case — correctly matches Phase 4's Finding 3 disposition
  (equivalence-class exclusion).
- No production code file was touched — correctly matches Files NOT to Modify.

## Specification references

- Frozen brief: `04-frozen-task-brief.md` — AC1-AC5, Constraints, Residual risks.
- Requirements: R21. LOCKED decisions: L5.
