# auth · T13 — Phase 9: Review Resolution

Human-approved disposition of Phase 7 (self-review, 1 finding) and Phase 8 (Kimi independent
review, 10 findings) against the Phase 6 implementation.

---

### Finding 1 — `LoginFailureHandler` bookkeeping failures can break the uniform failure redirect

**Disposition:** ACCEPTED.

**Reason:** Confirmed by direct code reading — no try/catch anywhere in `onAuthenticationFailure`/
`recordFailure`; any exception from `findLoginView`/`recordFailedAttempt`/`auditFailure` would
propagate and turn a normal failed login into a 500, itself a response-shape regression against
the very L5/AC9 guarantee this handler exists to uphold.

**Exact change made:** `LoginFailureHandler.java` — `onAuthenticationFailure` now wraps
`recordFailure(request)` in `try { ... } catch (RuntimeException e) { log.warn(...); }`;
`super.onAuthenticationFailure(...)` always runs afterward, unconditionally. Added an SLF4J
`Logger` field (matches this codebase's established swallow-but-log pattern, e.g.
`PasswordPolicy`/`AuditService`'s own fail-open logging).

---

### Finding 2 — `LoginSuccessHandler` lets a `LockoutService` failure break a successful login

**Disposition:** ACCEPTED.

**Reason:** Confirmed — worse than Finding 1 in practice: a user with fully correct credentials
could be denied login by a transient lockout-tracking/DB failure unrelated to their own request.

**Exact change made:** `LoginSuccessHandler.java` — the UUID resolution and
`lockoutService.recordSuccessfulAttempt(...)` call are now wrapped in the same
`try/catch (RuntimeException)` + `log.warn(...)` pattern; `super.onAuthenticationSuccess(...)`
always runs afterward. This single change resolves Finding 2 and Finding 8 together (see below).

---

### Finding 3 — `login.failed` audit event is lost if `LockoutService.recordFailedAttempt` throws

**Disposition:** ACCEPTED, resolved together with Finding 1.

**Reason:** Confirmed — `recordFailedAttempt` ran before `auditFailure` with no isolation between
them, so a lockout-tracking failure silently suppressed the audit event too (an R43 gap).

**Exact change made:** `LoginFailureHandler.recordFailure` — the `lockoutService
.recordFailedAttempt(...)` call now has its own inner `try/catch (RuntimeException)` +
`log.warn(...)`, independent of the outer catch from Finding 1's fix. `auditFailure(...)` is now
unconditionally reached regardless of whether the lockout call succeeded, threw, or was skipped
(not eligible).

---

### Finding 4 — Session-stored `AuthenticationException` could leak enumeration info via a future login page

**Disposition:** ACCEPTED as a genuine finding (duplicates Phase 7's own Finding 1); Kimi's
**specific recommended fix REJECTED** — verified not implementable.

**Reason:** Checked `SimpleUrlAuthenticationFailureHandler`'s actual source
(`spring-security-web-6.5.2`): `saveException(HttpServletRequest, AuthenticationException)` is
declared `protected final` — it cannot be overridden as Kimi's recommendation requires; that fix
would not compile. The only alternative within this class's API,
`setAllowSessionCreation(false)`, only prevents *creating* a new session — it does not stop the
exception from being stored in an *existing* session (`saveException`'s own `session != null`
branch), so it would not fully close the gap either, and disabling session creation for the whole
failure handler is a disproportionate, broader behavior change for a risk that is currently
unexploitable (confirmed at Phase 0/7: no login page template exists anywhere in this codebase;
O4 is an unresolved `design.md` open decision). Left as documented, not fixed — matching Phase 7's
original disposition, now reinforced by verifying the "obvious" fix doesn't actually work.

**Exact change made:** None. The forward-compatibility note already present in Phase 7's
self-review stands as the record of this finding for whoever implements O4.

---

### Finding 5 — No unit tests for the new handlers or `isCurrentlyLocked`

**Disposition:** CONFIRMED, already scheduled — no new action.

**Reason:** Not a new discovery; the Phase 5 plan and Phase 6 implementation notes both already
name these exact tests as Phase 10's job (test authorship is out of scope for Phase 6 per that
phase's own guardrail). No code or plan change needed here.

---

### Finding 6 — `AccountUserDetailsServiceTest` no longer compiles

**Disposition:** CONFIRMED, already scheduled — no new action.

**Reason:** Already explicitly flagged in Phase 6's implementation notes as an expected,
pre-identified consequence, deferred to Phase 10 by the same test-authorship boundary as Finding 5.

---

### Finding 7 — `ReuseDetectingAuthorizationServiceTest` import broken by the production import fix

**Disposition:** CONFIRMED, already scheduled — no new action.

**Reason:** Already explicitly flagged in Phase 6's implementation notes (along with the additional,
Phase-5-unanticipated `TokenClaimsCustomizerTest.java` breakage Kimi's review did not surface).
Deferred to Phase 10.

---

### Finding 8 — `LoginSuccessHandler` assumes the principal name is always UUID-shaped

**Disposition:** ACCEPTED, resolved together with Finding 2.

**Reason:** Confirmed — `UUID.fromString` throws `IllegalArgumentException` (a `RuntimeException`)
for a non-UUID input with no guard. Under this task's actual wiring this is unreachable in
practice (`LoginSuccessHandler` only ever sees principals `AccountUserDetailsService` produced,
always a UUID string), but defensive hardening against an internal-wiring assumption breaking is
cheap and directly prevents the same class of problem as Finding 2 (an authenticated user being
denied login by an internal bug unrelated to their credentials).

**Exact change made:** Same try/catch as Finding 2 — `UUID.fromString(authentication.getName())`
is inside the same `try` block, so a parse failure is caught, logged, and swallowed exactly like a
`LockoutService` failure, with the login still completing via `super.onAuthenticationSuccess(...)`.

---

### Finding 9 — `LoginFailureHandler` hardcodes the `"username"` request parameter

**Disposition:** ACCEPTED, documentation-only — no config key added.

**Reason:** The parameter name is correct today (`.formLogin(Customizer.withDefaults())`,
unchanged by this task). Kimi's own weaker alternative — document the coupling rather than add a
new config key — is the right call: introducing a `themistra.auth.login.username-parameter`
property with no current need for a non-default value is exactly the kind of speculative addition
this phase's guardrails rule out.

**Exact change made:** `LoginFailureHandler.recordFailure` — added a one-line comment stating the
coupling to `.formLogin(Customizer.withDefaults())`'s default parameter name, so a future
customization of that default is a visible, documented trap rather than a silent one.

---

### Finding 10 — Timing side-channel between known/unknown failure branches remains observable

**Disposition:** ACCEPTED, carried forward as Phase 10 test-writing guidance — no code change.

**Reason:** Already the frozen brief's own explicit, accepted trade-off (Phase 4, Finding 4's
disposition: response-shape identity only, timing is out of scope, mitigated separately by rate
limiting). Kimi's addition is purely about how the AC9 test should be *worded* when Phase 10 writes
it — not a code or design change. Noted for Phase 10, not actioned here.

---

## Summary

- **Accepted, code changed:** 5 (Findings 1, 2, 3, 8 — via two try/catch additions across the two
  handlers; Finding 9 — a one-line comment).
- **Accepted, no code change (already scheduled or infeasible as proposed):** 4 (Finding 4 —
  proposed fix verified non-compiling, left documented; Findings 5, 6, 7 — already scheduled for
  Phase 10 before this review ran).
- **Accepted, deferred to Phase 10 as test-writing guidance:** 1 (Finding 10).
- **Rejected:** 0 findings outright; one specific *recommended mechanism* (Finding 4's `saveException`
  override) rejected as unimplementable, with the underlying finding itself still accepted.

Verified compiling clean after all changes (`mvn -pl services/auth compile`, zero errors — the
same full-module compile this task made possible for the first time since T03, still holding).
No public API changed (both fixes are internal to existing method bodies), no refactoring, no
renaming, no scope beyond what each finding specifically required.
