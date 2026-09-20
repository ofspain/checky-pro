# auth · T15 — Phase 12: Specification Verification

Verifying the final test (`LoginFailureHandlerTest.shouldReturnIndistinguishableResponseForLockedAndBadCredentials`,
lines 202-237, plus the `invokeFailure` helper at line 239) against `spec/auth-service/requirements.md`,
`design.md`, and `tasks.md` for T15 only. Fresh re-verification this phase, not carried forward.

---

## Traceability matrix

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R21** — password authentication against `LOCKED`, `SUSPENDED`, `DELETED`, or a non-existent account fails with a response indistinguishable from bad credentials, revealing no account state | Yes (proof, not production behavior — `LoginFailureHandler` itself was already correct since T13) | `LoginFailureHandlerTest.java:202-237` — five cases (still-locked `LOCKED`, `SUSPENDED`, `DELETED`, non-existent, `ACTIVE`+bad-password baseline), each driven through the real `LoginFailureHandler.onAuthenticationFailure` with a fresh `MockHttpServletResponse` | `shouldReturnIndistinguishableResponseForLockedAndBadCredentials` (named) | No | No — strengthened beyond the original frozen-brief scope (see below), not narrowed |

**Named test (`package.md` §8):**
- `shouldReturnIndistinguishableResponseForLockedAndBadCredentials` — exists verbatim, maps to R18
  in `package.md`; confirmed drift (same pattern as every prior task in this chain, T09/T11-T14):
  the real match is R21, per this task's own header and `requirements.md`'s exact text.

---

## Beyond the frozen brief's original scope: what Phase 8/9/11's review actually changed

The frozen brief's original AC5 asserted only that the `sendRedirect` argument was equal across
all five cases. Two things this final version delivers go beyond that literal text, both
human-approved or independently re-derived through review, not silently added:

- **Real status-code and `Location`-header assertions** (Phase 11 Gap 2, human-approved) — the
  frozen brief's "redirect-target only" Constraint was originally justified by a stated "mock
  limitation." Kimi's Phase 11 review showed that limitation wasn't actually load-bearing: a real
  `MockHttpServletResponse` (already a `spring-test` transitive dependency, first use in this file)
  proves the same property against the actual HTTP-observable status code (302) and `Location`
  header, not just the method argument passed internally to `sendRedirect`. Verified:
  `LoginFailureHandlerTest.java:235-236`.
- **Comment precision fix** (self-review Finding 1 / independent review Finding 3, independently
  found twice, applied at Phase 9) — no behavioral effect, closes a comment that could have misled
  a future reader into thinking the baseline case directly stubbed an expired-lock `LOCKED`
  account.

---

## Principal-engineer assessment

**(1) Is the task fully complete?** Yes. The one named test the task requires exists, passes, and
proves R21's property at the unit level with real HTTP-observable assertions (status + Location),
not just an internal method-argument check.

**(2) Does it satisfy every acceptance criterion?** All five (AC1-AC5) from the frozen brief are
met, with AC5 strengthened beyond its original text via the human-approved Phase 11 fix — recorded
in the resolution trail (`09-review-resolution.md`, `10-test-generation.md`'s Phase 11 update), not
a silent deviation from what was approved. The frozen brief itself is unmodified per this
pipeline's rule that frozen briefs aren't amended after Phase 4.

**(3) Does it violate any LOCKED decision?** No. L5 is exactly what this test proves, at the login
slice. L12 verified clean this phase via a fresh import check: the test file imports only from
`com.themistra.auth.account` (DTOs, not entities) and `com.themistra.auth.audit` — no new
cross-module dependency, and no production code was touched at all this task.

**(4) Remaining risks:**
- **Session-stored exception message differs per case** (Phase 4 Finding 1, reaffirmed at Phase 9
  and Phase 11 Gap 4): `SimpleUrlAuthenticationFailureHandler.saveException` (confirmed `protected
  final`, unoverridable) stores the raw `AuthenticationException` in the session. Currently
  unexploitable — no login page template reads `WebAttributes.AUTHENTICATION_EXCEPTION` yet — but
  not tested or fixed here, per the human's explicit Phase 4 decision. Owner: whichever future task
  first builds a login page/error view must re-examine this before rendering that session
  attribute.
- **Status→exception mapping isn't independently regression-tested against Spring's real
  `DaoAuthenticationProvider`** (independent review Finding 1 / Phase 11 Gap 1): the test injects
  exception types directly rather than driving them through `AccountUserDetailsService` and a real
  `DaoAuthenticationProvider`. `AccountUserDetailsServiceTest` covers the `UserDetails` flags those
  exceptions derive from, but nothing currently asserts the flag→exception translation itself
  (Spring's own framework behavior). Closing this fully needs an integration or `MockMvc`-level
  test — still impractical in this sandbox: Testcontainers cannot complete its Docker handshake
  even with Docker now present (see `docker-testcontainers-handshake-issue` memory, diagnosed this
  task). Not this task's to fix.
- `contracts/api/auth.yaml` still does not exist anywhere in the repo (tracked since T11,
  unrelated to this task).
- `package.md` §11 Q2 (rate-limit thresholds) remains unresolved by the spec author — unrelated,
  unchanged.

---

## Verdict

**PASS** — R21 is fully proven by the named test at the unit level, strengthened during review to
assert real HTTP-observable status/header equality rather than only an internal method argument;
no LOCKED decision is violated; module boundaries are clean (fresh-verified, not carried forward);
`mvn -pl services/auth clean test-compile` succeeds with zero errors and the test file's own 11
tests (10 pre-existing + 1 new) all pass via a real `mvn test` run. No production code was touched
anywhere in this task.
