# auth · T03 — Phase 8: Independent Code Review Findings

Reviewed the implementation in `services/auth/src/main/java/com/themistra/auth/account/PasswordPolicy.java`,
`PasswordPolicyProperties.java`, `services/auth/src/main/java/com/themistra/auth/authn/BreachCheckClient.java`,
and `services/auth/src/main/resources/application.properties`, against the frozen brief
(`04-frozen-task-brief.md`) and `agents.md`. The Phase 7 self-review was consumed but re-examined
with fresh eyes; where it is upheld or extended, that is noted.

---

## Finding 1 — T03 has no unit tests, so R8–R10 acceptance criteria are currently unverified (HIGH)

**Issue:** The task statement and frozen brief both require unit tests for `PasswordPolicy` and
`BreachCheckClient`. None exist in the repository. This blocks verification of the highest-risk
parts of the implementation (HIBP URI resolution, timeout fail-open, audit-event emission, parsing
edge cases) and means the named tests cannot be judged passing.

**Evidence:**
- Task statement: "Add `PasswordPolicyProperties` and `PasswordPolicy`. Implement length validation
  and the Have I Been Pwned range check using `RestClient`. **Add unit tests.**"
- Frozen brief Required Tests (lines 195–212): lists `PasswordPolicyTest`,
  `BreachCheckClientTest`, and 12 specific test cases.
- No files matching `*PasswordPolicyTest.java` or `*BreachCheckClientTest.java` exist under
  `services/auth/src/test/`.
- Implementation notes (Phase 6, lines 102–104) explicitly state tests were deferred to Phase 10.

**Recommendation:** Treat this as a blocker. Create the two test classes before accepting T03,
including the tests that exercise exact URI resolution, simulated timeout, and audit-write failure.

**Confidence:** HIGH.

---

## Finding 2 — HIBP request URI resolution is correct but untested and easy to break with a leading slash (HIGH)

**Issue:** `BreachCheckClient` relies on `RestClient.Builder.baseUrl(".../range/")` plus
`.uri("{prefix}", prefix)` (no leading slash) to produce `.../range/ABCDE`. The construction is
consistent with `DefaultUriBuilderFactory`, but it is not exercised by any test, and a future
refactor that adds a leading slash would silently change the path to `.../range//ABCDE` or drop the
base path entirely. In either failure mode the call would fail, be caught as
`RestClientException`, and silently fail-open under R10.

**Evidence:**
- `BreachCheckClient.java` lines 46–47 and 63.
- Phase 6 implementation notes (lines 74–77) explain the reasoning but still note no test exists.

**Recommendation:** Add a `MockRestServiceServer` test asserting `requestTo("https://api.pwnedpasswords.com/range/ABCDE")`
(or equivalent) against the configured `url-prefix`, plus a negative test that proves the request
is *not* made to a double-slash path. This directly de-risks R9.

**Confidence:** HIGH (severity), MEDIUM that the current source is wrong — it is likely correct,
but the absence of a test makes it unprovable.

---

## Finding 3 — `PasswordPolicyProperties` does not enforce the NIST 12/128 bounds locked by L2 (MEDIUM)

**Issue:** `minLength` and `maxLength` are only constrained to be `@Min(1)`. The frozen brief
locks the NIST bounds to 12 and 128 (L2), and the default config honors that, but an operator can
override them to any positive value. A deployed service with `min-length=8` or `max-length=5` would
violate the locked decision without failing startup.

**Evidence:**
- `PasswordPolicyProperties.java` lines 21–22: `@Min(1) int minLength`, `@Min(1) int maxLength`;
  no `@Max` and no `@Min(12)`.
- Frozen brief Locked Decisions (lines 89–91): "Minimum 12 characters, maximum 128 characters ...
  Implement exactly as written."

**Recommendation:** Add `@Min(12)` on `minLength` and `@Min(12) @Max(128)` on `maxLength`
(or an `@AssertTrue` cross-field check) so the config record cannot express a value outside L2.
Also add the cross-field `minLength <= maxLength` check raised in the Phase 7 self-review.

**Confidence:** HIGH.

---

## Finding 4 — `account` and `authn` packages now form a two-way dependency cycle (MEDIUM)

