# auth · T03 — Phase 1: Specification Extraction

## Business Rules

- **R8.** When a password is set or changed, reject it if shorter than 12 characters or longer than 128 characters.
- **R9.** When a password is set or changed, query the Have I Been Pwned k-anonymity range API with the first 5 characters of the password's uppercase SHA-1 hash; if the trailing hash suffix appears in the range response with a count greater than zero, reject the password.
- **R10.** If the breach-check range API is unreachable, allow the password change and record a `password.breach_check_failed` audit event.

## Locked Decisions

- **L2. Password policy (NIST 800-63B).** Minimum 12 / maximum 128 characters, no composition rules, no forced periodic rotation. Breach screening uses the HIBP k-anonymity range API with a 5-character uppercase SHA-1 hash prefix. If the range API is down, the change is allowed but audited. — Implement exactly as written; do not add composition rules or rotation logic that L2 explicitly excludes.

## Files involved

**New files the spec expects** (`design.md` §6, package map):
- `account/PasswordPolicy.java` — length + breach-check rule.
- `account/PasswordPolicyProperties.java` — validated config record.
- `authn/BreachCheckClient.java` — `RestClient`-based HIBP caller.
- Mirrored unit tests under `src/test/java/com/themistra/auth/account/` and
  `src/test/java/com/themistra/auth/authn/`.

