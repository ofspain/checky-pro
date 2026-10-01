<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge). -->

# notification · T15 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T15 — Consumed event schema conformance |
| **Spec section** | R19 / package.md §8 |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` + existing contract tests + schemas |
| **Produces** | `artifacts/03-design-challenge.md` |

Phase 3 adversarial design review of the T15 brief before implementation.

---

## Finding 1 · How to validate against JSON Schema without adding a dependency

**Challenge:** The brief forbids a new JSON-Schema-validation library. The schemas are real on-disk JSON Schema files. A naïve string-based comparison would be brittle and would expose secrets.

**Resolution:** Use the already-present Jackson `ObjectMapper` to parse the schema into `JsonNode`, then read `required` and `properties` manually. The new test will assert:
1. Every field listed in `schema.required` is present in the serialized `JsonNode`.
2. Every field present in the serialized `JsonNode` is declared in `schema.properties`.
3. Optionally, each serialized value's JSON type matches the schema property's declared `type`.

This mirrors `EmailRequestedEventContractTest.serializedEventMatchesTheDocumentedSchema` and `UserLifecycleEventContractTest.serializedEventMatchesTheDocumentedSchema` exactly, so the new class is stylistically consistent.

---

## Finding 2 · Should the new test also verify type-level drift?

**Challenge:** The brief explicitly asks to re-read the DTOs against the schemas field-by-field to catch drift the existing tests might miss, giving a type mismatch as an example. However, AC1 only requires "required field present" and "no undeclared field." Adding a third assertion expands the test beyond the named-test requirement.

**Resolution:** Add a lightweight type check, but keep it general enough that it does not become a half-baked schema validator. For each schema property, assert the serialized JSON node's type matches the declared JSON Schema type (`string`, `integer`, `number`, `boolean`, `object`, `array`). Both DTOs only contain string-typed properties today, so the helper is trivial to satisfy now and will catch a future drift where, e.g., `occurredAt` is accidentally serialized as a numeric timestamp or `accountUuid` as an object. The type helper is implemented as a small private method, not a new production dependency.

---

## Finding 3 · Avoiding raw token leakage in assertion failure messages

**Challenge:** `EmailRequestedEvent` contains a raw `token` field. AssertJ failure messages must not embed the full serialized JSON string, because that would print the token in test output.

**Resolution:** All assertions operate on `JsonNode` field names and node-type checks, never on the raw JSON string. Failure messages use only the field name (e.g., `required field 'accountUuid' present`, `serialized field 'token' type matches schema`). The DTO is constructed with a clearly fake token (`"redacted-test-token"` or similar), but the test still avoids printing it by construction.

---

## Finding 4 · Parameterizing both DTOs while preserving the literal named test

**Challenge:** `package.md` §8 requires a single test method named exactly `shouldConformToConsumedEventSchemas`. The test must cover both schemas. A `@ParameterizedTest` would change the method name semantics. Two separate test methods would fail the literal-naming requirement.

**Resolution:** Implement one `@Test void shouldConformToConsumedEventSchemas()` that calls a private helper for each DTO/schema pair in sequence. If either fails, AssertJ reports the failure with the DTO/schema name in the assertion description. This preserves the exact method name and still covers both schemas.

---

## Finding 5 · Path to schema files from the new test class

**Challenge:** The existing contract tests use `Path.of("../../contracts/events/auth/...")` relative to the module root. The new test should use the same path convention so it resolves correctly both in the IDE and from Maven (`mvn -pl services/notification test`).

**Resolution:** Use the identical relative path pattern: `Path.of("../../contracts/events/auth/email-requested.v1.schema.json")` and `Path.of("../../contracts/events/auth/user-lifecycle.v1.schema.json")`. The build already works for the existing contract tests, so this path is correct.

---

## Finding 6 · What to do if re-reading uncovers genuine drift

**Challenge:** The brief says DTOs may be modified only if drift is found. The existing tests already pass, but the field-by-field check might reveal a mismatch.

**Resolution:** First run the new test. If it passes, no DTO changes are needed. If it fails, the failure will pinpoint the drift (missing field, extra field, or type mismatch). Only then modify the DTO to match the schema, never the schema itself. Since the schemas use `additionalProperties: false` and all properties are required, and both DTOs have the exact same fields today, no drift is expected.

---

## Finding 7 · `contracts/events/payments/` does not exist and must be explicitly scoped out

**Challenge:** R19 mentions `contracts/events/{auth,payments}/`, but `services/payment` does not exist. A reviewer might expect the new test to also cover payments schemas.

**Resolution:** The new test only covers the two existing auth schemas. The artifact will explicitly note that `contracts/events/payments/*` is out of scope because `services/payment` does not exist, consistent with T07's skip rationale.

---

## Finding 8 · No production code changes, but module/build implications

**Challenge:** Adding a test file only should not change `pom.xml`, but the test must compile and run. It will reuse `jackson-databind` and AssertJ, both already present.

**Resolution:** No dependency changes. No `package-info.java` changes. The test class lives in `com.themistra.notification.consumer.dto`, the same package as the DTOs and existing contract tests, respecting module boundaries.

---

## Decisions Made

1. **Plain Jackson only** — parse schema files via `ObjectMapper.readTree` and inspect `required`/`properties`/`type`.
2. **Single literal named test** — `shouldConformToConsumedEventSchemas()` calls helpers for both DTOs.
3. **Field-name-only failure messages** — no raw token or full JSON in assertion descriptions.
4. **Lightweight type check included** — catches type drift beyond presence, but does not attempt format/pattern/enum validation (already covered by existing tests).
5. **No DTO/schema changes expected** — if the test passes, only the new test file is created.
6. **Payments schemas explicitly excluded** — documented in the artifact.
