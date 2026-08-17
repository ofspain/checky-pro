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

## `AuthOpenApiContractTest.java` (new — 7 tests, the named test `shouldConformToAuthOpenApiContract`'s intent split across purpose-named methods)

| Test | Verifies |
|---|---|
| `everyControllerHandlerIsDocumentedInAuthYaml` | AC3 (R47), D1a — every one of the 30 real `@RestController` handlers has a matching `path`+`method` entry in `auth.yaml`. Negative-proofed (Phase 6): removing `/admin/audit` from the YAML fails this test with the exact missing route named. |
| `authYamlDocumentsNoRouteThatDoesNotHaveARealHandler` | AC3, D1a (reverse direction) — no stale/aspirational entry exists in `auth.yaml` without a real handler. Negative-proofed (Phase 6): a fake `/fake/nonexistent` entry fails this test by name. |
| `everyComponentSchemaMatchesItsRealDtoShape` | AC3, D1b — all 19 component schemas (10 response shapes + 9 request bodies) match their real DTO's actual serialized shape (required fields present, no undeclared fields) — the same technique as the event contract tests, applied per component. |
| `everyOperationResponseReferencesTheExpectedSchema` (Kimi Phase 8 Finding 1 closure) | AC3 — every one of the 30 operations' documented success-response `$ref` (or array/no-content shape) names the *correct* component, not merely *a* valid one. Negative-proofed (Phase 9): swapping `POST /accounts`'s request `$ref` to the wrong component fails with the exact wrong/expected names. |
| `everyOperationRequestBodyReferencesTheExpectedSchema` (Kimi Phase 8 Finding 1 closure) | AC3 — same proof for the 9 operations with request bodies. |
| `everyAccountStatusValueIsCoveredByTheAccountResponseSchemaEnum` (Kimi Phase 8 Finding 2 closure) | AC3 — `AccountResponse.status`'s OpenAPI enum doesn't silently fall behind `AccountStatus`. |
| `everyAuditOutcomeValueIsCoveredByTheAuditEventResponseSchemaEnum` (Kimi Phase 8 Finding 2 closure) | AC3 — same proof for `AuditEventResponse.outcome`. |

**Named test mapping:** `package.md` §8's `shouldConformToAuthOpenApiContract` is realized as all 7
methods above together, not one single method — each proves a distinct, independently-nameable
failure mode (missing endpoint, stale endpoint, wrong component shape, wrong component *reference*,
enum drift) rather than one monolithic assertion whose failure message would need to explain which
of five different things went wrong.

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
- `mvn -pl services/auth test -Dtest='AuthOpenApiContractTest,EmailRequestedEventPayloadContractTest,AuditMirrorPayloadContractTest'`
  — **12/12 pass** (7 + 2 + 3).
- `mvn -pl services/auth test -Dtest='EventTopicsTest'` — **3/3 pass**, confirms no regression to
  the pre-existing named test.
- Five total negative-proof runs across Phases 6 and 9 (two completeness, one YAML-syntax-error
  discovery, two `$ref`-correctness), each confirmed to fail for the right reason and reverted
  before this manifest was written.

---

**Phase 10 complete — test manifest written.** Proceed to Phase 11 (Kimi test review) on approval.
