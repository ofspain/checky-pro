# auth · T03 — Frozen Task Brief

**STATUS: FROZEN**
**Approved by:** femi (human approval gate, Phase 4)
**Date:** 2026-07-24
**Supersedes:** `artifacts/02-task-implementation-brief.md`, amended per `artifacts/03-design-challenge.md`.

Downstream phases (5 onward) implement against this document only. It may not be renegotiated
without a new human approval pass.

---

## Phase 3 finding disposition

All 11 Kimi findings reviewed and accepted by the human approver. Two are folded directly into
the brief below; two require action outside T03's code scope and are explicitly deferred with an
owner (not blockers for starting Phase 5).

| # | Finding | Disposition |
|---|---|---|
| 1 | HIBP requires a `User-Agent` header or returns 403 | **ACCEPTED — folded in** (Constraints, Required Tests). |
| 2 | Audit outcome/context for the R10 event undefined | **ACCEPTED — folded in** (State Changes). |
| 3 | `package.md` §8 maps these tests to R11/R12/R13, not R8/R9/R10 | **ACCEPTED — deferred.** Confirmed as a real spec inconsistency (verified against `requirements.md` and `package.md` directly). Owner: spec author (femi), to reconcile `spec/auth-service/package.md` and/or `requirements.md` outside this task — we do not modify `spec/`. T03 proceeds against `requirements.md`'s R8/R9/R10 text, which substantively matches these three named tests; `package.md`'s R11–R13 labels describe unrelated endpoints (change-own-password, reset-request, reset-email) and are not used. |
| 4 | Kafka mirror event type gets a `security.` prefix | **ACCEPTED — folded in** (State Changes note). |
| 5 | SHA-1 implementation location left open | **ACCEPTED — folded in** (Dependencies: locked to `BreachCheckClient`). |
| 6 | Bounded timeout not configurable | **ACCEPTED — folded in** (Dependencies: new config key). |
| 7 | Audit-write failure inside the fail-open path unaddressed | **ACCEPTED — folded in** (State Changes, Constraints). |
| 8 | HIBP response parsing edge cases unspecified | **ACCEPTED — folded in** (Constraints, Required Tests). |
| 9 | Null/blank password exception type unspecified | **ACCEPTED — folded in** (Constraints). |
| 10 | Task 3 / task 4 audit-wiring sequencing ambiguity | **ACCEPTED — resolved explicitly.** T03's implementation fully satisfies `tasks.md` task 4's intent ("wire `AuditService.record(...)` for `password.breach_check_failed`, unit-test the fail-open path"). Owner: project tracking — mark task 4 satisfied by T03 rather than duplicating the work. |
| 11 | Future conflict with `RegisterAccountRequest`'s hardcoded `@Size(12,128)` | **ACCEPTED — deferred.** Not a T03 file change (T03 still does not touch this DTO). Owner: task 9 implementer, who must reconcile/remove the hardcoded annotation when wiring `PasswordPolicy` into registration/change-password/reset. Logged here so it isn't lost. |

No findings rejected.

---

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
- `BreachCheckClient` — `RestClient` call to the HIBP range API, first-5-uppercase-SHA1-hex prefix,
  with a static `User-Agent` header and a bounded, configurable timeout (Finding 1, 6).
- Adding the password-policy config block to `application.properties` (verbatim `design.md` §4c
  keys plus the new `timeout-ms` key from Finding 6 — see Dependencies).
- The R10 fail-open audit call (`AuditService.record(...)`, event type
  `password.breach_check_failed`, `AuditOutcome.FAILURE`) — T03 fully satisfies `tasks.md` task 4's
  intent (Finding 10, resolved above).
- Unit tests for all of the above (no Spring context, per `agents.md`).

**Out:**
- Wiring `PasswordPolicy` into `AccountController`/`AccountService`, `RegisterAccountRequest`, or
  any endpoint (task 9). The hardcoded `@Size(12,128)` on `RegisterAccountRequest` remains
  untouched here (Finding 11, deferred to task 9).
- Verification tokens, lockout, MFA, API keys, sessions, rate limiting (other tasks).
- Any contract file (`contracts/api/auth.yaml`, `contracts/api/token-claims.md`,
  `contracts/events/auth/*.schema.json`) — none apply to this task.
- Testcontainers/integration tests — this task is pure domain logic + one outbound HTTP call; no
  persistence.
- Reconciling the `package.md` §8 / `requirements.md` requirement-ID mismatch (Finding 3) — spec
  authoring, not code, and out of scope for `spec/` edits.

## Business Rules

- **R8.** Reject a password shorter than 12 or longer than 128 characters.
- **R9.** Query HIBP range API with the first 5 chars of the password's uppercase SHA-1 hash;
  reject if the trailing suffix appears with count > 0.
