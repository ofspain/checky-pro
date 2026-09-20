# crypto · T23 · Phase 10 — Test Generation

**Process note.** Per this task's own Phase 6 implementation notes (and the established T19-T22
precedent), tests were written alongside the contract files themselves. Phase 9 extended one test
method and added no new one (strengthened `watchIdPathParameterIsDeclaredOnTheDeleteOperation`'s
assertion). No production code exists for this task — it authors contracts and the tests that verify
them. This artifact is the traceability manifest.

## Test files (this task's own new files)

| File | Tests | Purpose |
|---|---|---|
| `common/CryptoInternalOpenApiContractTest.java` | 10 | Full OpenAPI-contract conformance: route completeness both directions, every component schema against its real DTO shape, the `watchId` path parameter's full declaration, the `AttestResponse.outcome` enum-drift guard, request/response `$ref` expectation tables, and the two self-guarding "expected-map covers every real route" tests. |
| `watch/SeenPayloadContractTest.java` | 1 | `chain.tx.seen` payload structural fidelity. |
| `watch/ConfirmedPayloadContractTest.java` | 1 | `chain.tx.confirmed` payload structural fidelity. |
| `watch/FinalizedPayloadContractTest.java` | 1 | `chain.tx.finalized` payload structural fidelity (schema itself is the verbatim-copy AC6 proof; see below). |
| `reorg/ReorgedPayloadContractTest.java` | 1 | `chain.tx.reorged` payload structural fidelity. |
| `provider/ProviderDegradedPayloadContractTest.java` | 2 | `chain.provider.degraded` payload structural fidelity, plus the `DegradationReason` enum-drift guard. |
| `MoneyFieldsAreDecimalStringsContractTest.java` | 2 | Centralized money-discipline check across all 6 contract files (OpenAPI schemas + all 5 event schemas). |

**Total: 18 test methods**, all passing.

## Traceability matrix

