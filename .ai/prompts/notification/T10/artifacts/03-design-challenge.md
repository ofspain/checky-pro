<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge). -->

# notification · T10 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T10 — Secret-safe logging |
| **Spec section** | Secret-safe rendering & logging |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

Adversarial review of the T10 Phase 2 brief.

---

## Finding 1 · The static scan only checks for presence of `toString()`, not that it excludes the sensitive field

**Severity:** High

**Evidence:**
- AC5 requires scanning for fields named `token`/`secret`/`password`/`apiKey`/`key` and asserting the file "also declares its own `toString()` method."
- A class could satisfy this by declaring `public String toString() { return "EmailRequestedEvent[accountUuid=" + accountUuid + ", token=" + token + "]"; }` — the scan would pass, but the token would still leak.
- The brief explicitly acknowledges the scan cannot catch semantically-sensitive but ambiguously-named fields like `RenderedMessage.body`, but it does not acknowledge that the scan also cannot verify the *content* of the `toString()` it requires.

**Recommended brief amendment:**
Tighten AC5 so the static scan also asserts that the declared `toString()` body does not reference the sensitive field name(s) found in that file. This can still be done as a source-text scan (no reflection): after locating a sensitive field `foo`, assert the file's `toString()` method body does not contain `foo` (allowing for simple getter calls like `getFoo()` only if they are also absent, or requiring the field name itself not appear). This is stricter and actually enforces the L4 property the scan is meant to guard.

---

## Finding 2 · The field-name heuristic will false-positive on non-secret fields named `key`

**Severity:** Medium

**Evidence:**
- The scan matches fields named `key` (case-insensitively). A field named `key` could be a Kafka message key, a cache key, a map key, or any other non-secret identifier.
- Requiring such a file to declare `toString()` is harmless but creates noise; more importantly, it trains future authors to add a perfunctory `toString()` to satisfy the scan rather than thinking about what is actually sensitive.

**Recommended brief amendment:**
Either remove `key` from the heuristic (keep only `token`, `secret`, `password`, `apiKey`) or require that the matched field also has a sensitive-looking type (e.g., `String`) and is not annotated with an obviously non-secret marker. The simpler fix is to drop `key` from the scan list; the remaining four terms have a much lower false-positive rate.

---

## Finding 3 · `SecretSafeLogging` should be a non-instantiable utility class

**Severity:** Low

**Evidence:**
- The brief describes `SecretSafeLogging` as a "static redaction utility."
- A public class with only static methods but a public default constructor can still be instantiated, which is misleading for a utility.

**Recommended brief amendment:**
Add a private constructor (and possibly `final` class modifier) to `SecretSafeLogging`, with a comment explaining it is a static utility. Add a test asserting instantiation throws `AssertionError` or `UnsupportedOperationException`, mirroring common utility-class conventions.

---

## Finding 4 · The `redact()` regex does not cover JSON-shaped secrets or bearer tokens

**Severity:** Medium

**Evidence:**
- The regex `(?i)(token|secret|api_?key|password)=([^&\s]*)` matches `key=value` query-string shapes.
- It does not match `"token": "abc"` (JSON), `Authorization: Bearer abc` (HTTP header), or `token: abc` (YAML/log line without equals).
- Future `DeliveryOrchestrator` logging might include structured JSON log fields or headers, where secrets could slip through.

**Recommended brief amendment:**
Document the explicit scope of `redact()` (query-string / `key=value` forms only) and note the known limitations. If the intent is broader, extend the regex or add a second method for JSON/header-style redaction. For T10's own scope, documentation is sufficient.

---

## Finding 5 · No test verifies `redact()` against a rendered message body containing a real computed link

**Why it matters:** The ultimate purpose of `SecretSafeLogging` is to protect log lines that might contain rendered message bodies or computed links with embedded tokens. A test that passes a rendered `email.password_reset` body through `redact()` and asserts the raw token does not appear would prove the utility works against the actual output of T09.

**Recommended brief amendment:**
Add an integration-style test (or unit test using a real rendered body string) that:
- renders `email.password_reset` with a known token;
- passes the resulting body through `SecretSafeLogging.redact()`;
- asserts the output does not contain the raw token and does contain `token=***`.

This closes the loop between T09's computed links and T10's redaction.

---

## Finding 6 · The `redact()` regex value class `[^&\s]*` may not match secrets in all realistic log contexts

**Severity:** Low

**Evidence:**
- The value class stops at `&` or whitespace. This works for URL query parameters.
- In a plain log line like `user entered password=hello world`, it would only redact `password=hello`, leaving `world` exposed.
- In JSON `{"password":"secret"}`, the closing quote is not `&` or whitespace, so it would redact the whole value (good), but the key shape doesn't match in the first place (Finding #4).

**Recommended brief amendment:**
Either document that `redact()` is designed for URL query-string-like input, or broaden the value terminator class. If kept narrow, add a test proving that `password=hello world` redacts only up to the space and document that behavior.

---

## Finding 7 · No mention of whether the static-scan test should also cover `record` canonical constructors or components

**Severity:** Low

**Evidence:**
- Java records auto-generate a `toString()` that prints all components. A record with a component named `token` would pass a scan that only checks for an explicit `toString()` declaration, but the auto-generated `toString()` would leak the token.
- The current codebase already has `EmailRequestedEvent` (a record) with a manual `toString()` override, but a future record might not.

**Recommended brief amendment:**
Specify that the scan must treat records specially: if a record component matches a sensitive name, the record must declare an explicit `toString()` override (not rely on the auto-generated one). The scan can detect `record Foo(...)` syntax and the presence/absence of an explicit `toString()`.

---

## Finding 8 · No mention of updating `T01SkeletonRegressionTest` for `SecretSafeLogging.java`

**Severity:** Low

**Evidence:**
- The brief acknowledges the historical pattern that `T01SkeletonRegressionTest` needs updating for each task's new production files, but it does not list `T01SkeletonRegressionTest` under "Files to Modify."
- T10 adds `common/SecretSafeLogging.java` to the production file list.

**Recommended brief amendment:**
Add `T01SkeletonRegressionTest.java` to "Files to Modify" with a note to include `common/SecretSafeLogging.java` in the authorized production file list.

---

## Summary

The T10 brief is narrowly scoped and consistent with L4/R15: it adds a reusable redaction utility and a static scan to enforce the existing per-class `toString()` discipline. The most consequential gap is **Finding 1**: a static scan that only checks for the *presence* of `toString()` does not actually enforce that the sensitive field is excluded, which is the property L4 cares about. **Finding 2** reduces false positives in the heuristic. **Finding 4** documents realistic limitations of the `redact()` regex. Findings 3 and 5–8 are smaller precision, coverage, and bookkeeping items.
