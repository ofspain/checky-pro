<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Review). -->

# notification · T15 · Phase 8 — Independent Review

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T15 — Consumed event schema conformance |
| **Spec section** | R19 / package.md §8 |
| **Model** | Kimi 2.7 |
| **Consumes** | Implementation + `artifacts/07-self-review.md` + frozen brief |
| **Produces** | `artifacts/08-independent-review.md` |

Independent review of the T15 implementation and self-review.

---

## Finding 1 (concur with self-review) · `matchesJsonSchemaType` does not handle the JSON-Schema array `type` form

**Severity:** Low

**Evidence:**
- `ConsumedEventSchemaConformanceTest.java` line 67: `declaredProperties.get(field).get("type").asText()`.
- All real auth schemas use single-string `type` values; the helper is correct today.

**Assessment:** Valid latent gap. JSON Schema permits `"type": ["string", "null"]`. If a future schema revision uses it, `.asText()` on an array node returns `""`, and `matchesJsonSchemaType` throws `IllegalArgumentException("unsupported JSON Schema type: ")`. This is a confusing failure rather than correct validation.

**Recommendation:** Either document the helper as single-string-type only or extend it to handle arrays. For this task, documenting the limitation is sufficient; no speculative fix is required because neither consumed schema uses the array form.

---

## Finding 2 (concur with self-review) · Simultaneous drift in the second DTO is masked if the first fails

**Severity:** Low

**Evidence:**
- `shouldConformToConsumedEventSchemas` invokes both assertions in sequence inside one `@Test`; AssertJ throws on the first failure.

**Assessment:** Valid but minor. The brief explicitly rejected `@ParameterizedTest` to preserve the literal method name. `SoftAssertions` is the compatible fix and would surface both failures in one run. Given the small number of DTOs and the independent per-DTO contract tests, this is optional for Phase 9.

---

## Finding 3 · The type check silently skips optional schema fields not present in the serialized DTO

**Severity:** Very low / informational

**Evidence:**
- `assertConformsToSchema` lines 65–73: the type loop only checks `if (serialized.has(field))`.

**Assessment:** This is correct behavior for the stated acceptance criteria (AC1 only requires required fields to be present and no undeclared fields). If a future schema adds an optional property that the DTO does not serialize, the test will not flag it. That is consistent with consumer-side validation: the consumer must not send undeclared fields, but it need not send every optional field. No action required; noting for completeness.

---

## Finding 4 · The new test class does not assert `additionalProperties: false` from the schema itself

**Severity:** Very low / informational

**Evidence:**
- The test asserts `serialized.fieldNames()` are all declared in `schema.properties`, which is stronger than `additionalProperties: false` when the schema disallows extras and equivalent for these two schemas.

**Assessment:** The current assertion already enforces the same outcome for the auth schemas. No change needed. If a future schema allowed `additionalProperties: true`, the test would still reject extra DTO fields, which is a reasonable consumer-side guarantee.

---

## Cross-check against acceptance criteria

| Criterion | Status | Notes |
|---|---|---|
| AC1 — `shouldConformToConsumedEventSchemas` exists and checks required + undeclared fields for both DTOs | ✅ | Implemented exactly as required. |
| AC2 — No genuine drift between DTOs and schemas | ✅ | New test passes; field-by-field type check adds drift detection beyond existing tests. |
| AC3 — No raw token in assertion messages | ✅ | All assertions use field names/types only; fake token value is never embedded. |
| AC4 — Payments schemas explicitly scoped out | ✅ | Only auth schemas are covered; payments directory does not exist. |

---

## Verdict

The implementation satisfies the frozen brief. The two self-review findings are real but low severity and acceptable for this task's scope. No additional mandatory fixes identified. Optionally consider `SoftAssertions` (Finding 2) and documenting or extending the `type` helper (Finding 1) in Phase 9.