**Existing files to read (context/conventions), not to modify for this task:**
- `account/dto/RegisterAccountRequest.java` — has a pre-existing hardcoded `@Size(min=12,max=128)`
  on `password`; do not edit (no DTO/controller work is in T03's scope), but be aware it duplicates
  R8's length rule.
- `common/Hashing.java` — existing `sha256(String)` utility; no SHA-1 method exists today.
- `common/SecurityBeansConfig.java` — established home for shared, chain-agnostic beans
  (`PasswordEncoder`, `Clock`); reference for where a `RestClient` bean would conventionally live
  if one is introduced, though `design.md` §6 does not list a new bean there.
- `audit/AuditService.java`, `audit/RecordAuditEventRequest.java`, `audit/AuditOutcome.java` —
  needed to record `password.breach_check_failed` per R10 (see Open Questions on task ownership).

**Existing file to extend:**
- `src/main/resources/application.properties` — add the password-policy block from `design.md`
  §4c verbatim artifacts (see Dependencies below); this block must be copied exactly, not
  paraphrased.

## Dependencies

- **`RestClient`** (Spring Web) — no bean or usage exists anywhere in the service yet; this task
  introduces the first one.
- **`AuditService.record(RecordAuditEventRequest)`** — for the R10 fail-open audit event
  (`eventType = "password.breach_check_failed"`, no `accountUuid`/`actorUuid` guaranteed available
  at this layer — see Open Questions).
- **SHA-1, uppercase hex** — not currently provided by `common.Hashing` (SHA-256 only); needed to
  compute the HIBP query prefix/suffix per R9.
- **Config keys** (verbatim, `design.md` §4c):
  ```properties
  themistra.auth.password.min-length=12
  themistra.auth.password.max-length=128
  themistra.auth.password.breach-check.enabled=true
  themistra.auth.password.breach-check.url-prefix=https://api.pwnedpasswords.com/range/
  ```
- **Contracts** (`contracts/api/auth.yaml`, `contracts/api/token-claims.md`,
  `contracts/events/auth/*.schema.json`) — none apply to T03: this task has no controller/API
  surface and emits no Kafka event (the audit mirror is `AuditService`'s existing outbox call, not
  a new contract). Listed in the phase header as the service-wide contract set, not because T03
  authors or changes any of them.
- **External API contract (HIBP)** — not documented in the spec package; the range endpoint's
  response body is newline-delimited `SUFFIX:COUNT` pairs (HIBP's published k-anonymity API
  convention). Treated as an external-knowledge assumption, not a repository or spec fact.

## Acceptance Criteria

- **AC1 (R8).** A password of length < 12 is rejected by `PasswordPolicy`.
- **AC2 (R8).** A password of length > 128 is rejected by `PasswordPolicy`.
- **AC3 (R8, boundary).** Passwords of exactly 12 and exactly 128 characters are accepted by the
  length check (min/max are inclusive per "shorter than 12 or longer than 128").
- **AC4 (R9).** Given a password whose uppercase SHA-1 hash's suffix is returned by the HIBP range
  endpoint with a count > 0, `PasswordPolicy` rejects it.
- **AC5 (R9, implied).** Given a password whose suffix is absent from the range response (or
  returned with count 0), `PasswordPolicy` does not reject it on breach grounds.
- **AC6 (R9).** The outbound HIBP request uses only the first 5 characters of the uppercase SHA-1
  hash as the query path segment — the full password/hash is never transmitted.
- **AC7 (R10).** When the range API call fails (unreachable, timeout, non-2xx/5xx, or any I/O
  error), `PasswordPolicy` allows the password and a `password.breach_check_failed` audit event is
  recorded via `AuditService`.
- **AC8 (config, `themistra.auth.password.breach-check.enabled`).** When breach-check is disabled
  via config, the HIBP call is skipped entirely. Not stated as a numbered requirement, but implied
  by the verbatim config block existing in `design.md` §4c under L2's scope — include unless Open
  Questions below determine otherwise.

## Tests required

**Named (`package.md` §8):**
- `shouldRejectPasswordShorterThan12OrLongerThan128` → R8.
- `shouldRejectBreachedPasswordUsingHibpRange` → R9.
- `shouldAllowPasswordWhenBreachApiIsDownAndAuditFailure` → R10.

**Boundary tests implied:**
- Exactly-12 and exactly-128 character passwords pass the length check (AC3).
- 11-character and 129-character passwords fail (AC1/AC2).
- A range response containing the suffix with count `0` does not reject (AC5) — distinguishes
  "present with zero count" from "absent," since R9 says "count greater than zero."
- Hash-prefix sent to HIBP is uppercase and exactly 5 characters (AC6).
- `breach-check.enabled=false` skips the outbound call entirely (AC8), if implemented per Open
  Questions below.

**Testability note (not a new requirement, an implementation constraint):** `agents.md` requires
unit tests to run with no Spring context. No HTTP-mocking library (WireMock, MockWebServer,
`MockRestServiceServer`) is currently on the classpath, and no prior `RestClient` usage exists to
copy a pattern from — the test approach for `BreachCheckClient` must be worked out in the design
phase, not assumed here.

## Open Questions

1. **T03/T04 audit-wiring boundary (genuine blocker).** T03's own named-test list requires
   `shouldAllowPasswordWhenBreachApiIsDownAndAuditFailure`, which per R10 needs an
   `AuditService.record(...)` call — but `tasks.md` task 4 ("Breach-check audit event") separately
   assigns "Wire `AuditService.record(...)` for `password.breach_check_failed` and unit-test the
   fail-open path" as its own task, distinct from task 3 ("Password policy domain + config").
   `package.md` §11 has no item covering this split. Before design/implementation: does T03 include
   the `AuditService` call (making task 4 redundant/a no-op), or does T03 implement fail-open
   *without* the audit call and defer the named test itself to T04? Proceeding on the assumption
   that T03 owns it (required to satisfy T03's own stated named test) unless corrected.
2. **Test dependency for `BreachCheckClient` (scope question).** Unit-testing an outbound
   `RestClient` call without a Spring context, per `agents.md`'s testing convention, typically
   needs either a seam (injectable `ClientHttpRequestFactory`) or a test-scoped HTTP-mocking
   library not currently in `pom.xml`. Adding a new test dependency is a small pom.xml change but
   is worth surfacing before Phase 2 design commits to an approach, since "no unrelated
   refactoring/scope beyond this task" is a standing guardrail.
3. **`breach-check.enabled=false` behavior (minor, non-blocking).** R9 does not itself describe a
   disable toggle; it is inferred solely from the verbatim config block in `design.md` §4c under
   L2. Treating "skip the HIBP call when disabled" as in-scope (AC8) since the config key exists
   and L2 says implement exactly as written — flagging in case the author intended the flag for a
   different purpose (e.g., ops kill-switch documented elsewhere, not behavior to unit-test).
