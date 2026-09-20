# auth · T13 — Phase 5: Implementation Plan

Plans the frozen brief (`04-frozen-task-brief.md`). No code below — signatures and call-order only.

## Gap the frozen brief itself left open (flagged, not silently resolved)

The frozen brief's Constraints section requires `isCurrentlyLocked` to be "read-only, no row
lock" — but `LockoutService`'s only existing read method,
`LockoutStateRepository.findByAccountUuidForUpdate`, always takes `FOR UPDATE OF ls`. Satisfying
the brief's own explicit constraint requires one new plain-read query method on
`LockoutStateRepository` — a file the brief's Files to Modify list omitted (Phase 4's own
oversight, not a new scope decision). This plan adds it as the minimal, necessary, non-speculative
consequence of a change the brief already authorized (`LockoutService.isCurrentlyLocked`) — not as
independently-introduced scope. Flagged here rather than either silently expanding the file list
without comment or silently reusing the wrong (locking) query to avoid touching it.

## Files to create

1. `services/auth/src/main/java/com/themistra/auth/authn/LoginFailureHandler.java`
2. `services/auth/src/main/java/com/themistra/auth/authn/LoginSuccessHandler.java`
3. `services/auth/src/test/java/com/themistra/auth/authn/LoginFailureHandlerTest.java`
4. `services/auth/src/test/java/com/themistra/auth/authn/LoginSuccessHandlerTest.java`
5. `services/auth/src/test/java/com/themistra/auth/authn/SasLoginIntegrationTest.java`

1-2 trace to the frozen brief's Files to Create; 3-5 to its Required Tests section.

## Files to modify

1. `services/auth/src/main/java/com/themistra/auth/authn/LockoutService.java` — add
   `isCurrentlyLocked`.
2. `services/auth/src/main/java/com/themistra/auth/authn/LockoutStateRepository.java` — add one
   plain-read query method (see Gap note above). Not in the frozen brief's list; included as a
   necessary consequence of item 1.
3. `services/auth/src/main/java/com/themistra/auth/authn/AccountUserDetailsService.java` — new
   constructor dependencies, `accountLocked` computation changed.
4. `services/auth/src/main/java/com/themistra/auth/token/SecurityChainsConfig.java` — import fix +
   handler wiring.
5. `services/auth/src/main/java/com/themistra/auth/token/ReuseDetectingAuthorizationService.java`
   — import fix only.
6. `services/auth/src/test/java/com/themistra/auth/authn/AccountUserDetailsServiceTest.java`
   (existing file, confirmed) — new constructor args, `lockedMapsToAccountLocked` split into two
   cases (still-locked vs. expired-but-not-yet-flipped).
7. `services/auth/src/main/resources/application.properties` — none expected (no new config keys
   this task introduces), listed here only to confirm it was checked, not silently skipped.

All eight authorized by the frozen brief's Files to Modify list (items 2, 6-7 are the
plan-level consequences/clarifications noted above and in each item).

## Public methods (signatures)

**`LockoutService.java`** (new method, alongside the three existing T12 methods):
- `@Transactional(readOnly = true) public boolean isCurrentlyLocked(UUID accountUuid, Instant now)`
  — `Objects.requireNonNull` both params; loads via the new plain-read repository method; `true`
  iff a row exists and `now.isBefore(row.getLockedUntil())`; `false` for a missing row or a row
  whose `lockedUntil` is null/at-or-before `now` (Finding 7 — matches the existing
  `LockoutStateMachine.evaluate`'s own `blocked` boundary convention: strictly-before is the
  blocking condition, at-or-after is permitted).

**`LockoutStateRepository.java`** (new method):
- `@Query(value = "SELECT ls.* FROM lockout_state ls JOIN accounts a ON a.id = ls.account_id "
  + "WHERE a.account_uuid = :accountUuid", nativeQuery = true)
  Optional<LockoutState> findByAccountUuid(@Param("accountUuid") UUID accountUuid)` — same shape
  as the existing `findByAccountUuidForUpdate`, minus `FOR UPDATE OF ls`.

**`LoginFailureHandler.java`** (new, `authn` package, `@Component`, extends
`SimpleUrlAuthenticationFailureHandler`):
- `public LoginFailureHandler(AccountService accountService, LockoutService lockoutService,
  AuditService auditService, Clock clock)` — constructor calls `super("/login?error")` (the
  redirect target `.formLogin(Customizer.withDefaults())` already uses by default — preserving it
  exactly, not introducing a new one, satisfies AC9/L5 by construction rather than by careful
  case-by-case checking).
- `@Override public void onAuthenticationFailure(HttpServletRequest request,
  HttpServletResponse response, AuthenticationException exception) throws IOException,
  ServletException` — calls the private recording logic below, then unconditionally
  `super.onAuthenticationFailure(request, response, exception)` — the redirect behavior is
  never touched, never branches on `exception`'s concrete type (AC9 by construction).

