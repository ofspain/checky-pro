# auth · T13 — Phase 13: PR / Commit Preparation

Phase 12 verdict was **PASS**. Prepared for merge below. Branch off `main`; `main` stays
deployable — this task wires two new, self-contained Spring Security handlers into the existing
SAS login flow, adds one read-only method to an existing service, and fixes two pre-existing
wrong-package imports that were blocking the module from compiling at all. No existing endpoint
behavior changes for any account that isn't currently `LOCKED` past its interval.

## Commit title

```
Wire lockout tracking into the real SAS login flow (T13)
```

## Commit message

```
Wire lockout tracking into the real SAS login flow (T13)

LockoutService (T12) has had no caller until now. Adds
LoginFailureHandler and LoginSuccessHandler - Spring Security
AuthenticationFailureHandler/AuthenticationSuccessHandler
implementations wired into SecurityChainsConfig's applicationChain -
so a failed password login actually increments the counter and
records a login.failed audit event, and a successful login actually
resets it.

Repository research (Phase 0) found that the "pre-existing, unrelated
token package compile break" cited by every task since T09 is two
one-line wrong-package imports (JwtAuthenticationConverter,
OAuth2TokenType), not a missing dependency - verified by patching a
scratch copy and compiling clean. Every prior task could route around
it; this one couldn't, since its own job is modifying the exact file
that was broken. Escalated for an explicit scope decision rather than
silently fixed or silently left blocking - human-approved to fix both
import lines as part of this task.

That same research surfaced a real, load-bearing gap: Spring's
DaoAuthenticationProvider checks the accountLocked flag before
password verification, and AccountUserDetailsService was deriving it
from raw Account.status. Account.status only flips back to ACTIVE via
a successful login (among other paths), but Spring would never let a
login attempt reach the point of succeeding once locked - the account
could never unlock through the real login flow no matter how long the
lockout interval had elapsed. Fixed by adding
LockoutService.isCurrentlyLocked(UUID, Instant) - a new, deliberately
non-locking read - and using it instead of raw status.

Independent review (Phase 8) found three real defects with zero tests
existing yet to catch them: LoginFailureHandler and LoginSuccessHandler
had no exception handling around their new bookkeeping calls, so a
transient DB hiccup during either one would have turned a normal
failed login - or worse, a fully successful one - into a raw 500. Both
now catch, log, and always complete the redirect/login regardless.
A fourth finding (the session-cached AuthenticationException as a
future enumeration-safety risk) had a proposed fix that doesn't
compile - saveException is protected final in Spring Security 6.5.2 -
verified before rejecting it; left documented instead, since no login
page exists yet to exploit it.

Fixing the import bugs let the module compile end-to-end for the
first time since T03, which let the full test suite run for the first
time in this project's history. That surfaced six pre-existing,
previously-unverifiable issues in five files this task doesn't
otherwise touch - most notably that ArchitectureTest's own
module-boundary rule appears to have never actually been checked
before and fails against pre-existing code. None of the six are fixed
here (fixing them would mean scope creep into three unrelated
packages); all six are documented in 12-specification-verification.md
for their respective owners.

43 new unit tests (10 + 4 + 6 + 19 across the new handlers,
LockoutService.isCurrentlyLocked, and AccountUserDetailsService's
updated locked-account logic) plus a 4-test Testcontainers integration
suite proving the real filter chain end to end - compiles clean but
unexecuted in this environment (no Docker), the same limitation every
Testcontainers test in this chain carries. Every scenario it covers
has an equivalent passing mocked-unit proof.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

## Files changed

**Production:**
- `services/auth/src/main/java/com/themistra/auth/authn/LoginFailureHandler.java` (new) —
  extends `SimpleUrlAuthenticationFailureHandler`; status-gated `LockoutService`/`AuditService`
  calls, exception-handling isolation per Phase 9 Findings 1/3.
- `services/auth/src/main/java/com/themistra/auth/authn/LoginSuccessHandler.java` (new) —
  extends `SavedRequestAwareAuthenticationSuccessHandler`; exception-handling isolation per Phase
  9 Findings 2/8.
- `services/auth/src/main/java/com/themistra/auth/authn/LockoutService.java` (modified) — added
  `isCurrentlyLocked(UUID, Instant)`.
- `services/auth/src/main/java/com/themistra/auth/authn/LockoutStateRepository.java` (modified) —
  added `findByAccountUuid` (plain, non-locking read), supporting the above.
- `services/auth/src/main/java/com/themistra/auth/authn/AccountUserDetailsService.java`
  (modified) — the R18 core fix: `accountLocked` via `isCurrentlyLocked`, not raw `Account.status`.
- `services/auth/src/main/java/com/themistra/auth/token/SecurityChainsConfig.java` (modified) —
  `JwtAuthenticationConverter` import fixed; the two new handlers wired into `applicationChain`'s
  `.formLogin(...)`.
- `services/auth/src/main/java/com/themistra/auth/token/ReuseDetectingAuthorizationService.java`
  (modified) — `OAuth2TokenType` import fixed. No other change.

**Tests:**
- `services/auth/src/test/java/com/themistra/auth/authn/LoginFailureHandlerTest.java` (new, 10
  tests)
- `services/auth/src/test/java/com/themistra/auth/authn/LoginSuccessHandlerTest.java` (new, 4
  tests)
- `services/auth/src/test/java/com/themistra/auth/authn/SasLoginIntegrationTest.java` (new, 4
  tests, Testcontainers — unexecuted in this environment)
- `services/auth/src/test/java/com/themistra/auth/authn/AccountUserDetailsServiceTest.java`
  (modified — constructor updated, `lockedMapsToAccountLocked` split into 2 tests, pending/
  suspended tests strengthened; 6 total, was 5)
- `services/auth/src/test/java/com/themistra/auth/authn/LockoutServiceTest.java` (modified — 5
  new `isCurrentlyLocked` tests; 19 total, was 14)
- `services/auth/src/test/java/com/themistra/auth/token/ReuseDetectingAuthorizationServiceTest.java`
  (modified — one-line import fix)
- `services/auth/src/test/java/com/themistra/auth/token/TokenClaimsCustomizerTest.java` (modified
  — one-line import fix plus 3 explicit generic type witnesses resolving a separate, independent,
  always-latent `assertThat` overload ambiguity)

**Process artifacts** (`.ai/prompts/auth/T13/artifacts/`): `00-repository-understanding.md`
through `12-specification-verification.md` — full phase trail, including the Phase 4 human
decision (import-fix scope), the Phase 3/8/11 Kimi reviews and their dispositions (one rejected
Phase 3 finding, one rejected Phase 8 fix-mechanism, ten accepted Phase 11 gaps), the Phase 9 four
code fixes, and the Phase 12 PASS verdict documenting six newly-surfaced, out-of-scope pre-existing
issues in full.

## Summary

Implements `tasks.md` task 13: wires T11/T12's already-built lockout logic into the real SAS login
flow. Four things worth a reviewer's attention: (1) the `AccountUserDetailsService` fix is the
task's most consequential change — without it, R18 ("allow the next attempt once the interval
elapses") is structurally unreachable through the real login form, since Spring's own
pre-authentication gate would reject the attempt regardless of elapsed time; (2) the two-line
import fix was a human-scoped decision, escalated rather than assumed, and is what let this whole
module compile and its full test suite run for the first time since T03; (3) that in turn
surfaced six pre-existing, unrelated issues (most notably an apparently-never-enforced ArchUnit
rule) — reported in full, fixed in none, to avoid scope creep into three packages this task has
nothing to do with; (4) both new handlers gained exception-handling only after independent review
caught that neither had any — a transient DB failure during bookkeeping would otherwise have
turned either a normal failed login or a fully successful one into a raw 500.

## Testing performed

`mvn -pl services/auth compile` and `mvn -pl services/auth test-compile` **both succeed with zero
errors** — verified fresh at Phase 12, not merely carried over from earlier phases. This is the
first time either has been possible for any task in this entire chain.

**Result: 39/39 executable unit tests passing** (`LoginFailureHandlerTest` 10,
`LoginSuccessHandlerTest` 4, `AccountUserDetailsServiceTest` 6, `LockoutServiceTest` 19), executed
via the JUnit Platform Launcher, most recently re-run in full at Phase 11. `SasLoginIntegrationTest`'s
4 tests (Testcontainers, real Postgres + real HTTP server) compile clean but could not execute
here — no Docker daemon in this sandbox, the same limitation every Testcontainers test in this
chain carries (T12's own integration suite is in the same state).

Kimi's independent code review (Phase 8) found 10 findings; 5 accepted and applied (exception
isolation in both handlers, the non-UUID-principal guard, a documentation comment); 1 accepted
finding with its specific proposed fix rejected as unimplementable (verified `saveException` is
`protected final`) and left documented instead; 3 confirmed as already scheduled from Phase 6; 1
carried forward as Phase 10 test-writing guidance. Kimi's independent test review (Phase 11) found
10 gaps; all 10 held up on inspection and were applied — including a fix to the integration test
harness itself (`TestRestTemplate`'s default redirect-following, which would have silently
invalidated every status-code assertion in that file). Full requirement-to-evidence-to-test
traceability, plus the six-issue out-of-scope discovery, is in `12-specification-verification.md`.

## Specification references

- **Task:** `spec/auth-service/tasks.md`, task 13 — "Login failure/success tracking."
- **Requirements:** R16, R18, R43 (`requirements.md`), all implemented and tested at this real
  call-site layer (decision logic itself is T11's, persistence is T12's, both unmodified).
- **LOCKED decisions:** L4 (unchanged — no new lockout arithmetic). L5 (enumeration safety —
  verified by construction: neither handler branches on account status or exception subclass when
  producing the HTTP response). L12 (module boundary — confirmed clean via `grep`, zero `Account`
  entity imports across every file this task touched).
- **Named tests:** `shouldAppendRowAndMirrorAuditEventForLoginFailure`,
  `shouldResetLockoutCounterOnSuccessfulLogin` (`package.md` §8) — both present verbatim at this
  service layer.
