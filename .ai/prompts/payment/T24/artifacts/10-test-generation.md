# payment · T24 · Phase 10 — Test Manifest

Consumes: `artifacts/04-frozen-task-brief.md` (FROZEN), `artifacts/09-review-resolution.md`.

T24's tests are its deliverable, so they were authored in Phase 6 and refined in Phase 9 (see
`06-implementation-notes.md`, deviation 1). This phase maps every test to its AC or requirement
and closes the gaps Phase 9 left. Phase 9 introduced three contract changes that had no
dedicated test: the request `amount` pattern, the `Problem` extension members, and the
column-order claim in the CSV description. This phase adds **3 tests**, one for each.

The tests are plain JUnit 5 with Jackson and reflection. They use no Spring context and no
Testcontainers; this is the sibling contract-test convention in `agents.md`. There is no `Clock`,
because there is no time-dependent logic: the fixtures use a fixed `Instant`. Production code was
not changed.

## `PaymentsOpenApiContractTest` (21 tests) — `contracts/api/payments.yaml`

| Test | Verifies |
|---|---|
| `shouldConformToPaymentsOpenApiContract` | **Named test (R31)**. An umbrella that runs every check below except the two self-guards. |
| `everyControllerHandlerIsDocumentedInPaymentsYaml` | AC1, AC4: code → contract completeness |
| `paymentsYamlDocumentsNoRouteThatDoesNotHaveARealHandler` | AC1, AC4: contract → code completeness |
| `componentSchemasAreExactlyTheFourExpected` | AC1, G3 |
| `everyComponentSchemaMatchesItsRealRecordShape` | AC1, AC3, AC7, Finding 2: properties = components (both directions), exact `required`, `nullable`, type/format mapping |
| `invoiceStateSchemaEnumMatchesInvoiceStateValues` | AC7, Finding 8 |
| `createInvoiceRequestChainEnumMatchesItsValidationPattern` | AC7, Finding 8 |
| `problemTypeEnumMatchesProblemTypesCatalogue` | AC9, G3 |
| `everyOperationRequiresBearerAuthWithMerchantScope` | AC8, Finding 7, L10 |
| `invoiceUuidPathParameterIsDeclared` | AC1 |
| `exportEndpointDocumentsItsQueryParametersAndAcceptHeader` | AC1, Finding 9 |
| `exportEndpointDocumentsJsonAndCsvContent` | AC1, Finding 3 |
| `everyOperationResponseReferencesTheExpectedSchema` | AC1: exact success status and schema per route |
| `everyOperationRequestBodyReferencesTheExpectedSchema` | AC1 |
| `createInvoiceRequestAcceptsADecimalStringAmount` | AC6, Finding 1 (Phase 9: whole-unit value) |
| `createInvoiceRequestLimitsMatchItsValidationAnnotations` | Kimi #6 (Phase 9) |
| `createInvoiceRequestAmountPatternAdmitsOnlyPositiveWholeBaseUnits` | **New.** AC3, L6, R4. The pattern is exactly `^[1-9][0-9]*$`. It admits `1`, `1000000`, and 2²⁵⁶−1 (78 digits, the `NUMERIC(78,0)` ceiling). It rejects `0`, `-1`, `10.5`, `1e6`, `01`, empty, and leading whitespace. |
| `problemExtensionMembersMatchWhatTheHandlersEmit` | **New.** G3 plus Phase 9 self F4 / Kimi #3. The `violations` items equal `ApiExceptionHandler.Violation`'s components, `trace_id` is a string, and `required` stays `{type, title, status}`. |
| `csvDescriptionListsTheExportColumnsInRecordOrder` | **New.** Finding 3. The documented CSV column list equals `TransactionExportRow`'s component order (`design.md` O5). |
| `expectedResponseSchemasCoverEveryControllerRoute` | AC4 self-guard |
| `expectedRequestSchemasCoverEveryRequestBodyHandler` | AC4 self-guard |

## `PaymentEventPayloadContractTest` (5 tests) — `contracts/events/payments/*.v1.schema.json`

Each of the four per-event tests checks:
- `additionalProperties: false` (Finding 6)
- properties = the record's components
- the exact `required` set
- the exact nullable set, as `["string","null"]`
- the type/format mapping (AC3, AC7)
- that the serialized instances, including null-valued ones, have no undeclared field and no
  wrongly-null field
- that the description states the real partition key (AC10)

| Test | Verifies |
|---|---|
| `invoiceCreatedPayloadMatchesItsSchema` | AC2 (R2 payload); `reference` set and null |
| `paymentSeenPayloadMatchesItsSchema` | AC2 (R8 payload) |
| `paymentFinalizedPayloadMatchesItsSchema` | AC2 (R10 payload); addresses set and null |
| `receiptIssuedPayloadMatchesItsSchema` | AC2 (R25 payload), G1: the pinned 6-field `required` |
| `everySchemaHasTheFrozenIdAndTitle` | Finding 4 |

## Other

- `T01SkeletonRegressionTest.paymentPomExcludesKmsChainClientsAndDeferredDependencies`: the
  tripwire flipped at Phase 6. It now covers AC5 (the dependency is present).

## AC coverage

| AC | Covered by |
|---|---|
| AC1 | `everyControllerHandler…`, `paymentsYamlDocumentsNoRoute…`, `componentSchemas…`, `everyComponentSchema…`, `invoiceUuidPath…`, `exportEndpoint…` ×2, `everyOperationResponse…`, `everyOperationRequestBody…` |
| AC2 | the four `*PayloadMatchesItsSchema` tests |
| AC3 | `assertTypeMatches` in both classes; `…AmountPatternAdmitsOnlyPositiveWholeBaseUnits` |
| AC4 | both completeness directions; both self-guards; mutation checks (Phases 6 and 9) |
| AC5 | `T01SkeletonRegressionTest` (dependency present); full suite green |
| AC6 | `createInvoiceRequestAcceptsADecimalStringAmount` |
| AC7 | `assertTypeMatches`; the two enum tests |
| AC8 | `everyOperationRequiresBearerAuthWithMerchantScope` |
| AC9 | `problemTypeEnumMatchesProblemTypesCatalogue` |
| AC10 | the four `*PayloadMatchesItsSchema` tests (description guard only; see Open Questions) |

There are no state transitions to cover: T24 has no runtime behavior.

## Verification

`mvn -o test` in `services/payment` gives **487/487 green**. That is 484 from Phase 9 plus the 3
new tests.

## Open Questions

None new. Carried forward from earlier phases:
- **G2:** the partition-key drift, plus AC10's description-only guard (self F6).
- **`tokenDecimals`:** a primitive default of `0` when the field is omitted.