**`LoginSuccessHandler.java`** (new, `authn` package, `@Component`, extends
`SavedRequestAwareAuthenticationSuccessHandler` — the exact class
`.formLogin(Customizer.withDefaults())` uses internally by default, confirmed present in the
resolved `spring-security-web` jar, so extending it preserves the current default behavior
exactly):
- `public LoginSuccessHandler(LockoutService lockoutService, Clock clock)`
- `@Override public void onAuthenticationSuccess(HttpServletRequest request,
  HttpServletResponse response, Authentication authentication) throws IOException,
  ServletException` — resolves `UUID.fromString(authentication.getName())` (the principal name is
  already the account UUID, per `AccountUserDetailsService`'s existing behavior), calls
  `lockoutService.recordSuccessfulAttempt(uuid, clock.instant())`, then unconditionally
  `super.onAuthenticationSuccess(...)`.

**`AccountUserDetailsService.java`** (existing, modified):
- Constructor gains `LockoutService lockoutService, Clock clock` (currently just
  `AccountService accountService`).
- `loadUserByUsername` unchanged in shape; `accountLocked(...)` argument changes from
  `view.status() == AccountStatus.LOCKED` to
  `view.status() == AccountStatus.LOCKED && lockoutService.isCurrentlyLocked(view.accountUuid(),
  clock.instant())`.

**`SecurityChainsConfig.java`** (existing, modified):
- Line 12 import corrected: `org.springframework.security.oauth2.server.resource.authentication
  .JwtAuthenticationConverter`.
- `applicationChain(HttpSecurity http, JwtAuthenticationConverter jwtAuthenticationConverter,
  LoginFailureHandler loginFailureHandler, LoginSuccessHandler loginSuccessHandler)` — two new
  `@Bean`-injected parameters; `.formLogin(Customizer.withDefaults())` becomes
  `.formLogin(form -> form.failureHandler(loginFailureHandler).successHandler(loginSuccessHandler))`.

**`ReuseDetectingAuthorizationService.java`** (existing, modified):
- Line 10 import corrected: `org.springframework.security.oauth2.server.authorization
  .OAuth2TokenType`. No other change — confirmed by the frozen brief's own Files NOT to Modify
  scoping (this file's logic is untouched).

## Private methods

**`LoginFailureHandler.java`:**
- `private void recordFailure(String email, Instant now)` — `email == null` (defensive; the form
  field should always be present, but Spring doesn't guarantee it) or
  `accountService.findLoginView(email).isEmpty()` → `auditFailure(null, null)`; otherwise resolve
  `LoginView`, call `auditFailure(accountUuid, accountUuid)`, and call
  `lockoutService.recordFailedAttempt(accountUuid, now)` only if `isLockoutEligible(view, now)`.
- `private boolean isLockoutEligible(LoginView view, Instant now)` — `view.status() ==
  AccountStatus.ACTIVE || (view.status() == AccountStatus.LOCKED &&
  !lockoutService.isCurrentlyLocked(view.accountUuid(), now))` (Findings 1/5).
- `private void auditFailure(UUID accountUuid, UUID actorUuid)` — builds the locked
  `RecordAuditEventRequest` shape from Finding 2: `eventType="login.failed"`,
  `outcome=AuditOutcome.FAILURE`, the two UUID params, `ip=request.getRemoteAddr()`,
  `rawUserAgent=request.getHeader("User-Agent")` (this handler is the first `AuditService` call
  site in the codebase with direct `HttpServletRequest` access — every existing call site passes
  `null` for these two fields for lack of one; the frozen brief's Finding 2 disposition explicitly
  calls for capturing them "when available," which is now genuinely possible), `traceId=null` (no
  tracing/correlation-id mechanism exists anywhere in this codebase to capture from — confirmed by
  search at Phase 0/1; `null` is the correct "not available" value, not a shortcut), `details=Map.of()`.

## Entities used

None directly — `LockoutState` stays entirely inside `LockoutService`/`LockoutStateRepository`
(T12's existing boundary, unchanged). Neither new handler nor the modified
`AccountUserDetailsService` imports `Account` or `LockoutState` (L12).

## Repositories used

- `LockoutStateRepository` — one new plain-read method (see Gap note).
- No other repository touched. `AccountRepository`/`AuditEventRepository` are reached only through
  their owning services (`AccountService`/`AuditService`), unchanged pattern.

## Services used

- `AccountService.findLoginView(String)` (pre-existing, reused).
- `LockoutService.recordFailedAttempt` / `.recordSuccessfulAttempt` (T12) + `.isCurrentlyLocked`
  (new, this task).
- `AuditService.record(RecordAuditEventRequest)` (pre-existing, reused).

## Unit/integration tests required

**`LoginFailureHandlerTest.java`** — plain JUnit 5 + Mockito, mocking
`AccountService`/`LockoutService`/`AuditService`, a mocked `HttpServletRequest`/
`HttpServletResponse`/`AuthenticationException` (Mockito mocks, not a real servlet container —
matches this module's existing no-Spring-context unit convention):
- `shouldAppendRowAndMirrorAuditEventForLoginFailure` (named) — known `ACTIVE` account, asserts
  `auditService.record(...)` called with the exact Finding 2 shape.
- Unknown email → `verifyNoInteractions(lockoutService)`, audit call with both UUIDs `null` (AC3).
- Each non-eligible status (`PENDING_VERIFICATION`, `SUSPENDED`, `DELETED`, `LOCKED` with
  `isCurrentlyLocked=true`) → audit called, `lockoutService.recordFailedAttempt` never called
  (AC7).
- `LOCKED` with `isCurrentlyLocked=false` → `lockoutService.recordFailedAttempt` called (AC1,
  Finding 1's positive case).
- Every case above → `super.onAuthenticationFailure(...)`'s redirect behavior is exercised
  identically; since the handler never branches on `exception`, a single parameterized-style check
  (same handler method called with different `AuthenticationException` subclass instances) proves
  AC9 without needing to inspect response internals beyond confirming no exception-type branching
  exists in the reviewed source.

**`LoginSuccessHandlerTest.java`**:
- `shouldResetLockoutCounterOnSuccessfulLogin` (named) — `Authentication.getName()` returns a
  UUID string; asserts `lockoutService.recordSuccessfulAttempt(uuid, now)` called exactly once.
- Null/malformed principal name → decide whether this throws (defensive) or is unreachable in
  practice (since `AccountUserDetailsService` only ever sets a real UUID as username) — leaning
  unreachable/no explicit guard needed, confirmed at implementation time, not designed further
  here.

**`LockoutServiceTest.java`** (existing file, new tests added):
- `isCurrentlyLocked` boundary tests: missing row → `false` (AC8); `now` strictly before
  `lockedUntil` → `true`; `now` at/after `lockedUntil` → `false` — mirrors the exact boundary
  convention `LockoutStateMachineTest`/`LockoutServiceTest` already established for
  `recordFailedAttempt`'s blocked check.

**`AccountUserDetailsServiceTest.java`** (existing file, modified):
- `lockedMapsToAccountLocked` split into `stillLockedMapsToAccountLocked` (stub
  `isCurrentlyLocked=true`) and `expiredLockDoesNotMapToAccountLocked` (stub
  `isCurrentlyLocked=false`, AC5/AC6's direct proof at this layer).

**`SasLoginIntegrationTest.java`** (new, Testcontainers + `@SpringBootTest`) — the first test in
this module needing to exercise a real HTTP-level Spring Security filter chain; no `MockMvc`
precedent exists anywhere in this codebase (confirmed at Phase 0/1). Plan: use
`@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate` (avoids introducing `MockMvc`
as a new test-scope dependency if it isn't already on the classpath; `TestRestTemplate` ships with
`spring-boot-starter-test`, already present) to POST real form-login credentials against a real
running instance and assert: (a) a locked-but-expired account's login succeeds and unlocks; (b) a
still-locked account's login is rejected without touching `lockout_state`; (c) a bad password
against a known account increments the counter and creates an audit row; (d) an unknown email
produces the identical HTTP response shape as (c). This is the highest-risk new test in this
task — first-of-its-kind in this module, and (matching every prior task's own honest disclosure)
unexecutable in this sandbox regardless, since it requires Testcontainers/Docker.

## Execution order

1. `LockoutStateRepository.java` — add the plain-read method (no dependents yet).
2. `LockoutService.java` — add `isCurrentlyLocked`, depends on step 1.
3. `ReuseDetectingAuthorizationService.java` — one-line import fix, independent of everything else.
4. `AccountUserDetailsService.java` — depends on step 2 (new `LockoutService`/`Clock`
   dependencies).
5. `LoginFailureHandler.java`, `LoginSuccessHandler.java` — depend on step 2 (and
   `AccountService`/`AuditService`, both pre-existing and unchanged).
6. `SecurityChainsConfig.java` — import fix (independent) + wiring in the two new handlers from
   step 5 (depends on step 5 existing).
7. **First real build checkpoint**: `mvn -pl services/auth compile` (AC10) — after steps 1-6, this
   should be the first successful full compile in this task chain since T03. If it fails for any
   reason beyond the two anticipated import lines, that's new information for Phase 6, not
   something this plan can predict.
8. `AccountUserDetailsServiceTest.java` — update for step 4.
9. `LockoutServiceTest.java` — new tests for step 2.
10. `LoginFailureHandlerTest.java`, `LoginSuccessHandlerTest.java` — new files, depend on step 5.
11. `SasLoginIntegrationTest.java` — depends on everything above; requires Docker to actually run
    (flagged as a known, likely-unexecutable-here risk, consistent with T12's own precedent).
12. `mvn -pl services/auth verify` — the real, full verification run this entire task chain has
    been unable to perform since T03. If AC10 held at step 7, this is where it's confirmed
    end-to-end, including whatever module-wide tests (not just this task's own) now run for the
    first time in months.

No new Flyway migration, no new config keys — confirmed at Phase 0/1, reconfirmed here.
