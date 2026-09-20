# auth · T03 — Phase 11: Test Review Findings

Reviewed `PasswordPolicyTest.java` and `BreachCheckClientTest.java` against the frozen brief
acceptance criteria, the named tests from the task header, and the post-Phase 9 production code.
All existing tests are coherent and deterministic against the current implementation; the gaps
below are about coverage and false-positive risk.

---

## Gap 1 — `BreachCheckClientTest`'s catch-all `"/"` handler lets most tests pass even with the wrong URI or HTTP method (HIGH)

**Why it matters:** Only `shouldQueryFiveCharacterUppercasePrefixWithUserAgentHeader` inspects the
received path. The other tests start the same server and rely on its body, so a regression that
sent the wrong prefix, dropped `/range/`, or changed the HTTP method would still pass because the
root context returns the fixture response regardless.

**Suggested test:** Make the handler context path-specific (`/range/{prefix}`) or assert the captured
HTTP method and exact path in every test that starts the server. At minimum, extend the existing
path test to also assert the method is `GET`, and add a failure-mode test where the server returns
404 for paths other than `/range/{prefix}` to prove the client constructs the expected URI.

---

## Gap 2 — `PasswordPolicyProperties` validation added in Phase 9 is not covered by tests (HIGH)

**Why it matters:** Phase 9 tightened `minLength`/`maxLength` to L2's 12/128 range, added an
`@AssertTrue` cross-field check, and capped `timeoutMs` at `Integer.MAX_VALUE`. Without tests, a
future refactor could regress the bounds (e.g., revert `@Min(1)`) and the suite would still pass.

**Suggested test:** Add a plain-JUnit validation test using
`jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator()` (no Spring context)
to assert violations for:
- `minLength = 8` or `maxLength = 5` (outside L2),
- `minLength = 100, maxLength = 20` (cross-field failure), and
- `timeoutMs = Integer.MAX_VALUE + 1L` (overflow bound),
and assert that `12/128` with `urlPrefix = "..."` and `timeoutMs = 3000` is valid.

---

## Gap 3 — `BreachCheckClient`'s trailing-slash normalization is untested (MEDIUM)

**Why it matters:** Phase 9 added normalization so a `urlPrefix` missing a trailing slash still
produces the correct request path. The current test suite always constructs `urlPrefix` with a
trailing slash, so a regression in `buildRestClient` normalization would not be caught and could
silently fail-open under R10.

**Suggested test:** Parameterize `startServer` (or add a dedicated test) to pass a `urlPrefix` of
`http://127.0.0.1:<port>/range` (no slash) and assert the received request path is still
`/range/{prefix}`.

---

## Gap 4 — Connection-failure test uses hardcoded port 1, which is not guaranteed closed (MEDIUM)

**Why it matters:** `shouldThrowBreachCheckUnavailableExceptionOnConnectionFailure` connects to
`127.0.0.1:1`. That port is usually closed, but on some systems or CI images it could be filtered,
open, or time out instead of refusing immediately, producing flakiness.

**Suggested test:** Replace port 1 with a closed ephemeral port: start an `HttpServer` on port 0,
immediately stop it, then point the `BreachCheckClient` at the now-closed address. This guarantees a
connection-refused error deterministically.

---

## Gap 5 — No test exercises an actual timeout (MEDIUM)

**Why it matters:** The frozen brief requires a bounded timeout so fail-open cannot hang. The
existing connection-refused test covers one `RestClientException` path, but it does not prove the
timeout factory actually bounds a slow response.

**Suggested test:** Add a server handler that sleeps longer than the configured `timeoutMs` (use a
short test value such as `50`) and assert `BreachCheckUnavailableException` is thrown quickly. Time
the test to ensure it does not wait the full default 3000ms.

---

## Gap 6 — Parser tolerance for malformed lines and whitespace around the colon is not tested (MEDIUM)

**Why it matters:** The production parser ignores blank lines, trims suffix/count, and skips lines
with invalid counts or missing colons. A regression in any of these rules could produce false
breach positives or negatives.

**Suggested test:** Add a response body such as `"garbage-no-colon\r\n" + suffix + " : 1 \r\nnot-a-number:xyz\r\n"`
and assert the password is treated as breached. Also add a body where the matching suffix line has
count `0` and another line has a positive count for a non-matching suffix, asserting not-breached.

---

## Gap 7 — No test confirms audit service is *not* called on normal, successful password validation (LOW/MEDIUM)

**Why it matters:** `PasswordPolicy` should only emit a `password.breach_check_failed` audit event
when the range API is unavailable. A regression that records the event on every validation (or a
misreading of the spec) would be caught only by an integration test, not by the current unit suite.

**Suggested test:** In `PasswordPolicyTest`, after a successful validation with a non-breached
password (e.g., in the 12/128 boundary test or a new dedicated test), add `verifyNoInteractions(auditService)`.

---

## Gap 8 — The named R8 test does not prove breach check is avoided for the too-long password case (LOW)

**Why it matters:** `shouldRejectPasswordShorterThan12OrLongerThan128` asserts
`verifyNoInteractions(breachCheckClient)` after the 11-character assertion but before the
129-character assertion. If a future change called the breach client for overly long passwords,
the test would still pass because the first assertion already happened, but the second case would
perform an unnecessary HIBP call.

**Suggested test:** Move `verifyNoInteractions(breachCheckClient)` to after both assertions, or split
the test into two cases (`rejectsShorterThan12` and `rejectsLongerThan128`) each asserting no breach
client interaction.

---

## Summary

The test suite now convincingly covers R8–R10, the named tests, and most Phase 3/8/9 edge cases.
The biggest remaining risks are false positives caused by the root-context server (Gap 1) and the
absence of validation tests for Phase 9's new constraints (Gap 2). Gaps 3–6 are targeted hardening
for things the production code now does but the tests do not exercise; Gaps 7–8 tighten weak
assertions.
