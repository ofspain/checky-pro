<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge). -->

# notification · T09 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T09 — Template renderer |
| **Spec section** | Template rendering |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

Adversarial review of the T09 Phase 2 brief.

---

## Finding 1 · Replacement values containing `$` or `\` will break regex substitution

**Severity:** High

**Evidence:**
- The brief instructs plain regex substitution of `{{key}}` placeholders in `subject`/`body`.
- Java's `Matcher.appendReplacement` treats `$` as a group-reference metacharacter and `\` as an escape metacharacter in the replacement string. A value such as `$100` or `C:\Users` passed in `eventData` will cause `IllegalArgumentException` ("Illegal group reference") or produce truncated/incorrect output.
- This affects any caller-supplied value, including tokens, display names, invoice amounts, or currency strings.

**Recommended brief amendment:**
Specify that every replacement value must be passed through `Matcher.quoteReplacement(...)` before substitution. Add an acceptance criterion and test proving that values containing `$` and `\` render correctly without exception.

---

## Finding 2 · Mismatch between seeded template variable `{{invoiceId}}` and link-computation key `invoiceUuid`

**Severity:** Medium

**Evidence:**
- `V3__seed_launch_templates.sql` uses `{{invoiceId}}` in `invoice.created`, `payment.seen`, `payment.finalized`, and `receipt.issued` templates.
- The brief computes `invoiceLink` from `eventData.get("invoiceUuid")`.
- If the future payment/chain events carry the invoice identifier under `invoiceId` (matching the template's own direct placeholder), `invoiceLink` will not be computed and will render empty.

**Recommended brief amendment:**
Align the raw-source key with the template placeholder and the future event contract. Either:
- change the link computation to use `eventData.get("invoiceId")`, or
- update the seeded templates to use `{{invoiceUuid}}` for display if that is the intended event field.

The same concern applies less critically to `receiptLink` (uses `receiptUuid`, which has no direct `{{receiptId}}` placeholder in the seeds).

---

## Finding 3 · Computed link values are not URL-encoded

**Severity:** Medium

**Evidence:**
- `verificationLink` and `resetLink` append raw `eventData.get("token")` into a URL query parameter.
- Tokens or UUIDs can contain characters that are reserved or unsafe in URLs (e.g., `&`, `=`, `+`, `%`, spaces, non-ASCII characters).
- Without URL encoding, a token like `abc&def=xyz` would produce `?token=abc&def=xyz`, which parses as two query parameters.

**Recommended brief amendment:**
Specify that token/UUID values appended to link URLs must be URL-encoded (e.g., via `URLEncoder.encode(value, StandardCharsets.UTF_8)`). Add tests with tokens containing `&`, `=`, spaces, and Unicode characters. Note that this may conflict with the "provisional link convention" disclaimer, but correctness of the rendered link is not provisional.

---

## Finding 4 · Behavior when `LinkProperties.baseUrl()` is blank or null is unspecified

**Severity:** Medium

**Evidence:**
- `LinkProperties` deliberately allows a blank `baseUrl` in the `local` profile (per its own Javadoc).
- The brief computes links by concatenating `baseUrl + "/verify-email?token=..."` without specifying what happens when `baseUrl` is blank or null.
- In `local`, the rendered links would be relative paths like `/verify-email?token=...` if `baseUrl` is empty, or throw `NullPointerException` if it is null.

**Recommended brief amendment:**
State whether `TemplateRenderer` should:
- allow blank `baseUrl` (rendering relative paths in local) and only throw in non-local profiles, or
- always require non-null/non-blank `baseUrl` and throw `IllegalArgumentException`/`IllegalStateException` if missing.

If the latter, the renderer should validate `baseUrl` and fail fast; if the former, document that local profile produces relative links.

---

## Finding 5 · Trailing slash in `baseUrl` produces double slashes

**Severity:** Low

**Evidence:**
- The link convention uses `baseUrl + "/verify-email?token=..."`.
- If `baseUrl` is configured as `https://checky.pro/` (with trailing slash), the rendered link becomes `https://checky.pro//verify-email?token=...`.

