<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge). -->

# notification · T08 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T08 — Preference resolver |
| **Spec section** | Preference resolution |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

Adversarial review of the T08 Phase 2 brief.

---

## Finding 1 · Defaults for `WEBHOOK` and `PUSH` channels are not specified

**Severity:** High

**Evidence:**
- V1's `channel_preferences` table has a `CHECK` constraint allowing `WEBHOOK` and `PUSH` in addition to `EMAIL` and `IN_APP`.
- The default-preferences table in `design.md` §4c only names six pairs: `SECURITY`/`EMAIL`, `SECURITY`/`IN_APP`, `PAYMENT`/`EMAIL`, `PAYMENT`/`IN_APP`, `MARKETING`/`EMAIL`, `MARKETING`/`IN_APP`.
- The brief says `PreferenceResolver` returns the documented default "for every `(category, channel)` pair the default table names" — it does not say what happens for `WEBHOOK` or `PUSH`.

**Recommended brief amendment:**
Explicitly state the default for `WEBHOOK`/`PUSH` (or explicitly exclude them from `PreferenceResolver`'s supported inputs). The safest default is `false` for all categories on these channels until task 13/14 implements them, because "send on every channel" is forbidden by `agents.md` and the default table gives no positive signal. If they are unsupported, the brief should say `PreferenceResolver` returns `false` for any `(category, channel)` pair outside the six named defaults.

---

## Finding 2 · Case-sensitivity of `category` and `channel` arguments is not specified

**Severity:** Medium

**Evidence:**
- The DB `CHECK` constraints store values in uppercase: `SECURITY`, `PAYMENT`, `MARKETING`, `EMAIL`, `IN_APP`.
- Future callers (task 11's `DeliveryOrchestrator`) may pass values derived from `notificationKind` strings or enum names that are not guaranteed to be uppercase.
- The brief does not say whether `PreferenceResolver.resolve(accountUuid, "security", "email")` should match a stored row for `("SECURITY", "EMAIL")` or fall back to the default.

**Recommended brief amendment:**
Specify the case-handling rule. Two reasonable options:
- Normalize inputs to uppercase before querying (treats `security` and `SECURITY` as equivalent), or
- Treat category/channel as exact-match, case-sensitive strings (callers must pass uppercase).

The first option is more forgiving and aligns with the DB constraint semantics. Add a test proving lowercase inputs produce the same result as uppercase inputs when a stored row exists.

---

## Finding 3 · Behavior for unknown categories or channels is not specified

**Severity:** Medium

**Evidence:**
- The default table covers only `SECURITY`, `PAYMENT`, `MARKETING` categories and `EMAIL`/`IN_APP` channels.
- If a future event category is added (e.g., `COMPLIANCE`) or a new channel appears before the default table is updated, `PreferenceResolver` will encounter a combination with no defined default.
- The brief says "never fail" under Null handling, but does not extend that guarantee to unknown categories/channels.

**Recommended brief amendment:**
State the safe fallback for any `(category, channel)` pair not in the default table. Consistent with `agents.md`'s "never 'send on every channel', never fail" rule, recommend returning `false` (suppressed) for unknown pairs. Add an explicit AC or test for this case.

---

## Finding 4 · `ChannelPreferenceRepository` extends `JpaRepository`, exposing write methods

**Severity:** Medium

**Evidence:**
- The brief instructs `ChannelPreferenceRepository extends JpaRepository<ChannelPreference, Long>` even though T08 "never writes a row."
- `JpaRepository` inherits `save`, `saveAll`, `delete`, etc., which would bypass the task's own "no write path" constraint if called accidentally.

**Recommended brief amendment:**
Either:
- change the repository to extend `Repository<ChannelPreference, Long>` and expose only `findByAccountUuidAndCategoryAndChannel`, or
- add a Javadoc warning that the inherited write methods must not be used and that `channel_preferences` is read-only in this task's scope.

Given the module's precedent of `extends JpaRepository` in T04/T05, the second option may be the only one that respects the frozen brief, but the risk should be documented.

---

## Finding 5 · No mention of updating the migration-grant integration test for V6

**Severity:** Medium

**Evidence:**
- AC5 requires `V6` to grant `SELECT` only and deny `INSERT`/`UPDATE`/`DELETE`.
- The brief acknowledges `T01SkeletonRegressionTest` will need updating but does not mention `NotificationBaselineMigrationIntegrationTest`.
- T04/T05 each added a dedicated grant-proof test or updated the migration test when a new grant was introduced.

**Recommended brief amendment:**
Add `NotificationBaselineMigrationIntegrationTest` to the "Files to Modify" list with a requirement to:
- update Flyway-history expectation to `"1", "2", "3", "4", "5", "6"`;
- add a dedicated test that connects as `notification_app` and proves `SELECT` succeeds while `INSERT`/`UPDATE`/`DELETE` are denied on `channel_preferences`.

---

## Finding 6 · Null handling for `category` and `channel` arguments is not specified

**Severity:** Low

**Evidence:**
- The brief says `PreferenceResolver` must handle "no row" (empty `Optional`) as a first-class case.
- It does not say what happens if `category` or `channel` is `null`.
- A `null` argument to the repository query or the `SECURITY`+`EMAIL` hardcoded check would produce an NPE or incorrect result.

**Recommended brief amendment:**
Specify that `PreferenceResolver.resolve` requires non-null `category` and `channel` (e.g., document as `@Nonnull` and rely on callers) or define a safe fallback (e.g., `null` channel/category returns `false`). Given the call-directly design, requiring non-null is reasonable; document it explicitly.

---

## Finding 7 · `category`/`channel` as strings vs. enums is left to Phase 5 without noting the trade-offs

**Severity:** Low

**Evidence:**
- The brief says Phase 5 will decide whether to use enums or strings for `category`/`channel`.
- The DB already enforces the value space via `CHECK` constraints, so strings are sufficient at the persistence layer.
- However, using enums in Java would make the default-table lookup compile-time safe and prevent typos in callers.

**Recommended brief amendment:**
Add a short design note listing the trade-off: enums give type safety but require updating Java code whenever a new category/channel is added; strings match the existing `notificationKind` precedent and the DB constraints. This helps Phase 5 make an informed choice rather than treating it as purely stylistic.

---

## Finding 8 · The default table uses lowercase prose but the brief uses uppercase constants

**Severity:** Low

**Evidence:**
- `design.md` §4c writes the default table as `email = ON, in_app = ON`.
- The brief correctly maps these to DB/channel constants `EMAIL`/`IN_APP`, but the prose mismatch could confuse a future reader.

**Recommended brief amendment:**
When copying the default table into the frozen brief, quote it with the exact DB values (`EMAIL`, `IN_APP`) to avoid any ambiguity. A small clarity improvement with no behavioral impact.

---

## Summary

The T08 brief is internally consistent with L2/L6 and `agents.md`: it correctly scopes the resolver as read-only, honors the hard floor for `SECURITY`+`EMAIL`, and defers write APIs to later tasks. The most consequential gap is **Finding 1**: the DB schema permits `WEBHOOK` and `PUSH` channels, but the brief does not define their default behavior, creating a correctness risk when task 11/12/13 first calls the resolver for those channels. **Findings 2–3** are medium-severity behavioral ambiguities (case sensitivity and unknown pairs). **Finding 4** repeats the `JpaRepository` write-surface concern already seen in T05. **Finding 5** ensures the grant is actually tested. Findings 6–8 are smaller precision and clarity items.
