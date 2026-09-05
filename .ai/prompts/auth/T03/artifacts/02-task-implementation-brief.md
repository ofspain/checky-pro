# auth · T03 — Task Implementation Brief (TIB)

## Task

Password policy domain + config: add `PasswordPolicyProperties` and `PasswordPolicy`; implement
length validation and the Have I Been Pwned (HIBP) k-anonymity range check via `RestClient`; add
unit tests.

## Purpose

Provide a single, reusable, config-driven password-policy component (length + breach screening)
that later tasks (registration, change-password, password-reset — task 9) will call to enforce
NIST 800-63B. Not wired to any endpoint in this task.

## Scope

**In:**
- `PasswordPolicyProperties` — validated `@ConfigurationProperties` record for
  `themistra.auth.password.*`.
- `PasswordPolicy` — domain rule combining length validation (R8) and breach screening (R9, R10).
- `BreachCheckClient` — `RestClient` call to the HIBP range API, first-5-uppercase-SHA1-hex prefix.
- Adding the verbatim password-policy config block to `application.properties`.
- The R10 fail-open audit call (`AuditService.record(...)`, event type
  `password.breach_check_failed`) — included in T03 because T03's own named test
  (`shouldAllowPasswordWhenBreachApiIsDownAndAuditFailure`) requires it, even though `tasks.md`
  task 4 separately describes "wire the breach-check audit event." Treating task 4 as satisfied by
  this task's implementation rather than deferring the audit call. **Assumption — flag if wrong.**
- Unit tests for all of the above (no Spring context, per `agents.md`).

**Out:**
- Wiring `PasswordPolicy` into `AccountController`/`AccountService`, `RegisterAccountRequest`, or
  any endpoint (task 9).
- Verification tokens, lockout, MFA, API keys, sessions, rate limiting (other tasks).
- Any contract file (`contracts/api/auth.yaml`, `contracts/api/token-claims.md`,
  `contracts/events/auth/*.schema.json`) — none apply to this task.
- Testcontainers/integration tests — this task is pure domain logic + one outbound HTTP call; no
  persistence.

## Business Rules

- **R8.** Reject a password shorter than 12 or longer than 128 characters.
- **R9.** Query HIBP range API with the first 5 chars of the password's uppercase SHA-1 hash;
  reject if the trailing suffix appears with count > 0.
- **R10.** If the range API is unreachable, allow the password and record a
  `password.breach_check_failed` audit event.

## Locked Decisions

- **L2.** NIST 800-63B: min 12 / max 128, no composition rules, no forced rotation. HIBP
  k-anonymity, 5-char uppercase SHA-1 prefix. Fail-open with audit if the range API is down.
  Implement exactly as written.

## Dependencies

- `RestClient` (Spring Web) — new; no existing bean or usage in this service.
- `AuditService.record(RecordAuditEventRequest)` / `AuditOutcome` — for the R10 audit event.
- SHA-1, uppercase hex digest — not present in `common.Hashing` (SHA-256 only) or anywhere else in
  the service; needs adding (home for it — `Hashing` vs. local to `BreachCheckClient` — is an
  implementation choice, not fixed by this brief).
- Config keys (verbatim, `design.md` §4c):
  ```properties
  themistra.auth.password.min-length=12
  themistra.auth.password.max-length=128
  themistra.auth.password.breach-check.enabled=true
  themistra.auth.password.breach-check.url-prefix=https://api.pwnedpasswords.com/range/
  ```
- Test facility: `org.springframework.test.web.client.MockRestServiceServer` (ships with
  `spring-test`, already on the classpath via `spring-boot-starter-test`) supports binding to a
  `RestClient.Builder` — no new test dependency is required for `BreachCheckClientTest`.

## Inputs

- Candidate raw password (`String`) into `PasswordPolicy`.
- Bound config values from `PasswordPolicyProperties`.
- HTTP response body (newline-delimited `SUFFIX:COUNT` pairs — HIBP's published convention, not
  documented in the spec package) into `BreachCheckClient`.

## Outputs

- `PasswordPolicy` accepts (no-op) or rejects (via a domain-specific exception — exact type is a
  design-phase decision) a candidate password.
