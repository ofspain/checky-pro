STATUS: FROZEN

# auth · T15 — Phase 4: Frozen Task Brief

Human Approval gate. Phase 3 (Kimi) raised 7 findings against the Phase 2 TIB. Each verified
against source before disposition (per this project's standing rule that adversarial findings are
checked, not trusted). One genuine judgment call was escalated and resolved by the human; the rest
were mechanical brief clarifications, folded in directly below.

## Disposition of Phase 3 findings

| # | Finding | Verified? | Disposition |
|---|---|---|---|
| 1 | Session stores the raw `AuthenticationException` (`saveException`), so redirect equality alone doesn't fully prove response indistinguishability | **Confirmed** — read `SimpleUrlAuthenticationFailureHandler` source (6.5.2): `saveException(HttpServletRequest, AuthenticationException)` is `protected final` (not the 3-arg signature Kimi cited; core claim still correct) and stores the exception object in session by default | **Human decision: redirect-only scope, documented residual risk.** No production code change. Matches T13 Phase 9's prior decision on this identical mechanism (unexploitable today — no login page renders `WebAttributes.AUTHENTICATION_EXCEPTION`). Recorded as a residual risk below, not fixed. |
| 2 | "Indistinguishable" narrowed to redirect URL without saying so; status/header/body unaddressed | Confirmed — brief only asserted redirect target | **Accepted.** Folded into Constraints: redirect URL is the only assertion; status/header/body assertions are explicitly out of scope because `HttpServletResponse` is a Mockito mock here — `sendRedirect(...)` doesn't produce a real status code to assert on. |
| 3 | `PENDING_VERIFICATION` omitted, though it's `disabled` like `SUSPENDED` | Confirmed via `AccountUserDetailsService.java` — `PENDING_VERIFICATION` and `SUSPENDED` both set `.disabled(true)`, producing the identical `DisabledException` | **Rejected, with reasoning recorded (not silently dropped).** R21's own text enumerates exactly `LOCKED`/`SUSPENDED`/`DELETED`/non-existent — `PENDING_VERIFICATION` isn't named. Since it shares the exact same exception type and code path as `SUSPENDED`, adding it as a case would exercise a byte-identical branch and add no marginal proof. Documented as an explicit equivalence-class exclusion, not a silent gap. |
| 4 | AC1's "`LOCKED`" ambiguous — expired vs. still-locked interval | Confirmed via `AccountUserDetailsService.java:48-49` | **Accepted, clarified.** AC1 renamed to "still-locked `LOCKED`" (produces `LockedException`). The expired-lock branch is not a separate case — it produces `BadCredentialsException`, identical to the baseline case, so it's implicitly covered by the baseline rather than needing its own assertion. |
| 5 | Overlaps with existing `everyExceptionSubclassProducesTheSameRedirect` | Confirmed — that test exists and asserts the same redirect target for 4 exception types | **Accepted, clarified, not deduplicated.** Added a note distinguishing the two: the existing test proves handler-level exception-agnostic redirect behavior against a fixed `ACTIVE` stub; the new test proves the full production path (account status → exception → redirect) with realistic `findLoginView`/`isCurrentlyLocked` stubs per status. Different property, deliberately kept separate. |
| 6 | `package.md` maps the named test to R18, not R21 | Confirmed (already caught independently in Phase 1/2) | **Already resolved** — no new action; frozen brief continues to cite R21 as authoritative per this task's own header and `requirements.md`'s exact text. |
| 7 | Line-number citations are brittle | Valid | **Accepted.** Below, dependencies are cited by method name, not line number. |

## Scope (final)

**In:**
- One new unit test, `shouldReturnIndistinguishableResponseForLockedAndBadCredentials`
  (`package.md` §8 name, verbatim), added to `LoginFailureHandlerTest.java`.
- Five cases, each driving `LoginFailureHandler.onAuthenticationFailure` with the exception type
  production actually produces (verified against `AccountUserDetailsService.loadUserByUsername`):
  - Still-locked `LOCKED` → `LockedException`
  - `SUSPENDED` → `DisabledException`
  - `DELETED` → `UsernameNotFoundException` (via `AccountService.findLoginView`'s empty `Optional`
    — `DELETED` and non-existent are already the same code path in production)
  - Non-existent email → `UsernameNotFoundException`
  - Baseline: `ACTIVE` + wrong password → `BadCredentialsException` (also stands in for
    expired-lock `LOCKED`, which produces the same exception type)