- **R10.** If the range API is unreachable, allow the password and record a
  `password.breach_check_failed` audit event.

Note (Finding 3): `package.md` §8 labels these tests' requirement IDs as R11/R12/R13; that mapping
is stale/incorrect and is not used here. R8/R9/R10 above are taken directly from `requirements.md`.

## Locked Decisions

- **L2.** NIST 800-63B: min 12 / max 128, no composition rules, no forced rotation. HIBP
  k-anonymity, 5-char uppercase SHA-1 prefix. Fail-open with audit if the range API is down.
  Implement exactly as written.

## Dependencies

- `RestClient` (Spring Web) — new; no existing bean or usage in this service.
- `AuditService.record(RecordAuditEventRequest)` / `AuditOutcome` — for the R10 audit event.
- SHA-1, uppercase hex digest — **locked (Finding 5):** implemented as a private/package-private
  method local to `BreachCheckClient` (or a small `authn`-scoped helper). Explicitly **not** added
  to `common.Hashing` for T03 — that class stays SHA-256-only, per its existing javadoc contract.
- Config keys — verbatim block from `design.md` §4c, **plus one new key added by human-approved
  amendment (Finding 6, not in the original verbatim artifact)**:
  ```properties
  themistra.auth.password.min-length=12
  themistra.auth.password.max-length=128
  themistra.auth.password.breach-check.enabled=true
  themistra.auth.password.breach-check.url-prefix=https://api.pwnedpasswords.com/range/
  themistra.auth.password.breach-check.timeout-ms=3000
  ```
- Static `User-Agent` header on every `BreachCheckClient` request (Finding 1) — value
  `Themistra-Auth-Service/1.0`; a constant, not configurable.
- Test facility: `org.springframework.test.web.client.MockRestServiceServer` (ships with
  `spring-test`, already on the classpath via `spring-boot-starter-test`) supports binding to a
  `RestClient.Builder` — no new test dependency required. Use it to simulate a timeout/error for
  the fail-open test and to assert the `User-Agent` header and prefix path on outbound requests.

## Inputs