**Recommended brief amendment:**
Specify normalization: either strip a trailing slash from `baseUrl` before concatenation, or document that `baseUrl` must not include a trailing slash. Add a test for the trailing-slash case.

---

## Finding 6 · `TemplateRepository` extends `JpaRepository`, exposing write methods

**Severity:** Medium

**Evidence:**
- The brief instructs `TemplateRepository extends JpaRepository<Template, Long>` even though T09 "never writes a row."
- `JpaRepository` inherits `save`, `saveAll`, `delete`, etc.

**Recommended brief amendment:**
Either change to `Repository<Template, Long>` exposing only the read method, or add a Javadoc warning that the inherited mutators must not be used (mirroring T08's own disposition). The V7 `SELECT`-only grant provides a DB-level safety net, but the API surface still contradicts the task's read-only intent.

---

## Finding 7 · No mention of updating the migration-grant integration test for V7

**Severity:** Medium

**Evidence:**
- AC5 requires `V7` to grant `SELECT` only and deny `INSERT`/`UPDATE`/`DELETE`.
- The brief acknowledges `T01SkeletonRegressionTest` will need updating but does not mention `NotificationBaselineMigrationIntegrationTest`.
- T04/T05/T08 each updated the migration test when a new grant was introduced.

**Recommended brief amendment:**
Add `NotificationBaselineMigrationIntegrationTest` to the "Files to Modify" list with requirements to update Flyway-history expectation to `"1"` through `"7"` and add a dedicated grant-proof test for `templates`.

---

## Finding 8 · Placeholder key character set is not specified

**Severity:** Low

**Evidence:**
- The brief says "Every `{{key}}` placeholder" but does not define what constitutes a valid `key`.
- The seeded templates use alphanumeric keys with camelCase (`displayName`, `verificationLink`).
- A regex that is too permissive could match `{{}}` or nested placeholders; one that is too restrictive could reject future keys with hyphens or underscores.

**Recommended brief amendment:**
Specify the allowed key format (e.g., `[a-zA-Z][a-zA-Z0-9_]*`). Add a test proving keys with underscores and digits work, and that malformed placeholders (empty, nested, unclosed) either render as-is or render empty according to a documented rule.

---

## Finding 9 · Null values inside `eventData` are not specified

**Severity:** Low

**Evidence:**
- `render` takes `Map<String, String> eventData`.
- A `HashMap` can store `null` values. If a key maps to `null`, naive string concatenation would produce the literal string `"null"`.
- The brief says a placeholder with "no value" renders empty, but does not define whether `null` counts as "no value."

**Recommended brief amendment:**
State that `eventData` must not contain `null` values (caller contract), or specify that `null` values are treated as empty strings. Add a test for the chosen behavior.

---

## Finding 10 · `TemplateRenderer` Spring bean lifecycle is not specified

**Severity:** Low

**Evidence:**
- The brief calls `TemplateRenderer` "the public entry point" but does not say it is a `@Service`/`@Component`.
- Task 11's `DeliveryOrchestrator` will need to inject it.

**Recommended brief amendment:**
Annotate `TemplateRenderer` with `@Service` (consistent with `PreferenceResolver` and `ContactProjectionUpdater`). Add a fast test proving it is component-scanned, or document the intended bean type explicitly.

---

## Summary

The T09 brief correctly scopes template rendering as read-only, defers write APIs, and pins the provisional link convention. The most consequential gap is **Finding 1**: a standard regex-substitution implementation without `Matcher.quoteReplacement` will throw on realistic input values. **Findings 2–4** are correctness issues around the computed links (key mismatch, URL encoding, blank baseUrl). **Finding 5** is a small robustness item. **Findings 6–7** repeat the `JpaRepository`/migration-test concerns seen in earlier tasks. Findings 8–10 are smaller precision and lifecycle items.
