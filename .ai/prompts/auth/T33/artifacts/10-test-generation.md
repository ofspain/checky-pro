<!-- MODEL: Claude Sonnet — Phase 10 (Test Generation). -->

# auth · T33 · Phase 10 — Test Generation

Test-only task convention (same as T27/T32): the task's entire deliverable is contract files plus
the tests proving they match reality. All tests were written across Phases 6 and 9 (Kimi's Phase 8
findings extended the original Phase 6 suite before this manifest was written). No new test is
added in this phase — this is purely the manifest.

## `EmailRequestedEventPayloadContractTest.java` (new — 2 tests)

| Test | Verifies |
|---|---|
| `serializedPayloadMatchesTheDocumentedSchema` | AC1 (R44) — `EmailRequestedEventPayload`'s real serialized shape matches `email-requested.v1.schema.json`: all 4 required fields present, no undeclared fields. |
| `bothKnownPurposeValuesSerializeCleanly` | AC1 — both documented `purpose` values (`verify_email`, `password_reset`) round-trip; the schema deliberately leaves `purpose` open (not a closed enum), so this documents known values rather than checking schema-enum coverage. |

## `AuditMirrorPayloadContractTest.java` (new — 3 tests)

| Test | Verifies |
|---|---|
| `serializedPayloadMatchesTheDocumentedSchema` | AC2 (R45) — `AuditMirrorPayload`'s real serialized shape matches `security-audit.v1.schema.json`, including `outcome` enum-membership. |
| `payloadWithNullAccountAndActorUuidStillMatchesTheSchema` | AC2 — proves the schema's `required` list doesn't wrongly demand the genuinely-nullable `accountUuid`/`actorUuid` (Phase 1's confirmed nullability). |
| `everyAuditOutcomeValueIsCoveredByTheSchemaEnum` | AC2 — the schema's `outcome` enum doesn't silently fall behind `AuditOutcome`. |

## `AuthOpenApiContractTest.java` (10 tests after Phase 11 — see gap closures below)

| Test | Verifies |
|---|---|
| `everyControllerHandlerIsDocumentedInAuthYaml` | AC3 (R47), D1a — every one of the 30 real `@RestController` handlers has a matching `path`+`method` entry in `auth.yaml`. Negative-proofed (Phase 6): removing `/admin/audit` from the YAML fails this test with the exact missing route named. |
| `authYamlDocumentsNoRouteThatDoesNotHaveARealHandler` | AC3, D1a (reverse direction) — no stale/aspirational entry exists in `auth.yaml` without a real handler. Negative-proofed (Phase 6): a fake `/fake/nonexistent` entry fails this test by name. |
| `everyComponentSchemaMatchesItsRealDtoShape` | AC3, D1b — all 19 component schemas (10 response shapes + 9 request bodies) match their real DTO's actual serialized shape (required fields present, no undeclared fields) — the same technique as the event contract tests, applied per component. |
| `everyOperationResponseReferencesTheExpectedSchema` (Kimi Phase 8 Finding 1 closure) | AC3 — every one of the 30 operations' documented success-response `$ref` (or array/no-content shape) names the *correct* component, not merely *a* valid one. Negative-proofed (Phase 9): swapping `POST /accounts`'s request `$ref` to the wrong component fails with the exact wrong/expected names. |
| `everyOperationRequestBodyReferencesTheExpectedSchema` (Kimi Phase 8 Finding 1 closure) | AC3 — same proof for the 9 operations with request bodies. |
| `everyAccountStatusValueIsCoveredByTheAccountResponseSchemaEnum` (Kimi Phase 8 Finding 2 closure) | AC3 — `AccountResponse.status`'s OpenAPI enum doesn't silently fall behind `AccountStatus`. |
| `everyAuditOutcomeValueIsCoveredByTheAuditEventResponseSchemaEnum` (Kimi Phase 8 Finding 2 closure) | AC3 — same proof for `AuditEventResponse.outcome`. |
| `shouldConformToAuthOpenApiContract` (Kimi Phase 11 Gap 1 closure) | The named test itself — delegates to the 7 rows above. |
| `expectedResponseSchemasCoverEveryControllerRoute` (Kimi Phase 11 Gap 2 closure) | Meta-guard — the hand-maintained `expectedResponseSchemas()` table can't silently miss a route; negative-proofed by removing an entry and confirming this test (not a downstream one) names the gap. |
| `expectedRequestSchemasCoverEveryRequestBodyHandler` (Kimi Phase 11 Gap 2 closure) | Meta-guard — same guarantee for `expectedRequestSchemas()`, compared against real `@RequestBody`-bearing handlers via reflection. |

**Named test mapping:** `package.md` §8's `shouldConformToAuthOpenApiContract` exists as its own
method (added at Phase 11, closing Kimi's Gap 1 — a single greppable name matching the spec
exactly), which delegates to all 7 methods above so each still reports its own specific failure
message rather than one opaque pass/fail.

## Kimi Phase 11 test review — gaps closed

| Gap | Disposition |
|---|---|
| Gap 1 — no single method named `shouldConformToAuthOpenApiContract` | Closed — added, delegates to the 7 purpose-named checks. |
| Gap 2 — `expectedResponseSchemas()`/`expectedRequestSchemas()` are hand-maintained with no guard against a forgotten entry | Closed — added `expectedResponseSchemasCoverEveryControllerRoute` and `expectedRequestSchemasCoverEveryRequestBodyHandler`, each asserting the table's key set exactly matches the real routes (the latter via a new `routesWithRequestBody()` reflection helper detecting `@RequestBody`-annotated parameters). Negative-proofed: removing one entry from `expectedResponseSchemas()` failed the new guard test by name; reverted. |
| Gap 3 — 2xx response selection relied on YAML field-iteration order | Closed — `actualResponseSchema` now picks the numerically-lowest 2xx status explicitly. |
| Gap 4 — contract file path is relative to Surefire's working directory | Accepted, no change — identical to `UserLifecycleEventPayloadContractTest`'s own existing, already-accepted convention; fixing it here alone would be inconsistent with the pattern this task was instructed to mirror, and fixing it everywhere is a separate, unrelated task. |

`AuthOpenApiContractTest` grew from 7 to 10 tests (the named test + 2 guard tests added, the
existing 7 unchanged in behavior aside from Gap 3's determinism fix). T33's full new-test count is
now **15** (2 + 3 + 10).

**`shouldRouteEmailRequestedEventsToAuthEmailRequestedTopic`** — already existed
(`EventTopicsTest.java`, added in an earlier task), reconfirmed green as a regression check, no
new work.

## Deliberately not tested (documented scope boundaries, not gaps)

- Exact HTTP status codes per operation (Phase 9 disposition of Kimi Finding 1 — not reliably
  reflectable; `auth.yaml` still documents them for human/codegen reference).
- Request-body/response *type-level* correctness beyond field-name presence (e.g., a schema
  documenting an integer field as `string` would not be caught) — inherent to the
  `UserLifecycleEventPayloadContractTest` pattern this task was instructed to mirror, not a
  regression (Phase 9 disposition of Kimi Finding 3).
- Reverse `required`-completeness (every non-null sample field must be schema-required) — rejected
  per Phase 9's disposition of Kimi Finding 5 (would produce false positives against
  deliberately-nullable fields populated non-null in a happy-path sample).
- Error (`4xx`) response shapes — out of scope per R47's own literal "responses" wording as
  narrowed at Phase 4 D2; `auth.yaml` doesn't even document per-operation error schemas beyond the
  service-wide `application/problem+json` convention already established elsewhere.
- A hypothetical bare-`@RequestMapping` handler with no explicit HTTP method (Phase 7/Kimi Finding
  4) — documented as a dormant limitation via an in-code comment, not fixed; no handler in this
  codebase triggers it.

## Verification performed

- `mvn -pl services/auth clean test-compile` — clean, no errors.
- `mvn -pl services/auth test -Dtest='AuthOpenApiContractTest,EmailRequestedEventPayloadContractTest,AuditMirrorPayloadContractTest,EventTopicsTest'`
  — **18/18 pass** (10 + 2 + 3 + 3).
- Six total negative-proof runs across Phases 6, 9, and 11 (two completeness, one
  YAML-syntax-error discovery, two `$ref`-correctness, one guard-test-catches-a-forgotten-table-entry),
  each confirmed to fail for the right reason and reverted before this manifest was finalized.

---

**Phase 10 complete — test manifest written and updated post-Phase-11.** Proceed to Phase 12
(Specification Verification) on approval.
