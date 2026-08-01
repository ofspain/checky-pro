# auth · T13 — Phase 6: Implementation Notes

Implements the frozen brief (`04-frozen-task-brief.md`) exactly per the plan
(`05-implementation-plan.md`). No test files touched in this phase (Phase 10's job) — production
code only, as planned.

## Changes

### `LockoutStateRepository.java` — `findByAccountUuid` (new)

Plain-read native query, identical shape to `findByAccountUuidForUpdate` minus `FOR UPDATE OF ls`
— the Phase 5 gap-fix (a method the frozen brief's Files to Modify list omitted, but its own
Constraints section required).

### `LockoutService.java` — `isCurrentlyLocked(UUID, Instant)` (new)

`@Transactional(readOnly = true)`. `repository.findByAccountUuid(accountUuid)
.map(LockoutState::getLockedUntil).map(now::isBefore).orElse(false)` — a missing row, or a row
with a null `lockedUntil`, both fall through to `false` via the same `Optional` chain (Finding 7).
No new imports beyond what the class already had.

Maps to: frozen brief AC8, Finding 7.

### `ReuseDetectingAuthorizationService.java` — import fix only

`org.springframework.security.oauth2.core.OAuth2TokenType` → `org.springframework.security.oauth2
.server.authorization.OAuth2TokenType`. One line, reordered alphabetically with the file's other
`org.springframework.security.oauth2.server.authorization.*` imports. No other change — confirmed
by diff: only the import line moved/corrected.

### `AccountUserDetailsService.java` — `accountLocked` now derived from `LockoutService`

Constructor gains `LockoutService lockoutService, Clock clock`. `loadUserByUsername` computes
`stillLocked = view.status() == AccountStatus.LOCKED && lockoutService.isCurrentlyLocked(view
.accountUuid(), clock.instant())` and passes that (not the raw status comparison) to
`.accountLocked(...)`. Class Javadoc rewritten to explain why — the pre-authentication-gate/R18
chicken-and-egg problem this task's own Phase 0/2 research surfaced.

Maps to: frozen brief AC5 (the core fix), AC6 (regression guard — still-locked accounts remain
rejected, since `isCurrentlyLocked` still returns `true` for them).

### `LoginFailureHandler.java` (new)

Extends `SimpleUrlAuthenticationFailureHandler`, constructed with the exact same `"/login?error"`
target the prior default configuration used — the redirect behavior is inherited, never
reimplemented, and `onAuthenticationFailure` never inspects the `AuthenticationException`
parameter's concrete type (it's passed straight to `super` unconditionally) — satisfying AC9 by
construction rather than by case-by-case checking.

`recordFailure` resolves the submitted `username` request parameter via
`accountService.findLoginView(email)`:
- Absent (null username or unknown email) → `auditFailure(request, null, null)` only.
- Present → `isLockoutEligible` gates the `LockoutService.recordFailedAttempt` call to `ACTIVE`,
  or `LOCKED` with `!isCurrentlyLocked` (Finding 1/5); `auditFailure` always fires with the real
  `accountUuid` as both target and actor (self-initiated action, matching every other
  self-service audit call in this codebase).

`auditFailure` builds the Finding 2 locked shape exactly: `eventType="login.failed"`,
`outcome=FAILURE`, both UUID params, `ip=request.getRemoteAddr()`,
`rawUserAgent=request.getHeader("User-Agent")` — this is the first `AuditService` call site in the
codebase with real `HttpServletRequest` access, so it's also the first to actually populate these
two fields (every prior call site passes `null` for lack of one) — `traceId=null` (no
tracing/correlation mechanism exists anywhere in this codebase to draw one from), `details=Map.of()`.

Maps to: frozen brief AC1, AC2, AC3, AC7, AC9.

### `LoginSuccessHandler.java` (new)

