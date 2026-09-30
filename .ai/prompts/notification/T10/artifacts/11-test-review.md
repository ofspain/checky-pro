<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review). -->

# notification · T10 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T10 — Secret-safe logging |
| **Spec section** | Secret-safe rendering & logging |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` + T10 test files |
| **Produces** | `artifacts/11-test-review.md` |

Review of the T10 regression-guard tests against the acceptance criteria and task statement.

---

## Gap 1 · No automated negative-proof that the static scan catches real leaks

**Why it matters:** The core correctness property of T10 — that sensitive fields never leak through `toString()` — rests on `SensitiveFieldsHaveSafeToStringTest.everyRealProductionFileWithASensitiveFieldHasASafeToString`. The Phase 10 artifact documents a one-time manual mutation test (adding `token` to `EmailRequestedEvent.toString()`), but if a future refactor accidentally weakens the scan regex or the `toString()` body checker, the build will not fail until someone re-runs that manual check.

**Suggested test:** Add a plain JUnit test that reads `SensitiveFieldsHaveSafeToStringTest.java` as text and asserts it contains both the sensitive-field regex and the `toString()` body reference check (i.e., the `toStringBodyReferencesField` logic or equivalent). Alternatively, add a test that temporarily mutates a known safe fixture string and asserts the scan catches it — but keep it automated, not manual.

---

## Gap 2 · The static scan only recognizes `String`-typed fields

**Why it matters:** `SENSITIVE_FIELD_PATTERN` is `(?i)\bString\s+(token|secret|password|apiKey|api_key)\b`. It will miss:
- fields declared as `java.lang.String token`;
- fields of other secret-carrying types such as `byte[] password` or `char[] apiKey`;
- multi-field declarations like `private String token, secret;` (only the first field is matched).

The current codebase uses only `String` single-field declarations, so the scan passes today. A future secret field using a different type or declaration style would silently bypass the guard.

**Suggested test:** Add synthetic-fixture tests proving the scan catches violations in:
- a fully-qualified `java.lang.String token` field;
- a multi-field declaration `String token, secret`;
- (optional) a non-`String` type such as `byte[] password`.

Then update the regex to handle these shapes, or document them as known limitations.

---

## Gap 3 · The static scan extracts only the first `toString()` body in a file

**Why it matters:** `extractToStringBody` returns the body of the first method matching `public String toString() {` in the source file. If a file contains multiple top-level or nested classes, and a sensitive field appears in a class whose `toString()` is not the first one in the file, the scan may check the wrong body.

**Suggested test:** Add a synthetic-fixture test with two classes in one file: the first class has a safe `toString()` and no sensitive field; the second class has `String token` and a leaking `toString()`. Assert the scan catches the leak in the second class. Then update `extractToStringBody` to find the `toString()` that belongs to the same class as the sensitive field (e.g., by tracking the nearest preceding `class`/`record` declaration).

---

## Gap 4 · The static scan may match sensitive field names inside comments

**Why it matters:** `SENSITIVE_FIELD_PATTERN` has no awareness of Java comment boundaries. A file containing a comment like `// String token is not used here` would be flagged as having a sensitive field, requiring a `toString()` even though no actual field exists.

**Suggested test:** Add a synthetic-fixture test with a source file that mentions `String token` only inside a comment and no actual field declaration, and assert the scan reports no violation. Then update the scanner to strip `//` and `/* */` comments before running the field regex, or document this as a known limitation.

---

## Gap 5 · No test verifies `redact()` handles values containing `=`

**Why it matters:** The regex value class `[^&\s]*` includes `=`, so `token=a=b` should redact to `token=***`. This is a realistic query-string case (e.g., a Base64 token with padding), but it is not explicitly tested.

**Suggested test:** Add a unit test asserting `SecretSafeLogging.redact("token=a=b&next=value")` equals `token=***&next=value`.

---

## Gap 6 · No test verifies the scan accepts a safe record with an explicit `toString()` override

**Why it matters:** `scanAcceptsAToStringThatExcludesTheSensitiveField` uses a class fixture. Records have different syntax and auto-generate `toString()`, so a record-specific positive test adds confidence that the scan handles the record case correctly when the override is present.

**Suggested test:** Add a synthetic-fixture test with a record that has a `String token` component and an explicit `public String toString() { return "redacted"; }`, and assert the scan reports no violation.

---

## Gap 7 · No integration test for redaction on the `IN_APP` channel

**Why it matters:** `SecretSafeLoggingIntegrationTest` renders `email.password_reset`/`EMAIL` and `email.verify`/`EMAIL`. The `IN_APP` channel also renders verification links containing tokens (`user.verify`/`IN_APP`). A channel-specific bug in `TemplateRenderer` that only affected `IN_APP` link formatting would not be caught by the current integration tests.

**Suggested test:** Add an integration test rendering `user.verify`/`IN_APP` with a token and asserting `redact()` removes the raw token. This is low priority because the unit tests already cover the regex and the EMAIL integration covers the real `TemplateRenderer` path.

---

## Summary

The T10 test suite is now strong: 18 new tests cover `redact()` correctness, the static scan's real-codebase enforcement, synthetic fixtures for leaking/record/safe classes, private-constructor utility shape, and real rendered-body redaction against T09 output. The scan was improved at Phase 10 to check `toString()` content (not just presence) and to handle records, addressing the strongest Phase 8 concern. The remaining gaps are mostly edge cases in the static-scan regex/parser (multi-class files, comments, multi-field declarations, fully-qualified types) and a few additional `redact()`/integration scenarios.
