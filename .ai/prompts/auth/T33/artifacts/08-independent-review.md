<!-- MODEL: Kimi 2.7 — Phase 8 (Independent Code Review). -->

# auth · T33 · Phase 8 — Independent Code Review

Consumed `artifacts/07-self-review.md`, the three new contract files, the three new contract test
files, and the updated `services/auth/pom.xml`. Ran the new tests locally: **8/8 pass**. No conflicts
with LOCKED decisions (none scoped to this task) or `agents.md` standing rules.

---

## Finding 1 — Operation-level request/response schema references are not verified

**Evidence:** `AuthOpenApiContractTest` has three independent checks:

1. `everyControllerHandlerIsDocumentedInAuthYaml` — controller routes ⊆ YAML routes.
2. `authYamlDocumentsNoRouteThatDoesNotHaveARealHandler` — YAML routes ⊆ controller routes.
3. `everyComponentSchemaMatchesItsRealDtoShape` — each named component schema matches a real DTO's
   serialized shape.

None of these asserts that a given operation's `requestBody` references the correct request schema,
or that its `200`/`201` response references the correct response schema. For example, if the YAML
for `POST /accounts` accidentally referenced `#/components/schemas/VerifyEmailRequest` instead of
`RegisterAccountRequest`, all three existing tests would still pass: the route is documented, every
route is real, and `VerifyEmailRequest`'s own component schema matches its DTO.

**Recommendation:** Add a fourth test (or extend the route-completeness tests) that, for each
controller handler, resolves the actual request/response DTO type from the method signature and
asserts the corresponding YAML operation uses the matching component schema `$ref`. This can be
reflection-based and remain Testcontainers-free.

**Confidence:** High.

---

## Finding 2 — No enum-coverage verification for OpenAPI component schemas

**Evidence:** `UserLifecycleEventPayloadContractTest` and `AuditMirrorPayloadContractTest` both have
dedicated tests proving every Java enum value is covered by the corresponding JSON Schema enum
(`everyAccountStatusValueIsCoveredByTheSchemaEnum`,
`everyAuditOutcomeValueIsCoveredByTheSchemaEnum`). `AuthOpenApiContractTest` has no equivalent for
its OpenAPI enum-typed fields: `AccountResponse.status` (all `AccountStatus` values) and
`AuditEventResponse.outcome` (all `AuditOutcome` values). The sample instances use only one value
per enum.

If a new `AccountStatus` or `AuditOutcome` value were added in Java but `auth.yaml` were not
updated, `everyComponentSchemaMatchesItsRealDtoShape` could still pass depending on which sample
value was serialized.

**Recommendation:** Add enum-coverage tests for each OpenAPI component schema that contains an enum,
matching the event-contract test pattern exactly.

**Confidence:** High.

---

## Finding 3 — Component-schema matching is structural only; field types and nested shapes are not checked

**Evidence:** `everyComponentSchemaMatchesItsRealDtoShape` verifies (a) required fields are present
and (b) every serialized field is declared in the schema. It does not verify that a schema property's
`type`/`format` matches the Java field's serialized type, or that nested object schemas match nested
DTO shapes. For example, `AuditEventResponse.details` is declared as `type: object` with
`additionalProperties: true`; the test only checks that `details` is present in the schema, not that
its inner structure permits arbitrary JSON.

This is consistent with the existing event-contract test pattern, but OpenAPI schemas are more
complex (nested objects, arrays, `$ref` chains). A type mismatch (e.g., an integer field documented
as string) would not be caught.

**Recommendation:** Document this limitation explicitly in the test's Javadoc and the Phase 10
manifest. If stronger type checking is desired, consider a shallow recursive check that compares
Jackson node types (`TextNode`, `NumberNode`, `BooleanNode`, `ArrayNode`, `ObjectNode`) against the
schema's declared `type` for primitive fields.

**Confidence:** Medium.

---

## Finding 4 — `controllerRoutes()` silently skips bare `@RequestMapping` handlers (already noted in self-review)

**Evidence:** `AuthOpenApiContractTest.controllerRoutes()` loops over `mapping.method()` and adds a
route only when the method array is non-empty. A bare `@RequestMapping("/x")` with no `method`
attribute yields an empty array, so the handler is omitted from both forward and reverse checks
without warning. The self-review correctly flags this as a future-footgun.

**Recommendation:** Either guard against it (assert every discovered `@RequestMapping` has at least
one HTTP method, or treat an empty method array as "all methods" and document it), or add a code
comment in `controllerRoutes()` referencing the self-review finding so the limitation is visible in
the source, not only in the Phase 7 artifact.

**Confidence:** Medium.

---

## Finding 5 — The `required` list is verified in only one direction

**Evidence:** `everyComponentSchemaMatchesItsRealDtoShape` checks that every field named in the
schema's `required` array is present in the serialized DTO. It does not check the converse: that
every non-null Java field is marked `required` in the schema. If a genuinely non-null field were
accidentally omitted from the `required` list, the test would still pass.

**Recommendation:** For component schemas that are meant to be strict, add a check that every field
whose sample instance is non-null appears in the schema's `required` array. Be careful with
explicitly nullable fields (`SessionResponse.deviceLabel`, `ApiKeyMetadata.lastUsedAt`, etc.) — the
test should only enforce non-null sample values as required.

**Confidence:** Medium.

---

## Non-Issues Confirmed

- **Completeness:** `auth.yaml` documents 29 route/method pairs (the count the implementation
  surfaced), and the bidirectional route check confirms they match the controller handlers.
- **Event schemas:** `email-requested.v1.schema.json` correctly marks `token` required and documents
  it as the intentional exception; `security-audit.v1.schema.json` correctly omits
  `accountUuid`/`actorUuid` from `required` and covers both `AuditOutcome` values.
- **Nullability handling:** `AuditMirrorPayloadContractTest.payloadWithNullAccountAndActorUuidStillMatchesTheSchema`
  directly verifies the nullable audit fields.
- **Purpose values:** `EmailRequestedEventPayloadContractTest.bothKnownPurposeValuesSerializeCleanly`
  covers both documented `purpose` strings.
- **Module boundaries:** `AuthOpenApiContractTest`'s cross-module imports are appropriately placed in
  `common` and do not trip existing ArchUnit rules.
- **Dependency scope:** `jackson-dataformat-yaml` is test-scope only, consistent with the brief.
- **Naming/location:** All file names and packages match the brief and existing conventions.

---

## Open Questions

1. Should operation-level request/response schema references be verified before this task is
   considered complete? (Finding 1.)
2. Is the one-directional `required` check sufficient, or should non-null sample fields be enforced
   as `required` in the schema? (Finding 5.)

---

**Phase 8 complete — independent review written.** Proceed to Phase 9 (human disposition) on approval.
