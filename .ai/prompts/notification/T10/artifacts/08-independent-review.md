<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Code Review). -->

# notification · T10 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T10 — Secret-safe logging |
| **Spec section** | Secret-safe rendering & logging |
| **Model** | Kimi 2.7 |
| **Consumes** | Phase 6 implementation + `artifacts/07-self-review.md` |
| **Produces** | `artifacts/08-independent-review.md` |

Fresh adversarial review of the completed T10 implementation. Findings only.

---

## Finding 1 · No T10-specific tests exist yet

**Issue:** AC1–AC5 require automated tests: `redact()` correctness (single match, multiple matches, case-insensitivity, no-match passthrough, `null` input) and the static-scan test for sensitive-field `toString()` discipline. The codebase currently has no `SecretSafeLoggingTest`.

**Evidence:**
- `services/notification/src/test/java/com/themistra/notification/common/` contains only T03 tests; no `SecretSafeLoggingTest.java`.
- No test references `SecretSafeLogging` or its `redact` method.

**Recommendation:** Add the missing tests before considering T10 complete:
- `SecretSafeLoggingTest` (plain JUnit): the five `redact()` correctness cases plus a test that `SecretSafeLogging` cannot be instantiated.
- A static-scan test that walks `src/main/java/com/themistra/notification`, finds files declaring sensitive fields, and asserts each such file declares a safe `toString()` (see Finding 2).

**Confidence:** High

---

## Finding 2 · The static-scan test design only checks presence of `toString()`, not that it excludes the sensitive field

**Issue:** AC5 (as written in the brief) requires a scan that asserts a file with a sensitive field "also declares its own `toString()` method." A class could satisfy this with a `toString()` that still prints the sensitive field. The implementation currently has no scan test at all, so this is the right moment to ensure the test actually enforces the L4 property.

**Evidence:**
- `SecretSafeLogging.java` exists, but there is no static-scan test file.
- The brief's AC5 does not require verifying the *content* of the declared `toString()`.

**Recommendation:** When implementing the static-scan test in Phase 10, make it assert both:
1. the file declares an explicit `toString()` method (not relying on record auto-generation), and
2. the body of that `toString()` does not reference the sensitive field name(s) found in the same file.

This can still be done as a source-text scan without reflection. Also handle records specially: a record with a sensitive component must declare an explicit `toString()` override, since the auto-generated one would leak.

**Confidence:** High

---

## Finding 3 · Inconsistency between static-scan field list and `redact()` regex

**Issue:** AC5's static-scan field list includes `key`, but `SecretSafeLogging.redact()`'s regex `(?i)(token|secret|password|api_?key)=([^&\s]*)` does not redact a standalone `key=value` substring. A field named `key` might be flagged by the scan, but the redaction utility would not protect a log line containing `key=somevalue`.

**Evidence:**
- Brief AC5: scan matches fields named `token`/`secret`/`password`/`apiKey`/`key`.
- `SecretSafeLogging.java` line 20: regex matches only `token`, `secret`, `password`, `apikey`/`api_key`.

**Recommendation:** Align the two lists. Either:
- remove `key` from the static-scan heuristic (it has a high false-positive rate anyway), or
- add `key` to the `redact()` regex if the intent is genuinely to treat any `key=value` as sensitive.

The first option is safer and avoids false positives on legitimate non-secret `key` fields (e.g., Kafka record keys).

**Confidence:** Medium

---

## Finding 4 · No test verifies `SecretSafeLogging` cannot be instantiated

**Issue:** The class is correctly declared `final` with a private constructor, but no test locks this utility-class shape.

**Evidence:**
- `SecretSafeLogging.java` lines 17 and 22–24: `final` class + private constructor.
- No test asserts instantiation is prevented.

**Recommendation:** Add a small unit test that attempts `new SecretSafeLogging()` (via reflection, since the constructor is private) and asserts it throws `IllegalAccessException`/`InstantiationException` or that the constructor exists and is private.

**Confidence:** Low

---

## Finding 5 · No committed test verifies `redact()` against a real rendered message body

**Issue:** The self-review confirmed via a scratch test that `redact()` removes a real token from a rendered `email.password_reset` body, but that test was deleted. No committed test closes the loop between T09's `TemplateRenderer` and T10's redaction.

**Evidence:**
- `SecretSafeLogging.java` Javadoc says it is for "future logging code" and "free-form content."
- No test in `src/test` renders a template and then redacts the result.

**Recommendation:** Add a test (could be in `SecretSafeLoggingTest` or `TemplateRendererIntegrationTest`) that:
- renders `email.password_reset` with a known token;
- passes the rendered body through `SecretSafeLogging.redact()`;
- asserts the raw token does not appear and `token=***` does appear.

This is the single most load-bearing scenario for this utility.

**Confidence:** Medium

---

## Finding 6 · No test documents the regex's boundary behavior for values containing whitespace

**Issue:** The Javadoc explicitly documents that `redact("password=hello world")` masks only `"hello"`, leaving `"world"` exposed. This is intentional but unusual; a test should lock it so a future edit does not silently broaden or narrow the behavior.

**Evidence:**
- `SecretSafeLogging.java` lines 31–36 document the boundary behavior.
- No committed test asserts it.

**Recommendation:** Add a unit test asserting exactly the documented `password=hello world → password=*** world` behavior.

**Confidence:** Low

---

## Finding 7 · No test verifies `redact()` does not alter non-matching text byte-for-byte

**Issue:** AC3 requires text with no secret-shaped substring returns unchanged. The implementation will return a new string that is equal to the input, but no test locks this.

**Evidence:**
- `SecretSafeLogging.java` line 49: `matcher.replaceAll(...)` returns a string.
- No test asserts passthrough behavior for non-matching input.

**Recommendation:** Add a unit test that passes arbitrary non-matching text (including special characters, unicode, etc.) and asserts the output equals the input exactly.

**Confidence:** Low

---

## Summary

The T10 production code is clean and consistent with the frozen brief: `SecretSafeLogging` is a `final` utility class with a private constructor, its regex correctly redacts `token`/`secret`/`password`/`apikey`/`api_key`-shaped query-string values while preserving key casing, and it returns `null` for `null` input. The `T01SkeletonRegressionTest` update is correct. The dominant issue is the complete absence of committed tests (Finding 1). Finding 2 is the most important quality concern: when the static-scan test is added, it must verify `toString()` content, not just presence, or it will not enforce L4. Finding 3 flags a real inconsistency between the scan's field list and the redaction regex. Findings 4–7 are smaller coverage items.
