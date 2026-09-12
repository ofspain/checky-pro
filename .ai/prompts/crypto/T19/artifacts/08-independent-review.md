# crypto · T19 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `crypto-service` |
| **Task** | T19 — Screening client |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md` |
| **Produces** | `artifacts/08-independent-review.md` |

Independent review of the Phase 6 implementation and Phase 7 self-review. Findings only; no rewrites.

---

## 1. `FailClosedScreeningClient.screen(...)` emits a log and reads the clock before validating inputs

- **Issue:** `screen(...)` calls `log.warn(...)` and `clock.instant()` before `ScreeningResult.create(...)` performs its `Objects.requireNonNull` checks on `chain` and `address`. A caller passing a `null` `chain` or `address` therefore produces a misleading "stub active" log entry and a clock read for a call that never completes or persists. This contradicts the established defensive convention in `ReorgDetector.reorg` (T18), which validates all arguments before any side effect.
- **Evidence:** `screening/FailClosedScreeningClient.java:38-46`.
- **Recommendation:** Move the `Objects.requireNonNull` validation for `chain` and `address` to the very top of `screen(...)`, before the log statement and clock read. Since `ScreeningResult.create` already performs the same checks, the change is pure reordering.
- **Confidence:** High

## 2. `ScreeningClient` interface Javadoc does not document `chain`/`address` nullability

- **Issue:** The interface's `@param` block explicitly notes that `txHash` may be `null`, but is silent on whether `chain` and `address` may be `null`. A future real-vendor implementation reading only the interface has no documented signal that these two parameters are non-nullable.
- **Evidence:** `screening/ScreeningClient.java:29-36`. Matches Phase 7 Self-Review Finding 2.
- **Recommendation:** Add explicit `@throws NullPointerException if chain or address is null` (or equivalent prose) to the interface Javadoc.
- **Confidence:** High

## 3. The fail-closed stub's warning log omits the counterparty `address`

- **Issue:** The `warn` log reports `chain` and `txHash` but not `address`, even though the message itself says the "address was not screened." An operator reading the log cannot tell which counterparty triggered the stub without cross-referencing the persisted `ScreeningResult` row.
- **Evidence:** `screening/FailClosedScreeningClient.java:39-40`. Matches Phase 7 Self-Review Finding 3.
- **Recommendation:** Include `address` in the log statement's parameters and message.
- **Confidence:** High

## 4. No test exercises `screen(...)` with a `null` `chain` or `null` `address`

- **Issue:** `FailClosedScreeningClientTest` covers normal input, `null` `txHash`, and a malformed address string, but never calls `screen(null, ...)` or `screen(..., null, ...)`. `ScreeningResultTest` verifies `ScreeningResult.create`'s null-checks, but that does not exercise `FailClosedScreeningClient`'s own calling path.
- **Evidence:** `screening/FailClosedScreeningClientTest.java:34-79`. Matches Phase 7 Self-Review Finding 4.
- **Recommendation:** Add tests asserting that `client.screen(null, "0xaddr", "0xtx")` and `client.screen("ETHEREUM", null, "0xtx")` each throw `NullPointerException`.
- **Confidence:** High

## 5. `ScreeningResult`'s class Javadoc mischaracterizes `token_allowlist` as an audit-trail table

- **Issue:** The Javadoc lists `token_allowlist` alongside `observations` and `attestations` as another "audit-trail table." `token_allowlist` is seeded, versioned configuration data, not a log of attempt outcomes. The shared property is append-only/INSERT-SELECT grant, not the audit-trail nature.
- **Evidence:** `screening/ScreeningResult.java:17-23`. Matches Phase 7 Self-Review Finding 5.
- **Recommendation:** Rephrase the comparison to focus on the shared append-only/grant property, or drop `token_allowlist` from that specific list.
- **Confidence:** Low

## 6. `ScreeningModuleBoundaryTest` does not forbid all non-`screening`/`common` `com.themistra.crypto.*` prefixes

- **Issue:** The boundary test only forbids the nine feature-module prefixes explicitly listed in `FULLY_FORBIDDEN_IMPORT_PREFIXES`. It does not forbid, for example, `com.themistra.crypto.config`, `com.themistra.crypto.common.config`, or any future subpackage. The current source files happen to import only `screening/`, JDK, JPA, Hibernate, SLF4J, and Spring, so the test passes, but the test is not exhaustive.
- **Evidence:** `screening/ScreeningModuleBoundaryTest.java:21-31`.
- **Recommendation:** Invert the test: assert that every `import com.themistra.crypto.*` line starts with either `com.themistra.crypto.screening.` or `com.themistra.crypto.common.`, rather than maintaining a list of forbidden prefixes.
- **Confidence:** Low

## 7. `ScreeningResult.create(...)` accepts empty strings for required `String` parameters

- **Issue:** `create(...)` rejects `null` for `chain`, `address`, `provider`, etc., but does not reject empty strings. Persisting an empty `chain` or `address` would satisfy the `NOT NULL` constraint while producing meaningless data.
- **Evidence:** `screening/ScreeningResult.java:66-83`.
- **Recommendation:** Add `isBlank()` checks (or `length() > 0` checks) for `chain`, `address`, and `provider`, throwing `IllegalArgumentException` for empty/blank values. If this is intentionally out of scope, document it explicitly.
- **Confidence:** Low

## 8. No test verifies that a persisted `ScreeningResult` cannot be updated through JPA

- **Issue:** The entity has no setters and `screenedAt` is annotated `updatable = false`, but there is no JPA-level test that attempts to mutate a loaded entity and flush it. The integration test only verifies raw JDBC `UPDATE`/`DELETE` failure.
- **Evidence:** `screening/ScreeningResultRepositoryIntegrationTest.java`.
- **Recommendation:** Add a test that loads a saved `ScreeningResult` and attempts a JPA merge with a modified field (e.g., via reflection to set a private field, then `repository.save(merged)` + `flush()`), asserting that the operation either throws or leaves the row unchanged.
- **Confidence:** Low

## 9. `FailClosedScreeningClient` does not guard against a `null` `Clock` or `ScreeningResultRepository` at construction

- **Issue:** The constructor assigns `screeningResultRepository` and `clock` without `Objects.requireNonNull`. Spring will normally inject non-null beans, but a direct instantiation in a test or future manual wiring could pass `null`, leading to a less-clear `NullPointerException` later.
- **Evidence:** `screening/FailClosedScreeningClient.java:32-35`.
- **Recommendation:** Add `Objects.requireNonNull` guards in the constructor for both dependencies, matching `ScreeningResult.create`'s defensive style.
- **Confidence:** Low

## 10. No test verifies that two consecutive `screen(...)` calls produce two independent `ScreeningResult` rows

- **Issue:** AC3 says "every call ... results in exactly one new `ScreeningResult` persisted." The existing test verifies one call produces one row, but does not verify that a second call does not somehow reuse or overwrite the first row.
- **Evidence:** `screening/FailClosedScreeningClientTest.java:65-79`.
- **Recommendation:** Add a test that calls `client.screen(...)` twice and verifies `screeningResultRepository.save(...)` is invoked twice with two distinct `ScreeningResult` instances.
- **Confidence:** Low

---

(End of independent review. Findings are for human fold-in during Phase 9.)