- Captures `response.sendRedirect(...)`'s argument per case (`ArgumentCaptor<String>`, matching
  `everyExceptionSubclassProducesTheSameRedirect`'s established technique) and asserts all five
  captured values are equal to each other.

**Out:**
- No new integration/Testcontainers test (Docker unavailable in this sandbox; unit level already
  proves the property at the enforcement point).
- No production code changes, including no `saveException` mitigation (Finding 1's human decision
  above).
- `PENDING_VERIFICATION` is not a separate case (Finding 3's disposition above).
- No status-code/header/body assertions (Finding 2's disposition — meaningless against a mock).
- No changes to any file other than `LoginFailureHandlerTest.java`.

## Business Rules

- **R21.** Password authentication against `LOCKED`, `SUSPENDED`, `DELETED`, or a non-existent
  account fails with a response indistinguishable from bad credentials, revealing no account
  state.

## Locked Decisions

- **L5.** Enumeration-safe responses — this test is the login-specific proof, redirect-level only
  (per Finding 1's disposition; session-level uniformity is a documented residual risk, not
  proven here).

## Dependencies

- `LoginFailureHandler` (unmodified) — specifically `onAuthenticationFailure`.
- `AccountUserDetailsService.loadUserByUsername` — the source of truth for which exception type
  each status produces (referenced by method, not line number, per Finding 7).
- `AccountService.findLoginView` — confirms `DELETED` and non-existent share one code path.
- `LockedException`, `DisabledException`, `UsernameNotFoundException`, `BadCredentialsException`
  (`org.springframework.security.*`).
- Mockito `ArgumentCaptor<String>` on `HttpServletResponse.sendRedirect(String)`.

## Files to Create

None.

## Files to Modify

- `services/auth/src/test/java/com/themistra/auth/authn/LoginFailureHandlerTest.java` — add the
  one new test method.

## Files NOT to Modify

- Any file under `src/main/java` (including `LoginFailureHandler.java` — Finding 1's decision
  keeps this test-only).
- `SasLoginIntegrationTest.java`.
- Any other test file.

## Acceptance Criteria

- **AC1 (R21).** Still-locked `LOCKED` produces the same redirect target as the `ACTIVE`+bad-password
  baseline.
- **AC2 (R21).** `SUSPENDED` produces the same redirect target as the baseline.
- **AC3 (R21).** `DELETED` produces the same redirect target as the baseline.
- **AC4 (R21).** Non-existent email produces the same redirect target as the baseline.
- **AC5 (R21, L5).** All captured values (AC1-AC4 plus the baseline) are asserted equal to each
  other in one comparison, not four isolated single-case assertions.

## Required Tests

- `shouldReturnIndistinguishableResponseForLockedAndBadCredentials` (named test) — the only test
  this task adds.

## Constraints

- Assert on the redirect target only — the one thing a real client observes through this handler;
  no status/header/body assertions (mock limitation, Finding 2).
- No `Instant.now()`/wall-clock dependency needed — this test starts downstream of the exception
  already having been thrown, no lock-expiry arithmetic involved.
- No Spring context, no persistence, no module-boundary exposure (L12 not exercised).

## Residual risks (carried forward, not fixed by this task)

- **Session-stored exception message differs per case** (Finding 1). `SimpleUrlAuthenticationFailureHandler
  .saveException` (confirmed `protected final`) stores the raw `AuthenticationException` — a
  `LockedException`/`DisabledException`/`UsernameNotFoundException`/`BadCredentialsException` each
  carry different messages into the session. Currently unexploitable: no login page template exists
  that reads `WebAttributes.AUTHENTICATION_EXCEPTION`. Same conclusion T13 Phase 9 reached about
  this identical mechanism. Owner: whichever future task first builds a login page/error view must
  re-examine this before rendering that session attribute to a user.
- `contracts/api/auth.yaml` still doesn't exist (tracked since T11, unrelated to this task).

## Open Questions

None remaining — Finding 1 was the only genuine blocker and is resolved above.