- Candidate raw password (`String`) into `PasswordPolicy`.
- Bound config values from `PasswordPolicyProperties`.
- HTTP response body (newline-delimited `SUFFIX:COUNT` pairs — HIBP's published convention) into
  `BreachCheckClient`. Parsing rules locked under Constraints (Finding 8).

## Outputs

- `PasswordPolicy` accepts (no-op) or rejects (via a single domain-specific exception type, used
  consistently for length, breach, and null/blank rejections — exact class name is a Phase 5
  implementation detail, not fixed here) a candidate password.
- `BreachCheckClient` reports whether the hash suffix was found with count > 0, or that the check
  could not be completed (for `PasswordPolicy` to apply the R10 fail-open path).

## State Changes

On fail-open only (R10): one `password.breach_check_failed` row is appended to `auth_audit` and
mirrored to Kafka via the existing `AuditService.record(...)` → outbox path.

- **Outcome (Finding 2, locked):** `AuditOutcome.FAILURE` — the breach check itself failed, even
  though the password is allowed.
- **Context (Finding 2, locked):** `accountUuid = null`, `actorUuid = null`. `PasswordPolicy` is
  not wired to any authenticated endpoint in T03, so no actor/target context exists yet; task 9
  will need to thread account context through when it wires `PasswordPolicy` into
  registration/change-password/reset.
- **Kafka representation (Finding 4, informational):** the DB row's `eventType` stays
  `password.breach_check_failed`; `AuditService` mirrors it to Kafka as
  `security.password.breach_check_failed` (the `"security."` prefix is existing `AuditService`
  behavior, not new for this task). Note for whoever later authors
  `contracts/events/auth/security-audit.v1.schema.json` (task 33).
- **Audit-write failure (Finding 7, locked):** if `AuditService.record(...)` itself throws,
  `PasswordPolicy` must catch and log it, not propagate — R10's "allow the password change"
  guarantee holds even if the audit write fails. Add a test for this.

No other database writes. `PasswordPolicy`/`BreachCheckClient` own no entity, repository, or table.

## Files to Create

- `services/auth/src/main/java/com/themistra/auth/account/PasswordPolicy.java`
- `services/auth/src/main/java/com/themistra/auth/account/PasswordPolicyProperties.java`
- `services/auth/src/main/java/com/themistra/auth/authn/BreachCheckClient.java`
- `services/auth/src/test/java/com/themistra/auth/account/PasswordPolicyTest.java` (name indicative)
- `services/auth/src/test/java/com/themistra/auth/authn/BreachCheckClientTest.java` (name indicative)

## Files to Modify

- `services/auth/src/main/resources/application.properties` — append the password-policy block
  above (including `timeout-ms`).

## Files NOT to Modify

- `account/dto/RegisterAccountRequest.java` (existing hardcoded `@Size(12,128)` — out of scope;
  see Finding 11, deferred to task 9).
- `account/AccountController.java`, `account/AccountService.java` (wiring is task 9).
- `common/Hashing.java` (Finding 5 — SHA-1 stays local to `authn`, not added here).
- Any file under `spec/` or `contracts/`.

## Acceptance Criteria

- **R8** — password of length < 12 rejected; length > 128 rejected; exactly 12 and exactly 128
  accepted by the length check (inclusive bounds).
- **R9** — password whose uppercase-SHA-1 suffix is returned by HIBP with count > 0 is rejected;
  a suffix absent or returned with count 0 is not rejected on breach grounds; only the 5-character
  uppercase prefix is ever sent to HIBP; every outbound request carries the
  `User-Agent: Themistra-Auth-Service/1.0` header.
- **R10** — any failure to complete the range-API call (unreachable, timeout beyond
  `breach-check.timeout-ms`, error status, I/O exception) results in the password being allowed AND
  a `password.breach_check_failed` audit event (`AuditOutcome.FAILURE`, null account/actor context)
  being recorded via `AuditService`; a failure in the audit write itself must not block the
  password from being allowed.
- **Config-derived** — when `themistra.auth.password.breach-check.enabled=false`, the HIBP call is
  skipped entirely.
- **Parsing (Finding 8)** — blank/whitespace-only response lines are ignored; suffix comparison is
  case-insensitive (both sides normalized to uppercase); a line without a positive integer count is
  treated as non-breach.
- **Null/blank (Finding 9)** — a null or blank candidate password is rejected via the same
  domain-specific exception used for length violations, not an NPE.

## Required Tests

- `shouldRejectPasswordShorterThan12OrLongerThan128` (R8).
- `shouldRejectBreachedPasswordUsingHibpRange` (R9).
- `shouldAllowPasswordWhenBreachApiIsDownAndAuditFailure` (R10).
- Boundary: exactly-12 / exactly-128 accepted; 11 / 129 rejected.
- Suffix present with count `0` is not treated as breached.
- Outbound request path uses an uppercase, 5-character prefix; outbound request includes the
  `User-Agent` header (Finding 1).
- `breach-check.enabled=false` bypasses the HTTP call.
- Simulated timeout (beyond `timeout-ms`) triggers fail-open, not an unbounded hang (Finding 6).
- Audit event recorded on fail-open has `AuditOutcome.FAILURE` and null account/actor context
  (Finding 2).
- `AuditService.record(...)` throwing during the fail-open path does not prevent the password from
  being allowed (Finding 7).
- Response parsing: trailing newline / blank line ignored, lowercase suffix still matches, count
  `0` is non-breach (Finding 8).
- Null and blank password both rejected via the domain exception, not NPE (Finding 9).

## Constraints

- **Performance / fail-open reliability:** `BreachCheckClient` uses a bounded timeout, configured
  via `themistra.auth.password.breach-check.timeout-ms` (default `3000`), not an arbitrary
  unconfigured value (Finding 6).
- **Security:** never log the raw password, the full SHA-1 hash, or the HIBP suffix; only the
  5-character prefix leaves the process (agents.md: "never log tokens, secrets").
- **Thread-safety:** `PasswordPolicy` and `BreachCheckClient` are stateless Spring singletons, safe
  for concurrent use — consistent with `AccountService`/`AuditService`.
- **Transaction:** neither class does its own persistence and neither should be annotated
  `@Transactional`; the R10 audit path relies on `AuditService.record(...)`'s own transaction
  boundary, and its failure must be caught, not propagated (Finding 7).
- **Module boundaries:** `PasswordPolicy` (package `account`) depends on `BreachCheckClient`
  (package `authn`) per `design.md` §6's package map. No existing `ArchitectureTest` rule forbids
  `account → authn`.
- **Null handling:** `PasswordPolicy` rejects null/blank candidate passwords via the same
  domain-specific exception as length violations (Finding 9) — never an NPE.
- **Response parsing:** ignore blank lines, compare suffixes case-insensitively, treat any
  non-positive-integer count as non-breach (Finding 8).

## Open Questions

- **Finding 3 (deferred, not blocking Phase 5):** `package.md` §8's requirement-ID labels for these
  three named tests (R11/R12/R13) conflict with `requirements.md`'s actual R8/R9/R10. Owner: spec
  author (femi) to reconcile `spec/auth-service/package.md`/`requirements.md` in a separate change.
  T03 implementation proceeds against `requirements.md`'s R8/R9/R10, as stated above.

All other open questions from Phase 1/2 are resolved above (Finding 10: task 3/4 sequencing;
Finding 11: forward-noted for task 9). No blockers remain for Phase 5.