- `BreachCheckClient` reports whether the hash suffix was found with count > 0, or that the check
  could not be completed (for `PasswordPolicy` to apply the R10 fail-open path).

## State Changes

On fail-open only (R10): one `password.breach_check_failed` row is appended to `auth_audit` and
mirrored to `auth.security.audit` via the outbox, through the existing `AuditService.record(...)`
call. No other database writes. `PasswordPolicy`/`BreachCheckClient` are otherwise stateless and
own no entity, repository, or table.

## Files to Create

- `services/auth/src/main/java/com/themistra/auth/account/PasswordPolicy.java`
- `services/auth/src/main/java/com/themistra/auth/account/PasswordPolicyProperties.java`
- `services/auth/src/main/java/com/themistra/auth/authn/BreachCheckClient.java`
- `services/auth/src/test/java/com/themistra/auth/account/PasswordPolicyTest.java` (name indicative)
- `services/auth/src/test/java/com/themistra/auth/authn/BreachCheckClientTest.java` (name indicative)

## Files to Modify

- `services/auth/src/main/resources/application.properties` — append the password-policy block
  above, verbatim.

## Files NOT to Modify

- `account/dto/RegisterAccountRequest.java` (existing hardcoded `@Size(12,128)` — out of scope, no
  endpoint wiring in T03).
- `account/AccountController.java`, `account/AccountService.java` (wiring is task 9).
- Any file under `spec/` or `contracts/`.

## Acceptance Criteria

- **R8** — password of length < 12 rejected; length > 128 rejected; exactly 12 and exactly 128
  accepted by the length check (inclusive bounds).
- **R9** — password whose uppercase-SHA-1 suffix is returned by HIBP with count > 0 is rejected;
  a suffix absent or returned with count 0 is not rejected on breach grounds; only the 5-character
  uppercase prefix is ever sent to HIBP (full hash/password never transmitted).
- **R10** — any failure to complete the range-API call (unreachable, timeout, error status, I/O
  exception) results in the password being allowed AND a `password.breach_check_failed` audit
  event being recorded via `AuditService`.
- **Config-derived** — when `themistra.auth.password.breach-check.enabled=false`, the HIBP call is
  skipped entirely (inferred from the verbatim config block under L2's scope).

## Required Tests

- `shouldRejectPasswordShorterThan12OrLongerThan128` (R8).
- `shouldRejectBreachedPasswordUsingHibpRange` (R9).
- `shouldAllowPasswordWhenBreachApiIsDownAndAuditFailure` (R10).
- Boundary: exactly-12 / exactly-128 accepted; 11 / 129 rejected.
- Suffix present with count `0` is not treated as breached.
- Outbound request path uses an uppercase, 5-character prefix.
- `breach-check.enabled=false` bypasses the HTTP call.

## Constraints

- **Performance / fail-open reliability:** the HIBP call must be made with a bounded timeout;
  R10's fail-open guarantee is meaningless against an indefinitely hanging call. No timeout value
  is specified anywhere in the spec — a reasonable default must be chosen (not mandated by any
  requirement, so not itself an acceptance criterion).
- **Security:** never log the raw password, the full SHA-1 hash, or the HIBP suffix; only the
  5-character prefix leaves the process (agents.md: "never log tokens, secrets").
- **Thread-safety:** `PasswordPolicy` and `BreachCheckClient` are stateless Spring singletons, safe
  for concurrent use — consistent with `AccountService`/`AuditService`.
- **Transaction:** neither class does its own persistence and neither should be annotated
  `@Transactional`; the R10 audit path relies on `AuditService.record(...)`'s own transaction
  boundary.
- **Module boundaries:** `PasswordPolicy` (package `account`) depends on `BreachCheckClient`
  (package `authn`) per `design.md` §6's package map. No existing `ArchitectureTest` rule forbids
  `account → authn`; this dependency direction is not itself new design, it's what the spec's file
  map already states.
- **Null handling:** `PasswordPolicy` must not silently NPE on a null/blank candidate password —
  no DTO-level `@NotBlank` guard is guaranteed at this layer since no controller calls it yet.

## Open Questions

No blockers. (The task-3/task-4 audit-wiring overlap noted in Phase 1 is resolved above as an
explicit assumption under Scope — surfaced there for the design-challenge phase to contest, not
treated as blocking here.)
