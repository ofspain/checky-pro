<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review). -->

# notification · T09 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T09 — Template renderer |
| **Spec section** | Template rendering |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` + T09 test files |
| **Produces** | `artifacts/11-test-review.md` |

Review of the T09 regression-guard tests against the acceptance criteria and task statement.

---

## Gap 1 · No automated negative-proof that computed links override caller-supplied values

**Why it matters:** AC6 requires that the five computed link placeholders are produced by `TemplateRenderer`, not read from `eventData`. The `computedLinkPlaceholdersOverrideCallerSuppliedValues` test proves the behavior, but the Phase 10 artifact documents a one-time manual mutation test (swapping the merge order) that proves the test catches a regression. If a future refactor accidentally reverts the merge order, the build will not fail until someone re-runs that manual check.

**Suggested test:** Add a plain JUnit test that reads `TemplateRenderer.java` as text and asserts the `render` method builds `values` by first copying `eventData` and then overlaying `computeLinkPlaceholders(eventData)` (i.e., `values.putAll(eventData)` followed by `values.putAll(computeLinkPlaceholders(eventData))`). This is a cheap, permanent regression guard for the exact precedence property.

---

## Gap 2 · No integration test exercises `getStartedLink` / `user.welcome`

**Why it matters:** The integration tests render `email.verify`, `email.password_reset`, and `user.verify` (IN_APP), but never `user.welcome` which is the only seeded template that uses `{{getStartedLink}}`. A regression that broke `getStartedLink` computation specifically would not be caught by the current integration suite.

**Suggested test:** Add an integration test method that renders `user.welcome`/`EMAIL` (or `IN_APP`) and asserts the body contains the normalized `baseUrl` value as `{{getStartedLink}}`.

---

## Gap 3 · No test explicitly asserts the `resetLink` path shape

**Why it matters:** The integration test `tokenWithReservedUrlCharactersIsUrlEncodedInTheComputedLink` uses `email.password_reset` and asserts the token is URL-encoded, but it does not assert the path is `/reset-password`. A regression that accidentally used `/verify-email` for both `verificationLink` and `resetLink` would still pass the URL-encoding test.

**Suggested test:** Add an assertion that the rendered `email.password_reset` body contains `https://checky.pro/reset-password?token=...` (and does not contain `/verify-email`). This locks the path convention for password-reset links.

---

## Gap 4 · No test for `RenderedMessage.toString()` with null subject/body

**Why it matters:** The existing test proves `toString()` excludes non-null subject/body content. If `subject` or `body` is null, a naively overridden `toString()` might still include the literal string `"null"` or behave differently. The current implementation's `toString()` override is not shown in the production code; the test relies on the record's auto-generated `toString()` plus whatever override exists.

**Suggested test:** Add an assertion in `renderedMessageToStringExcludesSubjectAndBodyContent` (or a new test) that constructs a `RenderedMessage(null, null, 1)` and asserts its `toString()` contains `version=1` but does not contain `"null"` for subject/body.

---

## Gap 5 · No test for `baseUrl` with a path segment

**Why it matters:** `normalizeBaseUrl` only strips a trailing slash. If `baseUrl` is configured as `https://checky.pro/app`, the rendered links become `https://checky.pro/app/verify-email?token=...`. This is a plausible configuration, but it is not tested. A future change that normalized the baseUrl differently (e.g., stripping everything after the host) would silently break deployments that use a path prefix.

**Suggested test:** Add a unit test with `LinkProperties("https://checky.pro/app")` and assert the computed `verificationLink` equals `https://checky.pro/app/verify-email?token=abc`.

---

## Gap 6 · No test for empty-string eventData values

**Why it matters:** The tests cover missing keys, explicit `null` values, and values with special characters, but not empty strings. An empty string is a valid `Map<String, String>` value and should render as empty. While this is the expected behavior, a regression that treated empty strings as missing could affect rendering.

**Suggested test:** Add a unit test that passes `Map.of("displayName", "")` and asserts the rendered output contains an empty string where the placeholder was.

---

## Gap 7 · No test verifies `TemplateRenderer` does not mutate the input `eventData` map

**Why it matters:** `render` creates a new `HashMap<>(eventData)` before adding computed links, so it does not mutate the caller's map. This is a good property, but a future refactor might accidentally use `eventData.putAll(...)` directly.

**Suggested test:** Add a unit test that passes an immutable/unmodifiable `Map.of(...)` and asserts no exception is thrown and the original map still has only the caller-supplied entries. This is a low-priority gap because the current code obviously copies.

---

## Gap 8 · No test verifies `Template` entity mapping directly

**Why it matters:** The integration tests exercise the entity indirectly. A drift in `@Column` names would surface as a render failure, but a dedicated mapping test makes the diagnosis faster.

**Suggested test:** Add a small test that fetches a seeded template via `templateRepository.findTopByNameAndChannelOrderByVersionDesc(...)` and asserts all getters return expected values. Low priority; existing tests already fail on mapping drift.

---

## Summary

The T09 test suite is now comprehensive: 17 new tests cover basic rendering, missing/null placeholders, regex-safety, version surfacing, unknown-template exception, link-placeholder precedence, malformed placeholders, token-safe `toString()`, blank/null `baseUrl`, null-argument rejection, real-seed rendering, URL encoding, IN_APP null subject, and versioned lookup. The strongest remaining gap is **Gap 1**: the manual mutation test that proves link-placeholder precedence is not encoded as an automated regression guard. Gaps 2–6 are smaller coverage items around specific link types, `toString()` edge cases, and `baseUrl` shapes. Gaps 7–8 are minor precision tests.