**Issue:** `PasswordPolicy` (account) imports `BreachCheckClient` (authn), and `BreachCheckClient`
(authn) imports `PasswordPolicyProperties` (account). This is a package-level cycle. No existing
`ArchitectureTest` catches it, but it conflicts with the module-boundary discipline in
`agents.md` ("Each module owns its entities, repositories, services, and API... Shared plumbing
lives only in `common`"). It was authorized by the frozen brief/plan, so this is a flag for
awareness rather than a code change within T03.

**Evidence:**
- `BreachCheckClient.java` line 3: `import com.themistra.auth.account.PasswordPolicyProperties;`
- `PasswordPolicy.java` line 6: `import com.themistra.auth.authn.BreachCheckClient;`
- `ArchitectureTest` does not include a "no package cycles" rule.

**Recommendation:** Accept for T03 since the brief chose the file locations, but add a future
cleanup item: hoist the HIBP-specific config into a small record owned by `authn`, leaving
`account.PasswordPolicyProperties` with only the length bounds. That removes the `authn → account`
import and breaks the cycle.

**Confidence:** MEDIUM.

---

## Finding 5 — `BreachCheckClient.isBreached` has no null guard on a public method (LOW)

**Issue:** `isBreached(String rawPassword)` is public and will NPE inside `sha1UppercaseHex`
(`rawPassword.getBytes(...)`) if called with `null`. Its current caller null-checks first, but the
method is a public component surface and should fail intentionally, not with an NPE.

**Evidence:** `BreachCheckClient.java` lines 40–41 and 69–72.

**Recommendation:** Add `Objects.requireNonNull(rawPassword, "rawPassword must not be null")` at
the top of `isBreached` (and optionally a blank check consistent with `PasswordPolicy`).

**Confidence:** HIGH.

---

## Finding 6 — `breach-check.timeout-ms` can overflow `int` when building the `ClientHttpRequestFactory` (LOW)

**Issue:** `BreachCheckClient` converts `timeoutMs` to `int` with `Math.toIntExact`. The property
is a `@Positive long` with no upper bound, so a config value larger than `Integer.MAX_VALUE`
(e.g., a typo like `4000000000`) will cause `ArithmeticException` at bean construction. Startup
fails, but with a confusing error rather than a validation failure.

**Evidence:** `BreachCheckClient.java` line 57: `int timeoutMs = Math.toIntExact(properties.breachCheck().timeoutMs());`.

**Recommendation:** Add `@Max(Integer.MAX_VALUE)` to `PasswordPolicyProperties.BreachCheck.timeoutMs`,
or model the field as a validated `int` directly.

**Confidence:** MEDIUM.

---

## Finding 7 — `urlPrefix` is required even when breach-check is disabled (LOW)

**Issue:** `BreachCheck.urlPrefix` is annotated `@NotBlank` unconditionally. If an operator sets
`themistra.auth.password.breach-check.enabled=false` and omits or blanks the URL, the application
will still fail to start because the nested config record is `@Valid`. This may be surprising
because the disabled feature never uses the URL.

**Evidence:** `PasswordPolicyProperties.java` lines 26–30.

**Recommendation:** Either (a) make `urlPrefix` required only when `enabled=true` using a class-level
constraint, or (b) document explicitly in the frozen brief that disabling the check still requires
a valid URL placeholder.

**Confidence:** MEDIUM.

---

## Finding 8 — `urlPrefix` trailing slash is not validated/normalized (LOW)

**Issue:** The URI construction in `BreachCheckClient` assumes `urlPrefix` ends with a slash. The
default config ends with `/range/`, but a custom value missing the trailing slash would produce
`.../rangeABCDE` (404 or unexpected response), which is caught as `RestClientException` and
silently fail-open.

**Evidence:**
- `BreachCheckClient.java` line 63: `.baseUrl(properties.breachCheck().urlPrefix())` plus line 46
  `.uri("{prefix}", prefix)` with no slash.
- `application.properties` line 61 ends with `/range/`.

**Recommendation:** Normalize `urlPrefix` to end with `/` inside `buildRestClient`, or add a
validation constraint ensuring the configured value ends with `/`.

**Confidence:** MEDIUM.

---

## Finding 9 — Fail-open catch in `BreachCheckClient` is narrower than L2's "any failure" wording (LOW)

**Issue:** `isBreached` catches only `RestClientException` and wraps it. That covers timeouts,
I/O errors, and non-2xx responses, as required. However, any other unexpected exception from the
method (currently only possible for a null input or an extremely unlikely `NoSuchAlgorithmException`)
would propagate and cause `PasswordPolicy` to reject the password instead of failing open. Because
`isBreached` is a public method, callers other than `PasswordPolicy` may also assume the documented
contract that only `BreachCheckUnavailableException` is thrown.

**Evidence:** `BreachCheckClient.java` lines 50–53 and 69–76.

**Recommendation:** Document that `isBreached` may throw only `BreachCheckUnavailableException`
for any failure path; address Finding 5 (null guard) and keep the SHA-1 failure as an
`IllegalStateException` (it is a JVM-level programming error, not an API failure).

**Confidence:** LOW.

---

## Finding 10 — Audit row with null `accountUuid` may not match future `security-audit.v1.schema.json` expectations (LOW)

**Issue:** The R10 fail-open audit event is intentionally recorded without account/actor context
because `PasswordPolicy` is not wired to an endpoint in T03. This is documented in the frozen brief,
but whoever authors `contracts/events/auth/security-audit.v1.schema.json` in task 33 must allow
null account/actor UUIDs and the outbox type `security.password.breach_check_failed` (via the
existing `AuditService` prefix behavior).

**Evidence:**
- `PasswordPolicy.java` line 71: `accountUuid` and `actorUuid` are `null`.
- `AuditService.java` line 62: mirrors as `"security." + request.eventType()`.
- Frozen brief State Changes (lines 142–146) notes the Kafka prefix but defers contract authoring
to task 33.

**Recommendation:** No T03 code change. Add an explicit forward note to task 33 reminding the
contract author that `accountUuid`/`actorUuid` may be null in `password.breach_check_failed` events
and that the Kafka event type is prefixed with `security.`.

**Confidence:** LOW.

---

## Summary

The implementation is clean, small, and consistent with the frozen brief in structure, but the
absence of tests (Finding 1) and the unverified HIBP URI construction (Finding 2) make it impossible
to accept T03 as complete. The strongest logic/contract concern is Finding 3: the config record lets
an operator violate the locked NIST 12/128 bounds. The remaining findings are defensive-boundary
and operational-hardening issues.
