<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review). -->

# notification · T08 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T08 — Preference resolver |
| **Spec section** | Preference resolution |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` + T08 test files |
| **Produces** | `artifacts/11-test-review.md` |

Review of the T08 regression-guard tests against the acceptance criteria and task statement.

---

## Gap 1 · No automated negative-proof that the `SECURITY`+`EMAIL` early return is required

**Why it matters:** The core correctness property of T08 — that `SECURITY`+`EMAIL` cannot be suppressed even by a stored opt-out — rests on the early `return true` before the repository query. The Phase 10 artifact documents a one-time manual mutation test, but if a future refactor accidentally removes the early return, the build will not fail until someone re-runs that manual check. (The `DEFAULTS` map alone would still return `true` for `SECURITY`+`EMAIL` when no row exists, so only the adversarial-stored-row case would break.)

**Suggested test:** Add a plain JUnit test that reads `PreferenceResolver.java` as text and asserts the `resolve` method contains the early-return guard `if ("SECURITY".equals(normalizedCategory) && "EMAIL".equals(normalizedChannel)) { return true; }` before any repository call. This is a cheap, permanent regression guard for the exact unconditional property.

---

## Gap 2 · No test for `SECURITY`/`IN_APP` opt-out or `SECURITY`/`EMAIL` stored-true row

**Why it matters:** The suite covers the hard floor for `SECURITY`/`EMAIL` with a stored `false` row and the default for `SECURITY`/`IN_APP` (true). It does not explicitly cover:
- a stored `SECURITY`/`IN_APP` row with `enabled=false` should suppress (normal stored-row precedence);
- a stored `SECURITY`/`EMAIL` row with `enabled=true` should still resolve `true` (trivially true, but documents the floor works in both directions).

**Suggested test:** Add two small assertions in the unit or integration test:
- `resolver.resolve(accountUuid, "SECURITY", "IN_APP")` returns `false` when a stored `enabled=false` row exists;
- `resolver.resolve(accountUuid, "SECURITY", "EMAIL")` returns `true` when a stored `enabled=true` row exists.

---

## Gap 3 · No test for stored rows on unsupported channels/categories

**Why it matters:** The implementation returns the stored row's `enabled` value for any `(category, channel)` pair, even `WEBHOOK`/`PUSH` or unknown categories. This is consistent with "stored row takes precedence," but it is untested. A future reader might assume unsupported channels always resolve `false` regardless of stored rows.

**Suggested test:** Add an integration test that inserts a row for `PAYMENT`/`WEBHOOK` with `enabled=true` and asserts `resolve` returns `true`. This documents and locks the "stored row takes precedence over unsupported-channel default" semantics.

---

## Gap 4 · No test for whitespace or empty-string inputs

**Why it matters:** `PreferenceResolver.resolve` uppercases its inputs but does not trim them. A caller passing `" SECURITY "` or `""` would produce a normalized key that does not match any default or stored row. The current behavior (return the stored row if one happens to match the whitespace, otherwise fall back to `false`) is probably unintended for whitespace but is not specified.

**Suggested test:** Either add a test that documents the current behavior (e.g., `" SECURITY "` falls back to default only if the repository matches it, otherwise `false`) or update the implementation to `.trim().toUpperCase(...)` and add a test that trimmed inputs match. The latter is more robust and aligns with the intent of case-insensitive matching.

---

## Gap 5 · No test verifies `ChannelPreference` entity mapping directly

**Why it matters:** The integration tests exercise the entity indirectly through `PreferenceResolver`, but no test asserts the entity maps the six columns correctly (e.g., `category`, `channel`, `enabled`, `updatedAt`). A drift in the `@Column` names would only surface in the resolver tests, making diagnosis harder.

**Suggested test:** Add a small test that reads a stored row via `channelPreferenceRepository.findByAccountUuidAndCategoryAndChannel(...)` and asserts each getter returns the expected value. This is a low-priority gap because the existing tests already fail if mapping is wrong.

---

## Gap 6 · `defaultsMapMatchesTheDesignDocVerbatimTableExactly` regex is sensitive to `design.md` formatting

**Why it matters:** The test parses `design.md` with a regex that expects `"category = (\\w+).*?: email = (ON|OFF),\\s*in_app = (ON|OFF)"`. If a future edit to `design.md` reformats the table (e.g., changes `in_app` to `in-app`, reorders columns, or adds extra commas), the test will fail even though the semantics haven't changed.

**Suggested test:** This is already a test; the concern is fragility, not absence. Consider making the regex more tolerant (e.g., allow `in-app` or `in_app`, tolerate varying whitespace) or add a fallback assertion that at least fails with a clear message if the regex finds fewer than 3 lines. The current test already asserts `lineCount == 3`, which is good.

---

## Gap 7 · No test verifies the `V6` grant from `PreferenceResolver`'s perspective

**Why it matters:** The baseline migration test proves `notification_app` can `SELECT` and cannot `INSERT`/`UPDATE`/`DELETE` on `channel_preferences`. However, `PreferenceResolverIntegrationTest` inserts rows via the admin connection, so it never exercises the runtime role's read privilege. A regression that accidentally changed the runtime datasource username to a superuser would not be caught.

**Suggested test:** This is already covered by `NotificationBaselineMigrationIntegrationTest`. The gap is only that no T08 test reads via `notification_app` and asserts success. Low priority; the existing module-level migration test is sufficient.

---

## Summary

The T08 test suite now covers the three named tests, stored-row precedence, default fallback, the `SECURITY`+`EMAIL` hard floor, case normalization, unknown-pair fallbacks, null rejection, default-table drift protection, and Spring bean resolution. The strongest remaining gap is **Gap 1**: the manual mutation test that proves the early-return hard floor is necessary is not encoded as an automated regression guard. Gaps 2–4 tighten coverage around edge cases of the resolver's semantics. Gaps 5–7 are smaller precision or already-covered-by-other-tests items.