Extends `SavedRequestAwareAuthenticationSuccessHandler` — the exact class
`.formLogin(Customizer.withDefaults())` used internally by default, confirmed present in the
resolved `spring-security-web` jar — so existing redirect/saved-request behavior is unchanged.
`onAuthenticationSuccess` resolves `UUID.fromString(authentication.getName())` (already the
account UUID, per `AccountUserDetailsService`'s established behavior) and calls
`lockoutService.recordSuccessfulAttempt(accountUuid, clock.instant())` before delegating to
`super`.

Maps to: frozen brief AC4.

### `SecurityChainsConfig.java` — import fix + handler wiring

`org.springframework.security.oauth2.jwt.JwtAuthenticationConverter` →
`org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter`.
`applicationChain` gains two new `@Bean`-injected parameters (`LoginFailureHandler`,
`LoginSuccessHandler`); `.formLogin(Customizer.withDefaults())` becomes
`.formLogin(form -> form.failureHandler(loginFailureHandler).successHandler(loginSuccessHandler))`.
No other line in this file changed.

Maps to: frozen brief AC10 (the import half), Files to Modify.

## Acceptance criteria — implementation status

| ID | Status |
|---|---|
| AC1 | Done — `isLockoutEligible` gates `recordFailedAttempt` to `ACTIVE`/expired-`LOCKED` |
| AC2 | Done — `auditFailure`'s locked `RecordAuditEventRequest` shape |
| AC3 | Done — unknown/null username short-circuits to audit-only, `accountUuid=null` |
| AC4 | Done — `LoginSuccessHandler.onAuthenticationSuccess` |
| AC5 | Done — `AccountUserDetailsService`'s `stillLocked` computation via `isCurrentlyLocked` |
| AC6 | Done — `isCurrentlyLocked` still returns `true` while `now < locked_until`, unchanged rejection |
| AC7 | Done — every non-eligible status falls through `isLockoutEligible` to audit-only |
| AC8 | Done — `isCurrentlyLocked`'s `Optional` chain resolves a missing row to `false` |
| AC9 | Done by construction — neither handler branches on exception/response type |
| AC10 | **Done, verified this phase** — `mvn -pl services/auth compile` succeeds with zero errors, confirmed by direct run. First successful full main-source compile since T03. |

## Deviations from the plan — flagged, not hidden

**Two additional test files break on the import fix, not anticipated by Phase 5's plan.**
`mvn -pl services/auth test-compile` (run this phase purely to see what surfaces, not required to
pass) shows, beyond the expected `AccountUserDetailsServiceTest.java` constructor-arity break:
- `ReuseDetectingAuthorizationServiceTest.java` — imports the same wrong
  `org.springframework.security.oauth2.core.OAuth2TokenType` the production file used to. Now that
  the production import is fixed, the test's own matching wrong import breaks. Directly downstream
  of this task's own human-approved change; the Phase 5 plan only anticipated the one test file
  for the class this task directly modifies (`AccountUserDetailsServiceTest`), not this one for a
  file this task also touches.
- `TokenClaimsCustomizerTest.java` — imports the **same wrong `OAuth2TokenType` package**, but in
  a class this task does not touch at all (`TokenClaimsCustomizer.java` is untouched). This
  compile error was **always latent** — `mvn test-compile` could never reach this file before,
  since the module-wide main-source break (fixed this phase) prevented test compilation from ever
  starting. It surfaces for the first time now, purely as a side effect of fixing the main-source
  break, not because of anything T13 changed in or near this file. Also shows unrelated
  `assertThat` overload-ambiguity errors in the same file (`IntPredicate` vs. `Predicate`) — a
  second, independent, equally-latent pre-existing issue, also newly visible for the first time.

None of these three test files are touched in this phase, per the guardrail (test authorship is
Phase 10's). All three are flagged here as necessary Phase 10 fixes; `TokenClaimsCustomizerTest`'s
two issues (the wrong import and the `assertThat` ambiguity) are **pre-existing and unrelated to
T13's own scope** — they happen to a file this task never touches — and Phase 10 should treat
fixing them as a minimal, out-of-band consequence of restoring full test-compilability, not as new
T13 scope, the same way the two production import fixes were scoped as a human-approved exception
rather than a design decision.

**`mvn -pl services/auth compile` (AC10's literal text) is fully satisfied — verified, zero
errors.** The broader goal of a clean `mvn -pl services/auth verify` is not yet reachable in this
phase; it depends on Phase 10 landing the three test-file fixes above.

## Build verification

```
mvn -pl services/auth compile
```
Zero output, zero errors — clean. Re-run twice to confirm (not a fluke/caching artifact).

```
mvn -pl services/auth test-compile
```
Fails with exactly the three pre-identified/newly-surfaced issues above, none of them touched in
this phase. Full output captured and summarized above for Phase 10's benefit.

## Specification references

- Task: `spec/auth-service/tasks.md`, task 13.
- Requirements: R16, R18, R43.
- LOCKED decisions: L4 (unchanged), L5 (response-identity verified by construction), L12
  (verified: no new `Account` import anywhere — confirmed by inspection of all five changed/new
  files).
- Frozen brief: `04-frozen-task-brief.md` — all Files to Create/Modify from the brief (plus the
  one Phase-5-flagged repository-method gap) covered; no file outside that list touched.
