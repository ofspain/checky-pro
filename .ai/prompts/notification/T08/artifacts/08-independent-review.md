<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Code Review). -->

# notification · T08 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T08 — Preference resolver |
| **Spec section** | Preference resolution |
| **Model** | Kimi 2.7 |
| **Consumes** | Phase 6 implementation + `artifacts/07-self-review.md` |
| **Produces** | `artifacts/08-independent-review.md` |

Fresh adversarial review of the completed T08 implementation. Findings only.

---

## Finding 1 · No T08-specific tests exist yet

**Issue:** AC1–AC5 and the three `package.md` §8 named tests (`shouldResolveChannelPreferencesPerRecipient`, `shouldSuppressChannelWhenRecipientOptedOut`, `shouldFallBackToDefaultPreferenceWhenNoneSet`) require automated tests. The codebase currently has no `PreferenceResolverTest` or `PreferenceResolverIntegrationTest`.

**Evidence:**
- `services/notification/src/test/java/com/themistra/notification/preference/` contains only T05's `ContactProjectionUpdater*Test` files.
- No test references `PreferenceResolver`, `ChannelPreferenceRepository`, or `ChannelPreference`.

**Recommendation:** Add the missing tests before considering T08 complete:
- `PreferenceResolverTest` (unit/integration, mocked or in-memory DB): stored row precedence, default fallback, `SECURITY`+`EMAIL` hard floor, unknown pair behavior, case normalization, null rejection.
- If using a real Spring context, a `PreferenceResolverIntegrationTest` (Testcontainers Postgres) proving the repository query and default logic against the actual DB role grant.

**Confidence:** High

---

## Finding 2 · No test verifies the `SECURITY`+`EMAIL` hard floor against a stored `enabled=false` row

**Issue:** The hard floor is implemented as an early `return true` before querying the repository. If a future refactor removed that early return, the code would still return `true` for `SECURITY`+`EMAIL` when no row exists (because the default map has `true`), but it would incorrectly return `false` if a stored row with `enabled=false` exists. This is the exact adversarial row the hard floor is meant to override.

**Evidence:**
- `PreferenceResolver.java` lines 59–61: the early return is the only protection.
- No test in `src/test` inserts a `SECURITY`/`EMAIL`/`enabled=false` row and asserts `resolve` still returns `true`.

**Recommendation:** Add a test that inserts such a row (via raw JDBC/admin connection or a future write path) and asserts `preferenceResolver.resolve(accountUuid, "SECURITY", "EMAIL")` is `true`.

**Confidence:** High

---

## Finding 3 · No test verifies the default table is copied verbatim from `design.md`

**Issue:** The brief requires the default table to be copied exactly. The implementation hardcodes six entries in `DEFAULTS`, but no test asserts that these six entries match `design.md` §4c or that no extra entries exist.

**Evidence:**
- `PreferenceResolver.java` lines 30–36: `DEFAULTS` map.
- No test compares the map contents against the spec or checks that only the six documented pairs are present.

**Recommendation:** Add a test that reads `design.md` §4c's default-preferences block, parses the six pairs and their ON/OFF values, and asserts that `PreferenceResolver.DEFAULTS` (or a package-visible accessor) contains exactly those entries. This prevents drift between spec and code.

**Confidence:** Medium

---

## Finding 4 · No test verifies `WEBHOOK`/`PUSH` and unknown category/channel behavior

**Issue:** The implementation resolves `false` for any `(category, channel)` pair outside the six documented defaults, including the DB-permitted `WEBHOOK`/`PUSH` channels and any unknown category. This behavior is correct but untested.

**Evidence:**
- `PreferenceResolver.java` lines 68–70: `defaultFor` returns `false` for unknown keys.
- No test calls `resolve(..., "PAYMENT", "WEBHOOK")`, `resolve(..., "COMPLIANCE", "EMAIL")`, etc.

**Recommendation:** Add tests asserting `false` for at least:
- a known category with an unsupported channel (`PAYMENT`/`WEBHOOK`);
- an unknown category with a known channel (`COMPLIANCE`/`EMAIL`);
- both `WEBHOOK` and `PUSH` under every documented category.

**Confidence:** Medium

---

## Finding 5 · No test verifies case normalization against the real DB

**Issue:** The implementation normalizes category and channel to uppercase before querying. A unit test can verify the normalization logic, but only an integration test can prove that a row stored as uppercase is matched by a lowercase query argument.

**Evidence:**
- `PreferenceResolver.java` lines 56–57 and 63: normalization before repository query.
- The self-review verified this with a scratch test, but no committed test exists.

**Recommendation:** Add an integration test that inserts a row with uppercase `PAYMENT`/`EMAIL` (via admin connection) and asserts `resolve(accountUuid, "payment", "email")` returns the stored value.

**Confidence:** Medium

---

## Finding 6 · No test verifies null-input rejection

**Issue:** `PreferenceResolver.resolve` uses `Objects.requireNonNull` for all three parameters. This is correct, but a future refactor that removed the null checks would not fail any existing test.

**Evidence:**
- `PreferenceResolver.java` lines 52–54.
- No test asserts `NullPointerException` (or any behavior) for null inputs.

**Recommendation:** Add three unit tests asserting that `resolve(null, ...)` throws `NullPointerException`. This locks the documented "must not be null" contract.

**Confidence:** Low

---

## Finding 7 · No test verifies `PreferenceResolver` is a Spring bean

**Issue:** `PreferenceResolver` is `@Service`-scanned and will be injected by task 11's `DeliveryOrchestrator`. No current test autowires it.

**Evidence:**
- `PreferenceResolver.java` line 27: `@Service`.
- No `@SpringBootTest` currently autowires `PreferenceResolver`.

**Recommendation:** Add a small `@SpringBootTest` (or extend an existing one) that autowires `PreferenceResolver` and asserts it is not null. This is a low-priority gap because the integration tests for T08 will exercise it, but a dedicated fast test prevents silent bean-scan regressions.

**Confidence:** Low

---

## Finding 8 · `ChannelPreferenceRepository` still exposes inherited mutators

**Issue:** Same class of concern as T05's `ContactProjectionRepository`: extending `JpaRepository` makes `save`/`delete` available even though T08 is read-only. The Javadoc acknowledges this and notes the `SELECT`-only grant makes accidental writes fail at the DB, but the API surface still contradicts the task's intent.

**Evidence:**
- `ChannelPreferenceRepository.java` line 16: `extends JpaRepository<ChannelPreference, Long>`.
- Lines 8–15 document the deviation.

**Recommendation:** If the frozen brief permits, change to `extends Repository<ChannelPreference, Long>` and explicitly declare only `findByAccountUuidAndCategoryAndChannel`. If not permitted, the current Javadoc warning is sufficient, but consider adding an ArchUnit or reflection test asserting no production code calls the inherited mutators.

**Confidence:** Low

---

## Summary

The T08 production code is clean and matches the frozen brief: `PreferenceResolver` correctly implements stored-row precedence, verbatim defaults, the `SECURITY`+`EMAIL` hard floor, case normalization, and safe `false` fallbacks for unsupported/unknown pairs. The `V6` grant and the T01/T02 regression-test updates are correct. The dominant issue is the complete absence of T08-specific automated tests (Finding 1). Findings 2–5 are coverage items needed to lock the most important correctness properties; Findings 6–8 are smaller precision and API-surface items.
