<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review) -->

# frontend · T29 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T29 — Uniform sign-in failure |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

## Scope

Reviewed `frontend/src/auth/identityErrorsIndistinguishableAcrossAccountStates.test.tsx` against `spec/frontend/tasks.md` task 29, `requirements.md` R10/R30, `design.md` L4, and `agents.md` §Enumeration safety.

## Summary

The file is an unusually thorough verification-only deliverable: 53 cases, 32 controls, 22 mutants killed, and a clear mapping to acceptance criteria. The dominant risks (vacuous fixtures, blind struct comparison, `+`-encoded query leaks, fake-clock tautology, comment-stripper regression, and allowlist slack) are all instrumented and dirty-proven.

The recommendations below are real but narrow: three are cross-task boundary issues that T29 correctly declares as delegated (T28, T59, T134) but that remain uncovered for R10/R30 as a whole; the rest are defense-in-depth or future-proofing gaps inside T29's own scope.

## Recommendations

**Gap: R10's privileged-account refusal copy is not exercised here.**
· Why it matters: R10 explicitly covers a MERCHANT or ADMIN account without confirmed enrollment, which SAS refuses inside its hosted `/login` flow, not at the SPA callback. The test file only drives hostile `error=` callbacks to `/app/callback`. Task 28 owns `sasRefusalForUnenrolledPrivilegedAccountShowsWrongPasswordCopy`, but until it lands, R10 has no executable evidence in the frontend unit suite.
· Suggested test: Either confirm task 28's file is the authoritative home for this clause, or add a Playwright/e2e case (task 134, `oidcRoundTripEndToEnd`) that drives an unenrolled MERCHANT/ADMIN through SAS `/login` and asserts the rendered failure copy equals `signIn.discarded`.

**Gap: R30's token-state half is absent from this file.**
· Why it matters: R30 requires identical copy/redirect/treatment across bad, expired, and already-used tokens as well as account states. This file tests only the five account states. `tasks.md` assigns token states to task 59 (`identityErrorsIdenticalAcrossTokenStates`), and Phase 10 records this as T29-D2, but R30 cannot be considered discharged by T29 alone.
· Suggested test: In task 59's test file, drive three callback arrivals that carry token-failure OAuth errors (e.g., `invalid_grant`, `invalid_token`, `access_denied`) and assert they produce the same `Outcome` struct, the same `signIn.discarded` copy, and the same clock-zero behavior as the five account-state failures.

**Gap: Focus management after the discard is not asserted.**
· Why it matters: `agents.md` and L11 require keyboard operation and managed focus across every screen. A state-dependent focus target or focus delay would be a detectable difference even if copy and redirect are identical. The current tests read live-region text but never assert where focus lands after `REPLACE` navigation to `/app/sign-in`.
· Suggested test: After each of the five arrivals, assert `document.activeElement` is the same element (e.g., the live region or the sign-in heading) and that it receives focus synchronously with the redirect; add a control showing that a different focus target causes the assertion to fail.

**Gap: Live-region attributes are not compared across states.**
· Why it matters: R30's "visual treatment" and L11's live-region rule are about more than text content. A state-specific `role="alert"` vs `role="status"` or `aria-live="assertive"` vs `"polite"` would change the screen-reader announcement timing and tone, leaking cause information. The test selects by any of those attributes but does not assert their exact shape.
· Suggested test: Extend the `Outcome` struct (or add a dedicated assertion) to record the matched element's `role` and `aria-live` values, and assert the pair is identical for all five states.

**Gap: DOM structural equivalence is only partially covered.**
· Why it matters: AC4 currently pins only the absence of `class` and `style` attributes. Task 173 will introduce a design system, at which point "identical visual treatment" will include element roles, tags, and nesting. A future implementation could render a cause-specific icon, section, or heading level without using a class or inline style.
· Suggested test: Snapshot or compare the rendered DOM structure (tag names, roles, and ARIA attributes, ignoring text content) across the five failure states, and assert it is byte-identical; include a control where an extra `div` or different heading level fails.

**Gap: The AC9 gating scan misses destructured `state`/`status` comparisons.**
· Why it matters: The regex requires a receiver (`account.state`, `user.status`, etc.). If production ever refactors to `const { state } = account; if (state === 'LOCKED') …`, the receiver disappears and the scan stays green while a state gate exists.
· Suggested test: Add a synthetic control that destructures `account.state` or `account.status` into a local variable and compares it; assert `accountStateGates()` reports the violation.

**Gap: No test exercises a callback that carries both `code` and `error`.**
· Why it matters: R8/R30 require the callback to discard on any error. A malicious or buggy identity provider could send a valid-looking `code` alongside `error=access_denied`; the SPA must still discard and must not exchange the code. The existing "never exchanges a code" case uses error-only callbacks.
· Suggested test: Arrive with `?code=VALID&error=access_denied&error_description=…&state=…` and assert `fetchSpy` is not called, the token is not held, and the outcome is the same uniform discard.

**Gap: Minimal OAuth error responses are not exercised.**
· Why it matters: RFC 6749 §4.1.2.1 makes `error_description` and `error_uri` optional. SAS today sends them, but a future backend change or proxy could strip them. The SPA must still discard uniformly and must not expose a missing-description state as a different user-visible outcome.
· Suggested test: Arrive with `?error=access_denied&state=…` only (no description, no URI) for each of the five fixtures' underlying `error` values, and assert the same `/app/sign-in` redirect with `signIn.discarded`.

**Gap: The whole-suite environmental failure is not isolated from this file's pass/fail signal.**
· Why it matters: Phase 10 reports 1395 passed / 1 failed, with the single failure being `lintRejectsHandWrittenBackendFetch` (pre-existing/environmental). A reviewer running the suite fresh will see a failing test and cannot immediately tell whether T29 regressed.
· Suggested test: No new product test is needed, but the CI/local runner should surface the environmental failure separately or document it in the test manifest so the 1/1396 failure is not mistaken for a T29 defect.

---

**Phase 11 complete.** Eight recommendations returned; no production or test code rewritten.
