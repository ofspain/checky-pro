# auth · T13 — Phase 7: Self Review

Reviews `LoginFailureHandler.java`, `LoginSuccessHandler.java`, `AccountUserDetailsService.java`,
`LockoutService.java`'s `isCurrentlyLocked`, `LockoutStateRepository.java`'s `findByAccountUuid`,
`SecurityChainsConfig.java`, and `ReuseDetectingAuthorizationService.java` (Phase 6) against the
frozen brief (`04-frozen-task-brief.md`) and `agents.md`. No rewrite performed — findings only;
fixes are Phase 9's job.

---

## Finding 1 — The session-stored `AuthenticationException` could leak enumeration info through a future login page, even though the redirect response itself is identical

- **Issue:** `LoginFailureHandler` extends `SimpleUrlAuthenticationFailureHandler` constructed
  with a `defaultFailureUrl`. Verified against the actual Spring Security 6.5.2 source: when a
  `defaultFailureUrl` is set, `onAuthenticationFailure` unconditionally calls `saveException`,
  which stores the concrete `AuthenticationException` object (type and message) in the HTTP
  session under `WebAttributes.AUTHENTICATION_EXCEPTION`. The frozen brief's AC9/L5 constraint —
  and this handler's own Javadoc — correctly verify that the *redirect response* (status, Location
  header, body) is identical across every exception subclass. Neither addresses what happens if
  something later *reads* that session attribute: `LockedException`, `DisabledException`,
  `BadCredentialsException`, and `UsernameNotFoundException` all have different default messages
  and types, and a login page that displayed one differently from another would reintroduce
  exactly the enumeration signal AC9 was designed to close — through a side channel the redirect
  response itself never exposes.
- **Severity:** Low — currently latent, not exploitable today. No login page template exists
  anywhere in this codebase (confirmed at Phase 0: O4, "login page presentation," is still an
  unresolved `design.md` open decision — `.formLogin(Customizer.withDefaults())` uses Spring's
  built-in generated login page, which does not read or display this session attribute
  differently by exception type). This is a forward-looking risk for whoever eventually builds
  O4's custom login page, not a defect in what T13 ships.
- **Evidence:** `SimpleUrlAuthenticationFailureHandler` source (`spring-security-web-6.5.2`),
  `onAuthenticationFailure`'s Javadoc: "If redirecting or forwarding, `saveException` will be
  called to cache the exception for use in the target view." `LoginFailureHandler.java:46`
  (`super("/login?error")`, activating this code path).
- **Recommendation:** Not a fix-now item — same disposition category as the frozen brief's own
  Finding 6 (MFA step-up ordering): document as a forward-compatibility note for whoever implements
  O4, so the future login page template is built with this constraint in mind (never render the
  cached exception's type or message differently per case), rather than being discovered the hard
  way after a login page already ships.

---

## Non-findings (verified clean, including one hypothesis that turned out wrong)

- **Investigated and ruled out: NPE on a malformed login POST.** Hypothesized that a POST to
  `/login` omitting the `username` field entirely would reach `AccountService.findLoginView(null)`
  → `normalize(null)` → `null.trim()` → uncaught `NullPointerException`, bypassing the uniform
  failure response. Verified against the actual `UsernamePasswordAuthenticationFilter` source
  (6.5.2): `attemptAuthentication` already normalizes a null username to `""` before constructing
  the authentication token (`username = (username != null) ? username.trim() : "";`) — `""` never
  NPEs through `normalize`/`findByEmail`, it just correctly resolves to "no account found." This
  hypothesis would have been a false positive; ruled out by reading Spring's actual source rather
  than assuming framework behavior.
- **`auth_audit`/`AuditService` null-`accountUuid` handling:** verified the schema
  (`auth_audit.account_id`/`actor_uuid`, `V1__auth_baseline_schema.sql:129-130`) allows `NULL` for
  both, and `AuditService.partitionKey` already null-guards
  (`accountUuid != null ? accountUuid.toString() : UUID.randomUUID().toString()`). The unknown-email
  failure path's `auditFailure(request, null, null)` call is safe, not a latent constraint
  violation.
- **Module boundary (L12):** confirmed via inspection — no `com.themistra.auth.account.Account`
  import in any of the six changed/new files; only `AccountService`, `AccountStatus`, `LoginView`
  (none of them the entity itself).
- **Response-identity (AC9):** both handlers extend Spring's own default classes
  (`SimpleUrlAuthenticationFailureHandler`, `SavedRequestAwareAuthenticationSuccessHandler`) and
  never inspect the concrete exception/authentication type before delegating to `super` — verified
  by reading both handlers top to bottom; the only branching in `LoginFailureHandler` is on
  *resolved account status* for which internal calls fire, never on the HTTP response shape.
- **Core fix correctness (AC5/AC6):** traced the full sequence for a lock that has just expired —
  `AccountUserDetailsService` reads `isCurrentlyLocked` fresh via a monotonic clock, so a `false`
  result at load time can never become stale-`true` by the time `LoginFailureHandler`/
  `LoginSuccessHandler` runs later in the same request (time only moves forward; `lockedUntil`
  cannot decrease mid-request). A still-active lock (`now < locked_until`) is correctly rejected by
  Spring's own pre-authentication check before password verification ever runs, unchanged from
  before this task.
- **Concurrency:** `isCurrentlyLocked`'s non-locking read (a deliberate, already-approved
  trade-off — frozen brief Constraints, "point-in-time check, not part of a read-evaluate-write
  cycle") could theoretically observe a stale value under concurrent writes, but the subsequent
  `recordFailedAttempt`/`recordSuccessfulAttempt` call re-evaluates against the current,
  lock-protected state regardless (T12's existing `FOR UPDATE OF ls` protection, unchanged) — no
  new race condition introduced, worst case is a login attempt proceeding to password-check on a
  slightly-stale read, which self-corrects at the next real state-changing call.
- **Transaction boundaries:** `isCurrentlyLocked`'s own `@Transactional(readOnly = true)` covers
  its DB read regardless of whether `AccountUserDetailsService.loadUserByUsername` (not itself
  transactional) wraps it — correct, standard Spring propagation.
- **Thread-safety:** both new handlers are stateless `@Component` singletons (only injected
  `final` dependencies) — safe under concurrent requests.
- **`ReuseDetectingAuthorizationService.java`/`SecurityChainsConfig.java` import fixes:** confirmed
  via diff — exactly the two approved lines changed in each file, nothing else.

## Specification references

- Frozen brief: `04-frozen-task-brief.md` — AC9/L5 (Finding 1's context), Finding 6 (the
  disposition pattern Finding 1 follows), AC5/AC6 (traced and confirmed correct).
- `agents.md`: module boundaries (L12), transaction conventions.
