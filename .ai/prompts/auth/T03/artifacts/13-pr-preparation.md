# auth · T03 — Phase 13: PR / Commit Preparation

Phase 12 verdict was **PASS**. Prepared for merge below. Branch off `main`; `main` stays
deployable throughout — this task adds new, unwired domain code only (no endpoint changes, no
schema changes), so it carries no runtime behavior change until task 9 wires it in.

## Commit title

```
Add password policy domain and HIBP breach-check (T03)
```

## Commit message

```
Add password policy domain and HIBP breach-check (T03)

Add PasswordPolicyProperties, PasswordPolicy, and BreachCheckClient to enforce
NIST 800-63B password requirements (L2): 12-128 character length and Have I
Been Pwned k-anonymity breach screening, with a fail-open path that records a
password.breach_check_failed audit event when the range API is unreachable.

Not wired to any registration/change-password/reset endpoint yet - that's
task 9. This task adds the domain and config only, per tasks.md task 3, and
fully absorbs task 4's audit-wiring intent since the R10 named test requires
it (decided at the Phase 4 human-approval gate).

27 unit tests cover length boundaries, breach detection, fail-open + audit
behavior (including audit-write failures being swallowed, never blocking the
password), HIBP response-parsing edge cases, and config-validation hardening
(NIST bounds and a min<=max cross-check enforced at the
@ConfigurationProperties level, not just in domain logic).

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

## Files changed

**Production:**
- `services/auth/src/main/java/com/themistra/auth/account/PasswordPolicy.java` (new)
- `services/auth/src/main/java/com/themistra/auth/account/PasswordPolicyProperties.java` (new)
- `services/auth/src/main/java/com/themistra/auth/authn/BreachCheckClient.java` (new)
- `services/auth/src/main/resources/application.properties` (modified — appended the
  `themistra.auth.password.*` config block)

**Tests:**
- `services/auth/src/test/java/com/themistra/auth/account/PasswordPolicyTest.java` (new)
- `services/auth/src/test/java/com/themistra/auth/account/PasswordPolicyPropertiesTest.java` (new)
- `services/auth/src/test/java/com/themistra/auth/authn/BreachCheckClientTest.java` (new)

**Process artifacts** (`.ai/prompts/auth/T03/artifacts/`): `00-repository-understanding.md`
through `12-specification-verification.md` — the full 13-phase trail for this task, including the
Phase 3/8/11 Kimi reviews, the Phase 4/9 human-approval resolutions, and the traceability matrix.

## Summary

Implements `tasks.md` task 3 (Foundation): a config-driven password-policy domain enforcing L2
(NIST 800-63B) — minimum/maximum length and Have I Been Pwned breach screening via a k-anonymity
range query, with a fail-open-and-audit rule when that external API is unreachable. Pure domain +
config addition; no controller, DTO, or existing-file wiring (that's task 9). Two design decisions
worth a reviewer's attention: (1) task 4's audit-wiring was folded into this task rather than kept
separate, decided explicitly at Phase 4; (2) `PasswordPolicyProperties`'s length bounds are hard-
capped to L2's 12/128 range via Bean Validation, so misconfiguration fails startup rather than
silently accepting an out-of-policy value.

## Testing performed

`mvn -pl services/auth test` could not be run for a final Surefire-mediated confirmation: the
`token` package has a pre-existing, unrelated compile failure (`OAuth2TokenType`,
`JwtAuthenticationConverter` symbols not found — a likely `spring-security-oauth2-authorization-
server` version mismatch, last touched 2026-07-13, untouched by this branch). Verified instead by
compiling the three new/changed test classes and their real transitive dependency chain directly
with `javac` against the module's resolved dependency classpath, then executing them with the
JUnit Platform `Launcher` API (`junit-platform-launcher` matching the project's resolved
`junit-platform-engine` version) — the same engine Surefire itself delegates to.

**Result: 27/27 tests passing**, ~5s total, zero external network calls. Notably, the original
test plan called for `MockRestServiceServer` bound to a `RestClient.Builder`; writing that test
surfaced a real bug — `BreachCheckClient`'s own timeout-configuring `.requestFactory(...)` call
silently overwrites the mock's request factory, and in a sandbox with internet access this made
tests fall through to live calls against the real Have I Been Pwned API. Resolved by testing
against a real, local `com.sun.net.httpserver.HttpServer` (JDK built-in, `127.0.0.1` only) instead
— no production code change needed, and it proves the exact production code path end-to-end
(confirmed the previously-unverified HIBP URI construction is in fact correct).

A Kimi independent code review (Phase 8) and test review (Phase 11) both ran against this
implementation; all accepted findings are folded in (`09-review-resolution.md`, `10-test-
generation.md` addendum). Full traceability from requirement to evidence to test is in
`12-specification-verification.md`.

## Specification references

- **Task:** `spec/auth-service/tasks.md`, task 3 — "Password policy domain + config."
- **Requirements:** R8, R9, R10 (`requirements.md`).
- **LOCKED decision:** L2 (`design.md` §4a).
- **Named tests:** `shouldRejectPasswordShorterThan12OrLongerThan128`,
  `shouldRejectBreachedPasswordUsingHibpRange`, `shouldAllowPasswordWhenBreachApiIsDownAndAuditFailure`
  (`package.md` §8 — note the stale R11/R12/R13 labels there, flagged and deferred to the spec
  author at the Phase 4 gate; this PR follows `requirements.md`'s R8/R9/R10 text).
