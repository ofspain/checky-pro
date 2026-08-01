# auth · T13 — Phase 10: Test Generation

Test manifest against the frozen brief (`04-frozen-task-brief.md`) and the Phase 9-resolved
implementation. No production code touched beyond the mechanical fixes already scoped and
approved at Phase 9 (two production import lines) — this phase's own two additional test-file
import/type-inference fixes are the same class of already-approved, minimal, out-of-band
consequence, not new scope (see below).

## Files

**New:**
- `services/auth/src/test/java/com/themistra/auth/authn/LoginFailureHandlerTest.java` (11 tests)
- `services/auth/src/test/java/com/themistra/auth/authn/LoginSuccessHandlerTest.java` (3 tests)
- `services/auth/src/test/java/com/themistra/auth/authn/SasLoginIntegrationTest.java` (4 tests,
  Testcontainers — unexecuted in this environment, see below)

**Modified:**
- `services/auth/src/test/java/com/themistra/auth/authn/AccountUserDetailsServiceTest.java` —
  constructor updated for the new `LockoutService`/`Clock` dependencies; `lockedMapsToAccountLocked`
  split into `stillLockedMapsToAccountLocked` (AC6) and `expiredLockDoesNotMapToAccountLocked`
  (AC5, the core fix).
- `services/auth/src/test/java/com/themistra/auth/authn/LockoutServiceTest.java` — 5 new tests for
  `isCurrentlyLocked` (missing row, null `lockedUntil`, strictly-before/at/after `lockedUntil`
  boundary, null-rejection).
- `services/auth/src/test/java/com/themistra/auth/token/ReuseDetectingAuthorizationServiceTest.java`
  — one-line import fix (already scheduled at Phase 6/9).
- `services/auth/src/test/java/com/themistra/auth/token/TokenClaimsCustomizerTest.java` — one-line
  import fix (already scheduled) **plus** three explicit generic type witnesses
  (`built.<List<String>>getClaim(...)`, `built.<String>getClaim(...)`) to resolve a separate,
  genuinely independent `assertThat` overload-resolution ambiguity this file had — verified by
  testing that fixing only the import did *not* resolve it. This ambiguity was **always latent**:
  `mvn test-compile` could never reach this file before this task fixed the module-wide
  main-source break, so it was never previously visible. Scoped identically to the import fix
  itself — a minimal, mechanical, out-of-band consequence of restoring test-compilability in a
  file T13 does not otherwise touch, not new T13 scope.

## Test → requirement / acceptance-criterion mapping

**`LoginFailureHandlerTest.java`:**

| Test | Maps to |
|---|---|
| `shouldAppendRowAndMirrorAuditEventForLoginFailure` | Named test / AC1, AC2 |
| `unknownEmailAuditsWithNullUuidsAndNeverCallsLockoutService` | AC3 |
| `nullUsernameParameterAuditsWithNullUuidsAndNeverCallsLockoutService` | AC3 boundary |
| `pendingVerificationAccountAuditsOnlyNeverCallsLockoutService` / `suspendedAccount...` / `deletedAccount...` | AC7 |
| `stillLockedAccountAuditsOnlyNeverCallsRecordFailedAttempt` | Finding 5, AC7 |
| `expiredLockAccountCallsRecordFailedAttempt` | AC1, T11 AC7 wiring |
| `everyExceptionSubclassProducesTheSameRedirect` | AC9 |
| `bookkeepingFailureDoesNotPreventTheRedirect` | Phase 9 Finding 1 |
| `lockoutFailureStillAllowsAuditToFireAndRedirectToProceed` | Phase 9 Finding 3 |

**`LoginSuccessHandlerTest.java`:**

| Test | Maps to |
|---|---|
| `shouldResetLockoutCounterOnSuccessfulLogin` | Named test / AC4 |
| `lockoutFailureDoesNotPreventLoginCompleting` | Phase 9 Finding 2 |
| `nonUuidPrincipalNameDoesNotPreventLoginCompleting` | Phase 9 Finding 8 |

**`LockoutServiceTest.java`** (new `isCurrentlyLocked` tests):

| Test | Maps to |
|---|---|
| `isCurrentlyLockedReturnsFalseForMissingRow` | AC8, Finding 7 |
| `isCurrentlyLockedReturnsFalseForNullLockedUntil` | Finding 7 |
| `isCurrentlyLockedReturnsTrueStrictlyBeforeLockedUntil` / `...FalseAtOrAfterLockedUntil` | Boundary, same convention as `recordFailedAttempt`'s blocked check |
| `isCurrentlyLockedRejectsNullAccountUuidOrNow` | Null-handling constraint |

**`AccountUserDetailsServiceTest.java`:**

| Test | Maps to |
|---|---|
| `stillLockedMapsToAccountLocked` | AC6 (regression guard) |
| `expiredLockDoesNotMapToAccountLocked` | AC5 (the core fix) |

**`SasLoginIntegrationTest.java`** (Testcontainers, real Postgres + real HTTP server):

| Test | Maps to |
|---|---|
| `expiredLockAccountCanSuccessfullyLoginAndUnlocks` | AC5, end-to-end |
| `stillLockedAccountCannotLoginEvenWithCorrectPassword` | AC6, end-to-end |
| `wrongPasswordAgainstKnownAccountIncrementsCounterAndAudits` | AC1, real filter-chain reachability |
| `unknownEmailProducesTheSameResponseShapeAsAKnownAccountFailure` | AC3/AC9, end-to-end |

