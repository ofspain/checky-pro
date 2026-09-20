<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review). -->

# auth · T33 · Phase 11 — Test Review

Consumed `artifacts/10-test-generation.md` and the final test files. Verified locally:
`mvn -pl services/auth test -Dtest='AuthOpenApiContractTest,EmailRequestedEventPayloadContractTest,AuditMirrorPayloadContractTest,EventTopicsTest'`
→ **15/15 pass**. Findings only — no test or production code changes.

---

## Executive Summary

The Phase 10 suite is comprehensive and directly closes the gaps raised in Phase 8. Route
completeness is bidirectional, component schemas are matched against real DTOs, operation-level
`$ref` references are verified for both request bodies and responses, and enum drift is guarded for
`AccountStatus` and `AuditOutcome`. The remaining concerns are traceability/naming and table
maintenance, not correctness.

---

## Findings

### Gap 1 — `package.md` §8 named test `shouldConformToAuthOpenApiContract` does not exist as a single test method

**Why it matters:** The spec's named-test list is typically used by CI/pipeline tooling and human
reviewers to confirm a specific, greppable test exists. Here, the named test's intent is split
across seven purpose-named methods (`everyControllerHandlerIsDocumentedInAuthYaml`,
`authYamlDocumentsNoRouteThatDoesNotHaveARealHandler`, `everyComponentSchemaMatchesItsRealDtoShape`,
`everyOperationResponseReferencesTheExpectedSchema`,
`everyOperationRequestBodyReferencesTheExpectedSchema`,
`everyAccountStatusValueIsCoveredByTheAccountResponseSchemaEnum`,
`everyAuditOutcomeValueIsCoveredByTheAuditEventResponseSchemaEnum`). The Phase 10 manifest
justifies this as avoiding a monolithic assertion, but there is no single method a downstream
tool can run or report on using the exact name from `package.md`.

**Suggested test:** Add a no-op or delegating `@Test void shouldConformToAuthOpenApiContract()`
that simply exists as the named hook, or rename one of the seven methods to exactly match the
named test and have it invoke the others. Alternatively, update `package.md` §8 to list the seven
method names if the spec permits multi-method named tests.

**Evidence:** `AuthOpenApiContractTest.java:86-202`; `artifacts/10-test-generation.md:37-41`.

---

### Gap 2 — `expectedResponseSchemas()` and `expectedRequestSchemas()` are hand-maintained expectation tables

**Why it matters:** If a new endpoint is added, the bidirectional route checks will fail until
`auth.yaml` is updated, but the schema-reference checks will fail with a missing-key error rather
than a clear "add this new route to the expectation table" message. The tables also duplicate the
mapping knowledge already present in the controller annotations and the YAML itself.

**Suggested test:** This is acceptable for the current scope, but add a code comment at the top of
each table explaining that new endpoints must be added to these maps, and that the route
completeness tests alone are not enough. Optionally, add a guard test that asserts the key set of
`expectedResponseSchemas()` equals the set of routes returned by `controllerRoutes()` (minus the
routes that legitimately have no body), so a new handler forces an explicit table update rather
than a cryptic assertion failure.

**Evidence:** `AuthOpenApiContractTest.java:266-313`.

---

### Gap 3 — `actualResponseSchema` picks the first 2xx response by iteration order

**Why it matters:** The method iterates `responses.fieldNames()` and returns the schema for the
first status code starting with `"2"`. YAML object field order is preserved by Jackson, so this
happens to work for the current spec (each operation has at most one 2xx response). If a future
operation legitimately documents both `200` and `204`, or if `201` is listed before `200`, the
test could compare against the wrong response shape.

**Suggested test:** Make the selection deterministic by preferring the lowest 2xx status code
numerically, or by matching the exact status documented in the expectation table. Low priority —
no operation today has multiple 2xx responses.

**Evidence:** `AuthOpenApiContractTest.java:222-237`.

---

### Gap 4 — Contract file paths are relative to Surefire's working directory

**Why it matters:** `Path.of("../../contracts/api/auth.yaml")` works because Maven Surefire runs
with the module directory as the working directory. If the tests are ever executed from a different
working directory (e.g., via an IDE with a non-default run configuration, or a future build tool
change), the path will resolve incorrectly. The existing `UserLifecycleEventPayloadContractTest`
uses the same pattern, so this is a fleet-wide convention rather than a new risk.

**Suggested test:** Consider resolving the contract path from a classpath resource or from a
project-property injected at build time, so the test is independent of the working directory.
Because the existing event-contract test shares the same fragility, this is a low-priority
consistency improvement, not a T33-specific blocker.

**Evidence:** `AuthOpenApiContractTest.java:67`.

---

## Non-Issues Confirmed

- **Phase 8 Finding 1 closure:** `everyOperationResponseReferencesTheExpectedSchema` and
  `everyOperationRequestBodyReferencesTheExpectedSchema` now verify operation-level `$ref`
  correctness, with explicit expectation tables and negative-proof coverage.
- **Phase 8 Finding 2 closure:** Enum-coverage tests exist for both `AccountResponse.status` and
  `AuditEventResponse.outcome`.
- **Phase 8 Finding 4 closure:** The bare-`@RequestMapping` limitation is documented inline in
  `controllerRoutes()`.
- **Route completeness:** 30 controller routes match 30 YAML routes in both directions.
- **Event schemas:** `EmailRequestedEventPayloadContractTest` and `AuditMirrorPayloadContractTest`
  follow the established pattern and cover required fields, undeclared-field rejection, known
  purpose values, nullable audit fields, and enum coverage.
- **Pre-existing named test:** `EventTopicsTest` still passes, confirming no regression to R44/R45
  routing.
- **Dependency scope:** `jackson-dataformat-yaml` remains test-scope only.

---

## Traceability Summary

| AC | Covered By | Gap |
|---|---|---|
| AC1 (R44) — email-requested schema matches payload | `EmailRequestedEventPayloadContractTest` (2 tests) | None |
| AC2 (R45) — security-audit schema matches payload | `AuditMirrorPayloadContractTest` (3 tests) | None |
| AC3 (R47) — auth.yaml documents all non-SAS endpoints and schemas match DTOs | `AuthOpenApiContractTest` (7 tests) | Gap 1 (named-test traceability), Gap 2 (hand-maintained tables), Gap 3 (2xx selection order) |
| R44/R45 routing regression | `EventTopicsTest` (pre-existing) | None |

---

**Phase 11 complete — test review written.** Proceed to Phase 12 (Specification Verification) on approval.
