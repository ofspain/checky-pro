<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Review). -->

# notification · T19 · Phase 8 — Independent Review

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T19 — Delivery-log dispute-grade / append-only verification |
| **Spec section** | R10, R11, R12, R13, L3, L9 |
| **Model** | Kimi 2.7 |
| **Consumes** | Implementation + `artifacts/07-self-review.md` + frozen brief |
| **Produces** | `artifacts/08-independent-review.md` |

Independent review of the T19 implementation and self-review.

---

## Finding 1 (concur with self-review Finding 1) · Template-version assertion omits IN_APP rows

**Severity:** Low

**Evidence:**
- `retryChainAppendsMultipleRowsForTheSameSourceEventKey` lines 266–272: the `allSatisfy` block asserting `templateVersion()` non-null runs only over `emailRows`.
- The IN_APP row for the same `verify_email` event is rendered (`user.verify` template) and should also carry a template version.

**Assessment:** Valid gap. A regression that dropped `template_version` on IN_APP rows only would pass this test while violating AC3's stated scope.

**Recommendation:** Fix in Phase 9 by asserting all rendered rows (both EMAIL and IN_APP) have non-null `templateVersion`.

---

## Finding 2 (concur with self-review Finding 2) · AC5 static scan is textual and partial

**Severity:** Low

**Evidence:**
- `noApplicationCodeUpdatesOrDeletesDeliveryLogRows` lines 300–302 scans for raw SQL `UPDATE`/`DELETE ... delivery_log` and `deliveryLogRepository.delete*(`. It does not detect `EntityManager.merge/remove`, `JdbcTemplate` updates, or dynamically built native queries.

**Assessment:** The database grant proved in AC1 is the complete runtime guarantee. The static scan is a useful but incomplete guard.

**Recommendation:** Disclose in Phase 12 that AC5's static evidence is textual and that AC1 carries the authoritative guarantee. No test change required.

---

## Finding 3 (concur with self-review Finding 3) · AC5 scan depends on Surefire working directory

**Severity:** Informational

**Evidence:**
- `Path.of("src/main/java")` at line 305.

**Assessment:** Surefire runs with the module as the working directory, so the path resolves correctly. Running the test from another directory would throw `NoSuchFileException` rather than pass silently.

**Recommendation:** No change needed. Document the assumption.

---

## Finding 4 · AC2 assertion on attempt numbers could be more explicit

**Severity:** Very low / informational

**Evidence:**
- `retryChainAppendsMultipleRowsForTheSameSourceEventKey` lines 263–265 checks that the number of distinct attempt values is 2.

**Assessment:** This correctly rejects the case where both rows have the same attempt number, but it does not assert the expected sequence is `{1, 2}`. A future reader must infer the values.

**Recommendation:** Optional Phase 9 enhancement: assert `emailRows.extracting(LogRow::attempt).containsExactlyInAnyOrder((short) 1, (short) 2)`. This makes the append-only progression explicit.

---

## Finding 5 · AC4 does not assert that the SUPPRESSED row lacks a template version

**Severity:** Very low / informational

**Evidence:**
- `suppressedChannelLeavesASuppressedDeliveryLogRow` asserts the EMAIL row is `SUPPRESSED` but does not assert `templateVersion == null`.

**Assessment:** AC3 explicitly scopes the template-version check to rendered rows. Documenting that SUPPRESSED rows carry no template version would strengthen the correlation between AC3 and AC4.

**Recommendation:** Optional Phase 9 enhancement: assert the SUPPRESSED row's `templateVersion` is null.

---

## Finding 6 · Maven verification cannot be confirmed in this environment

**Severity:** Medium (verification gap)

**Evidence:**
- `mvn` is not available in this workspace.
- The self-review does not restate Maven run output, but Phase 6 presumably executed it.

**Assessment:** The test class is syntactically correct and internally consistent, but no local execution was performed.

**Recommendation:** Run `mvn -pl services/notification clean verify` in an environment with Maven before closing the task.

---

## Cross-check against acceptance criteria

| Criterion | Status | Notes |
|---|---|---|
| AC1 — UPDATE/DELETE rejected as `notification_app` | ✅ | Uses `notificationAppConnection()` and asserts SQLState `42501`. Also verifies row remains unchanged. |
| AC2 — Retry chain leaves multiple rows sharing `source_event_key` | ✅ | FAILED → SENT chain driven; rows share key and differ by attempt. Could assert exact attempt values. |
| AC3 — Rendered rows record `template_version` | ⚠️ | Covered for EMAIL rows; IN_APP rendered row is not checked. |
| AC4 — Suppressed channel leaves `SUPPRESSED` row | ✅ | Asserts EMAIL `SUPPRESSED` row and IN_APP row; asserts no email sent. |
| AC5 — No code path updates/deletes `delivery_log` | ⚠️ | Static scan plus AC1 grant. Scan is textual and partial; AC1 is the real backstop. |

---

## Verdict

The implementation satisfies the functional acceptance criteria, with one clear, cheap improvement needed in Phase 9 (Finding 1: extend template-version check to IN_APP). The other findings are optional strengthenings or disclosures. No production code changes are required.