## Coverage against the frozen brief's Required Tests list

Every bullet covered: both named tests; every eligible/non-eligible status branch (AC7); the
core-fix boundary (AC5/AC6) at both the unit (`AccountUserDetailsServiceTest`) and integration
(`SasLoginIntegrationTest`) layers; `isCurrentlyLocked` boundary tests; the exception-subclass
response-identity test (AC9) Phase 5 flagged as needing the module's first non-`MockMvc`
servlet-mock harness — built using plain Mockito mocks of `HttpServletRequest`/
`HttpServletResponse`/`HttpSession`, verified against the actual Spring Security 6.5.2 source
(`SimpleUrlAuthenticationFailureHandler`/`SavedRequestAwareAuthenticationSuccessHandler`/
`DefaultRedirectStrategy`/`HttpSessionRequestCache`) for what internally needs stubbing to avoid
NPEs from unstubbed mock defaults — not guessed.

## Build verification

**`mvn -pl services/auth compile` and `mvn -pl services/auth test-compile` both succeed with zero
errors** — confirmed by direct run. This is the first time the *entire* module (production and
test sources) has compiled cleanly since T03, across all thirteen tasks in this chain.

All non-Testcontainers tests across the whole module (309 tests, every package, not just this
task's own files) were then **executed** via the JUnit Platform Launcher, run from the module's
own working directory (`services/auth/`) to match Maven's convention (a relative-path contract
test elsewhere in the suite depends on it):

```
309 tests found, 303 successful, 6 failed
```

All four of T13's own new/modified test files (`LoginFailureHandlerTest`, `LoginSuccessHandlerTest`,
`AccountUserDetailsServiceTest`, `LockoutServiceTest`) pass 100%.

### The 6 failures — none in files T13 created or touches beyond an approved import line

Restoring full module compilability let the test suite run in its entirety for the first time,
surfacing six pre-existing, previously-unverifiable issues, all unrelated to login/lockout
tracking:

- **`TokenClaimsCustomizerTest`, 2 failures** — `NullPointerException: this.roleService is null`.
  Root cause verified: `private final TokenClaimsCustomizer customizer = new
  TokenClaimsCustomizer(roleService);` is a field initializer that runs during test-instance
  construction, before Mockito's `@Mock` field injection happens — `roleService` is `null` at the
  moment `customizer` captures it. A genuine test-authoring bug (the field needs to move into a
  `@BeforeEach` method), unrelated to the import fix already applied to this file.
- **`ReuseDetectingAuthorizationServiceTest`, `AdminAccountControllerTest`,
  `AdminAccountRoleControllerTest` — 1 failure each** — `UnnecessaryStubbingException` (Mockito
  strict stubs): a shared `@BeforeEach`/helper-method stub not consumed by every test that uses
  it. Same root-cause pattern across all three, in three unrelated packages (`token`, `account`,
  `authz`) — a pre-existing test-hygiene issue, not something T13's own scope touches.
- **`ArchitectureTest.only_the_account_module_may_touch_the_Account_entity` — 1 failure, the most
  significant of the six.** Reports 6 violations, all in `AccountResponse.from(Account)`
  (`account.dto` package) — a file T13 never touches. The rule is defined as
  `resideOutsideOfPackage("com.themistra.auth.account")` with no `..` suffix, meaning ArchUnit
  treats it as an *exact* package match — `com.themistra.auth.account.dto` counts as "outside"
  `com.themistra.auth.account` under this rule's literal definition, even though it's a
  subpackage. Whether this is a bug in the rule's own definition (missing `..`) or a genuine,
  long-standing violation of the module's own stated boundary is a real open question — but what's
  certain is that **this ArchUnit rule has never actually been checked before now**, since
  `ArchitectureTest` could never run until this task's import fixes made the module
  test-compilable. This is a significant, newly-surfaced finding worth its own follow-up outside
  T13's scope (fixing either the rule or `AccountResponse`'s construction pattern is unrelated to
  login/lockout tracking).

None of these six are fixed in this phase — fixing them would be scope creep far beyond a
login-failure/success-tracking task into five unrelated files across three unrelated packages,
one of them a whole-codebase architecture-enforcement question. Reported here in full because
restoring module-wide compilability is what made them visible at all, and hiding a discovery this
significant would contradict the "flag it, don't hide it" principle this entire pipeline runs on.

### `SasLoginIntegrationTest` — compiles clean, unexecuted

Requires Docker (Testcontainers), unavailable in this sandbox — same limitation as every prior
Testcontainers test in this pipeline (T12's `LockoutPersistenceIntegrationTest`, still also
unexecuted). Additionally uses hand-rolled CSRF-token scraping and session-cookie propagation
across `TestRestTemplate` calls (no `MockMvc` precedent in this module to fall back on, confirmed
at Phase 0/1) — verified compiling, but the actual HTTP mechanics have not been proven against a
running server. Flagged as a real, specific residual risk for whoever next has Docker access, not
assumed correct.

## Specification references

- Task: `spec/auth-service/tasks.md`, task 13.
- Requirements: R16, R18, R43.
- LOCKED decisions: L4, L5, L12.
- Frozen brief: `04-frozen-task-brief.md` — AC1-AC10, Required Tests.
- Review resolution: `09-review-resolution.md` — Findings 1, 2, 3, 8 (all now have dedicated
  bookkeeping-failure-isolation tests); Finding 4 (documented, not code-tested, per its own
  disposition); Findings 5, 6, 7 (this phase's own subject matter).
