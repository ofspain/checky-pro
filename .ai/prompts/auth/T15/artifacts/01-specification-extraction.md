# auth · T15 — Phase 1: Specification Extraction

## Business Rules

- **R21.** IF an account is `LOCKED`, `SUSPENDED`, `DELETED`, or does not exist, THEN password
  authentication SHALL fail with a response that is indistinguishable from bad credentials and
  SHALL reveal no account state. (`requirements.md:33`) — the sole requirement in scope.

## Locked Decisions

- **L5. Enumeration-safe responses.** Login, registration, password-reset request/confirm, and
  email verification return uniform responses that don't reveal whether an email exists or an
  account's locked/suspended/deleted state (`design.md:9`). T15's test proves the login slice of
  this decision specifically — registration/password-reset/verification are out of scope for this
  task.
- **L12 (background only, not exercised by this task).** Module boundaries — not touched; this is
  a test-only task with no new cross-module dependency.

## Files involved

**Read, not modified (production code under test):**
- `services/auth/src/main/java/com/themistra/auth/authn/LoginFailureHandler.java` (T13) — the
  mechanism being verified.
- `services/auth/src/main/java/com/themistra/auth/authn/AccountUserDetailsService.java` (T13) —
  produces the divergent `AuthenticationException` subclasses per status, ahead of the point
  `LoginFailureHandler` normalizes them.
- `services/auth/src/main/java/com/themistra/auth/account/AccountService.java` — `findLoginView`,
  whose `DELETED`-filter (`AccountService.java:339`) makes `DELETED` and non-existent the same
  code path already.

**Extended or created (this task's actual deliverable):**
- `services/auth/src/test/java/com/themistra/auth/authn/LoginFailureHandlerTest.java` (T13,
  existing) — candidate location for a unit-level version, per Phase 0's finding that this file
  already has the nearest-matching technique (`everyExceptionSubclassProducesTheSameRedirect`).
- `services/auth/src/test/java/com/themistra/auth/token/SasLoginIntegrationTest.java` (T13,
  existing) — candidate location for an integration-level version.
- Exactly which file(s) receive the new test, and at which level, is a Phase 2 design decision —
  Phase 0 surfaced this as unresolved, not decided here per this phase's own scope (extraction,
  not design).

## Dependencies

- `AccountService.findLoginView(String)` — returns `Optional<AccountLoginView>`, empty for
  unknown/`DELETED`.
- `AccountUserDetailsService.loadUserByUsername(String)` — throws `LockedException`,
  `DisabledException`, or `UsernameNotFoundException` depending on status.
- `LoginFailureHandler.onAuthenticationFailure(HttpServletRequest, HttpServletResponse,
  AuthenticationException)` — the single normalization point; `SimpleUrlAuthenticationFailureHandler`
  parent behavior (redirect target, no exception detail in the response) is what must be proven
  identical across cases.
- No new config keys, entities, or contracts. No `contracts/` schema governs an HTTP redirect
  response shape — `contracts/api/auth.yaml` remains empty (`.gitkeep` only), confirmed again this
  phase, unrelated to this task's scope.

## Acceptance Criteria

- **AC1 (→ R21).** A password attempt against a `LOCKED` account produces the same response
  (status + redirect target, or body if HTTP-level) as a bad-credentials attempt against an
  `ACTIVE` account.
- **AC2 (→ R21).** Same, for `SUSPENDED`.
- **AC3 (→ R21).** Same, for `DELETED`.
- **AC4 (→ R21).** Same, for a non-existent email.
- **AC5 (→ R21, L5).** The test asserts response *equality* across all four cases against each
  other (or against a shared baseline), not merely that each individually "looks like a normal
  failure" — the requirement's claim is indistinguishability between cases, which requires a
  comparison, not four isolated assertions.

## Tests required

- **Named test (`package.md` §8):** `shouldReturnIndistinguishableResponseForLockedAndBadCredentials`
  — package.md maps it to R18; confirmed drift (same pattern as every prior task, T09/T11-T14): the
  actual match is R21, per this task's own header and `requirements.md`'s exact text.
- **Boundary implied by AC1-AC5 but not named explicitly:** the non-existent-email case, which
  R21's text calls out by name alongside the three statuses — the named test's own title doesn't
  mention it, but R21 does, so it's in scope regardless.

## Open Questions

- None that block Phase 2. Phase 0's unit-vs-integration-level question is a design choice, not a
  specification ambiguity — nothing in `requirements.md`/`design.md`/`package.md` mandates either,
  so it belongs in Phase 2 (Implementation Brief), not here.