| Test | AC | What it proves |
|---|---|---|
| `CryptoInternalOpenApiContractTest.shouldConformToCryptoInternalOpenApiContract` (named test, runs all 7 constituent checks) | AC1 | All 4 real routes documented, no orphan routes, every schema matches its real DTO, `$ref` tables match, expectation tables complete. |
| `CryptoInternalOpenApiContractTest.watchIdPathParameterIsDeclaredOnTheDeleteOperation` | AC1 (Finding #2) | `watchId` is declared `in: path`, `required: true`, `schema.type: string`, `schema.format: uuid` (strengthened at Phase 9). |
| `CryptoInternalOpenApiContractTest.everyComponentSchemaMatchesItsRealDtoShape` (both `AttestResponse-signed`/`-blocked` keys) | AC1 (Finding #4) | Both real `AttestResponse` variants match the one `AttestResponse` schema. |
| `SeenPayloadContractTest`, `ConfirmedPayloadContractTest`, `FinalizedPayloadContractTest`, `ReorgedPayloadContractTest`, `ProviderDegradedPayloadContractTest.serializedPayloadMatchesTheDocumentedSchema` | AC2 | All 5 real event payload records serialize to exactly what their schema declares (required-present, no undeclared field). |
| `SeenPayloadContractTest`, `ConfirmedPayloadContractTest`, `FinalizedPayloadContractTest`, `ReorgedPayloadContractTest` (each asserts `idempotencyKey` is in `required`); `ProviderDegradedPayloadContractTest` (asserts the real payload has no `idempotencyKey` field, matching the schema's deliberate omission) | AC3 | `idempotencyKey`'s presence (4 `chain.tx.*` events) vs. deliberate, explained absence (`provider-degraded`) both correctly documented. |
| `MoneyFieldsAreDecimalStringsContractTest.everyMonetaryFieldInTheOpenApiContractIsADecimalStringNeverANumber` / `.everyMonetaryFieldInEveryEventSchemaIsADecimalStringNeverANumber` | AC4 | Every `expectedAmount`/`amount` property across all 6 contract files is `type: string`, never a JSON number. |
| `ProviderDegradedPayloadContractTest.everyDegradationReasonValueIsCoveredByTheSchemaEnum` | AC5 | `DegradationReason.values()` all appear in the schema's `reason` enum. |
| `CryptoInternalOpenApiContractTest.attestResponseOutcomeSchemaEnumMatchesTheTwoValuesTheDtoCanActuallyProduce` | AC5 | `AttestResponse.outcome`'s schema enum is exactly `{SIGNED, BLOCKED}` — the two values the DTO can actually produce (deliberately narrower than the full 3-value `AttestOutcome` Java enum; `REFUSED` is structurally impossible in a `200`). |
| N/A — verified by direct `diff` at Phase 6/7, not a JUnit test | AC6 | `tx-finalized.v1.schema.json` is byte-for-byte identical to `design.md` §4c's given JSON text. A JUnit test can't assert "verbatim copy of a markdown-embedded snippet" meaningfully; this was verified by direct comparison during authoring and re-confirmed during Phase 8/9 (`FinalizedPayloadContractTest` proves the *real payload* conforms to that schema, which is the executable half of AC6). |

## Verification run

`mvn -pl services/crypto test -Dtest=CryptoInternalOpenApiContractTest,SeenPayloadContractTest,ConfirmedPayloadContractTest,FinalizedPayloadContractTest,ReorgedPayloadContractTest,ProviderDegradedPayloadContractTest,MoneyFieldsAreDecimalStringsContractTest`
— 18/18 pass (Phase 9's strengthened assertion included).

Full module regression (`mvn -pl services/crypto -am test`): 738 tests, 6 failures, all pre-existing and
unrelated to this task (disclosed since T18-T22). Zero regressions.

## Gaps

None identified beyond what Phase 7/8/9 already surfaced and resolved (at the time this section was
first written — see the Phase 11 additions below).

## Phase 11 (Kimi Test Review) additions

Per this pipeline's own Phase 11 convention, no separate resolution artifact is written — accepted
findings are folded directly into this artifact and the test suite. Kimi raised 8 gaps, all framed as
regression-lock additions against specific ACs. 7 were accepted; 1 was rejected as a deliberate,
precedent-matching scope boundary.

Kimi also correctly caught that the AC3 row above (line 31) overstated what the tests asserted *before*
this phase: the tx-event tests iterated `schema.get("required")` without ever checking that
`idempotencyKey` itself was in that list, and `ProviderDegradedPayloadContractTest` never asserted
`idempotencyKey`'s absence. Gap 1 below closes exactly that gap, so the row is now accurate as written.

| Gap | Disposition | Test added |
|---|---|---|
| 1. Event-payload tests don't verify `idempotencyKey` requiredness (4 tx events) or its absence (`provider-degraded`) — AC3 | **ACCEPTED** | Added a `requiredFields.contains("idempotencyKey")` assertion to `SeenPayloadContractTest`/`ConfirmedPayloadContractTest`/`FinalizedPayloadContractTest`/`ReorgedPayloadContractTest`; added a `declaredProperties.has("idempotencyKey")` `isFalse()` assertion to `ProviderDegradedPayloadContractTest`. |
| 2. `additionalProperties: false` never actually checked in any event schema test (the assertion messages claimed to enforce it but only checked field-name coverage) | **ACCEPTED** | Added `schema.get("additionalProperties").asBoolean().isFalse()` to all 5 event payload contract tests. |
| 3. Event schema property types/formats/enums (`watchId` format, `occurredAt` format, `confirmations` type, etc.) not verified beyond name-presence | **REJECTED — deliberate scope boundary** | This contract-testing technique is explicitly scoped to structural (name) matching, documented in `UserLifecycleEventPayloadContractTest`'s own Javadoc ("a structural check ... rather than adding a JSON-Schema-validation library"), and auth's own precedent test doesn't do exhaustive per-field type/format checking either. None of AC1-AC6 calls for it. Exhaustive type verification for every field of every schema is a larger, separately-scoped effort (a real JSON-Schema validator, or a much larger hand-written assertion table) not warranted by this task's brief. |
| 4. OpenAPI `security` requirements (bearerAuth scope on internal routes, no-auth on the well-known endpoint) not contract-tested | **ACCEPTED** | Added `everyInternalRouteRequiresBearerAuthWithInternalCryptoWriteScope` and `publicVerificationKeysEndpointHasNoSecurity` to `CryptoInternalOpenApiContractTest`. |
| 5. `RegisterWatchRequest.chain`/`AttestRequest.chain` enum drift against `Chain.values()` unguarded | **ACCEPTED** | Added `chainEnumsInRequestSchemasCoverEveryChainValue`, mirroring the existing `DegradationReason`/`AttestOutcome` enum-drift-guard technique. |
| 6. `RegisterWatchResponse.status` type/enum-absence not verified | **ACCEPTED** | Added `registerWatchResponseStatusIsAPlainStringNotAnEnum`. |
| 7. AC6 (`tx-finalized` verbatim fidelity) enforced only by a one-time manual `diff`, not by any automated test | **ACCEPTED** | Added `FinalizedPayloadContractTest.schemaIsByteForByteEquivalentToDesignMdSection4c` — extracts the fenced JSON block from `design.md` §4c and asserts `JsonNode`-tree equality against the committed schema file (structural, not raw-string, comparison — survives incidental whitespace differences from the markdown fence while still catching any real content drift). |
| 8. `provider-degraded`'s `reason` enum could silently gain a value `DegradationReason` doesn't have | **ACCEPTED** | Strengthened `everyDegradationReasonValueIsCoveredByTheSchemaEnum` with an additional bidirectional `containsExactlyInAnyOrderElementsOf` assertion. |

**Verification run (Phase 11):**
`mvn -pl services/crypto test -Dtest=CryptoInternalOpenApiContractTest,SeenPayloadContractTest,ConfirmedPayloadContractTest,FinalizedPayloadContractTest,ReorgedPayloadContractTest,ProviderDegradedPayloadContractTest,MoneyFieldsAreDecimalStringsContractTest`
— 23/23 pass (was 18/18 before this phase's 5 new test methods: 4 in `CryptoInternalOpenApiContractTest`,
1 in `FinalizedPayloadContractTest`; the remaining accepted gaps strengthened existing test bodies rather
than adding new methods).

Full module regression (`mvn -pl services/crypto -am test`): 743 tests, 6 failures, 0 errors — the same 6
pre-existing, unrelated failures disclosed since T18-T22, confirmed by name: `ProviderHealthRepositoryIntegrationTest.deleteStillFailsAtTheDatabaseLevel`,
`ObservationRepositoryIntegrationTest.savedObservationRoundTripsEveryFieldIncludingTheJsonPayloadAndTheConvertedFactType`,
`ObservationRepositoryIntegrationTest.repositoryHasNoUpdateOrDeleteMethodReachableAtTheDatabaseLevel`,
`QuorumDecisionRepositoryIntegrationTest.repositoryHasNoUpdateOrDeleteMethodReachableAtTheDatabaseLevel`,
`TokenAllowlistRepositoryIntegrationTest.deleteFailsAtTheDatabaseLevel`,
`TokenAllowlistRepositoryIntegrationTest.findCurrentVersionEntryScopesToPerChainMaxVersionIndependently`
— all pre-existing DB-permission-grant issues in modules this task never touches. Zero regressions.
