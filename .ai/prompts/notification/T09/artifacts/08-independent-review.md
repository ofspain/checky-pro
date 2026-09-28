<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Code Review). -->

# notification · T09 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T09 — Template renderer |
| **Spec section** | Template rendering |
| **Model** | Kimi 2.7 |
| **Consumes** | Phase 6 implementation + `artifacts/07-self-review.md` |
| **Produces** | `artifacts/08-independent-review.md` |

Fresh adversarial review of the completed T09 implementation. Findings only.

---

## Finding 1 · No T09-specific tests exist yet

**Issue:** AC1–AC7 and the `package.md` §8 named test (`shouldRenderTemplateWithEventDataAndSelectedChannel`) require automated tests. The codebase currently has no `TemplateRendererTest` or `TemplateRendererIntegrationTest`.

**Evidence:**
- `services/notification/src/test/java/com/themistra/notification/template/` does not exist.
- No test references `TemplateRenderer`, `TemplateRepository`, or `Template`.

**Recommendation:** Add the missing tests before considering T09 complete:
- `TemplateRendererTest` (mocked repository): template substitution, missing/empty placeholders, `$`/`\` safety in event data, version surfaced in `RenderedMessage`, unknown template exception, link placeholders overriding caller-supplied values.
- `TemplateRendererIntegrationTest` (Testcontainers Postgres with real seeded V3 rows): real seeded template rendering, versioned lookup highest-wins, URL encoding of tokens/UUIDs, trailing-slash normalization, blank/null `baseUrl` behavior.

**Confidence:** High

---

## Finding 2 · `RenderedMessage` auto-generated `toString()` may leak PII and tokens

**Issue:** `RenderedMessage` is a Java record, so it auto-generates `toString()` that prints `subject`, `body`, and `version`. The rendered `body`/`subject` can contain the recipient's name, email, invoice amounts, and — most critically — raw verification/reset tokens embedded in computed links. Logging a `RenderedMessage` would violate `agents.md` L4 ("Never log tokens, secrets, reset-token values ... or PII").

**Evidence:**
- `TemplateRenderer.java` line 47: `public record RenderedMessage(String subject, String body, int version) {}`.
- No `toString()` override excludes sensitive fields.
- The rendered `body` for `email.verify`/`email.password_reset` contains the full `verificationLink`/`resetLink`, including the raw URL-encoded token.

**Recommendation:** Override `RenderedMessage.toString()` to include only `version` (and optionally a non-sensitive indicator such as a truncated hash of body length), never the actual `subject`/`body` content. Add a test asserting that a rendered token does not appear in `toString()`.

**Confidence:** Medium

---

## Finding 3 · No test verifies computed link placeholders override caller-supplied values (AC6)

**Issue:** AC6 requires that `verificationLink`/`resetLink`/`getStartedLink`/`invoiceLink`/`receiptLink` are computed by `TemplateRenderer`, not read directly from `eventData`. The implementation correctly overlays computed links on top of `eventData`, but no test locks this behavior.

**Evidence:**
- `TemplateRenderer.java` lines 70–71: `values.putAll(eventData)` then `values.putAll(computeLinkPlaceholders(eventData))`.
- No test passes a caller-supplied `verificationLink` and asserts the rendered output uses the computed link instead.

**Recommendation:** Add a unit test that calls `render(..., Map.of("verificationLink", "CALLER_SUPPLIED", "token", "abc"))` and asserts the rendered body contains the computed `baseUrl + "/verify-email?token=abc"`, not `"CALLER_SUPPLIED"`.

**Confidence:** Medium

---

## Finding 4 · No test verifies `RenderedMessage.version()` equals the fetched template version

**Issue:** AC4 requires the rendered message to surface the template version. The implementation returns `template.getVersion()`, but no test asserts this.

**Evidence:**
- `TemplateRenderer.java` line 75: `return new RenderedMessage(subject, body, template.getVersion())`.
- No test verifies the `version` field of the returned record.

**Recommendation:** Add a unit test with a mocked repository returning a template with a specific `version` (e.g., `7`) and assert `renderedMessage.version() == 7`.

**Confidence:** Medium

---

## Finding 5 · No test verifies blank/null `baseUrl` behavior

**Issue:** `normalizeBaseUrl` returns an empty string when `baseUrl` is null and strips a trailing slash. This behavior is reasonable but untested. A regression that removed null handling or changed it to throw would not be caught.

**Evidence:**
- `TemplateRenderer.java` lines 104–109.
- No test calls `render` with a `LinkProperties` bean whose `baseUrl` is null or blank.

**Recommendation:** Add integration tests (or unit tests with a mock `LinkProperties`) asserting:
- `baseUrl = null` produces relative links like `/verify-email?token=...`;
- `baseUrl = "https://checky.pro/"` produces `https://checky.pro/verify-email?token=...` (single slash).

**Confidence:** Low

---

## Finding 6 · No test verifies the placeholder regex rejects invalid keys

**Issue:** `PLACEHOLDER_PATTERN` only matches keys starting with a letter and followed by letters/digits/underscores. This is correct, but no test documents or locks the behavior for malformed placeholders.

**Evidence:**
- `TemplateRenderer.java` line 37: `Pattern.compile("\\\\{\\\\{([a-zA-Z][a-zA-Z0-9_]*)}}")`.
- No test exercises `{{}}`, `{{123}}`, `{{a-b}}`, or unclosed `{{key}`.

**Recommendation:** Add a test asserting that malformed/invalid placeholders are left as literal text in the rendered output (or rendered empty, whichever is the documented behavior). This prevents a future regex change from silently accepting invalid keys.

**Confidence:** Low

---

## Finding 7 · No test verifies `TemplateRenderer` is a Spring bean

**Issue:** `TemplateRenderer` is `@Service`-scanned and will be injected by task 11's `DeliveryOrchestrator`. No current test autowires it.

**Evidence:**
- `TemplateRenderer.java` line 34: `@Service`.
- No existing `@SpringBootTest` autowires `TemplateRenderer`.

**Recommendation:** Add a small `@SpringBootTest` (or extend an existing integration test) that autowires `TemplateRenderer` and asserts it is not null. This is a low-priority gap because the integration tests for T09 will exercise it.

**Confidence:** Low

---

## Finding 8 · `TemplateRepository` still exposes inherited mutators

**Issue:** Same class of concern as T05/T08: extending `JpaRepository` makes `save`/`delete` available even though T09 is read-only. The Javadoc acknowledges this and notes the `SELECT`-only grant makes accidental writes fail at the DB, but the API surface still contradicts the task's intent.

**Evidence:**
- `TemplateRepository.java` line 15: `extends JpaRepository<Template, Long>`.
- Lines 7–14 document the deviation.

**Recommendation:** If the frozen brief permits, change to `extends Repository<Template, Long>` and explicitly declare only `findTopByNameAndChannelOrderByVersionDesc`. If not permitted, the current Javadoc warning is sufficient.

**Confidence:** Low

---

## Summary

The T09 production code is well-implemented: it uses `Matcher.quoteReplacement` for safe substitution, URL-encodes tokens/UUIDs, normalizes `baseUrl` (including trailing slash and null), surfaces the template version, and throws on unknown templates. The `V7` grant and the T01/T02 regression-test updates are correct. The dominant issue is the absence of committed T09-specific tests (Finding 1). Finding 2 is a real `agents.md` L4 concern: the auto-generated `RenderedMessage.toString()` would leak tokens and PII if ever logged. Findings 3–7 are coverage items that Phase 10 should add. Finding 8 repeats the `JpaRepository` API-surface concern from earlier tasks.
