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
first written — see the Phase 11 additions below, once Kimi's test review lands).
