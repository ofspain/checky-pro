# payment · T24 · Phase 6 — Implementation Notes

Consumes: `artifacts/04-frozen-task-brief.md` (FROZEN), `artifacts/05-implementation-plan.md`

## Files created (exactly as planned)

1. `contracts/events/payments/receipt-issued.v1.schema.json`
   - Field-for-field copy of `design.md` §4c, including its pinned 6-field `required` (G1).
   - Only change: the description now says `Partition key = receiptUuid.` (G2).
2. `contracts/events/payments/invoice-created.v1.schema.json`
   - 9 properties.
   - `reference` is `["string","null"]` and not required (Finding 2).
   - `tokenDecimals` is an integer, 0–255.
3. `contracts/events/payments/payment-seen.v1.schema.json`
   - 7 properties, all required.
4. `contracts/events/payments/payment-finalized.v1.schema.json`
   - 10 properties.
   - `fromAddress` and `toAddress` are `["string","null"]` and not required (Finding 2,
     widened).
5. `contracts/api/payments.yaml`
   - Covers the 4 routes, `bearerAuth: [MERCHANT]` on every operation, and the export's JSON +
     CSV content, `Content-Disposition` header, `from`/`to`/`format` query params and `Accept`
     header.
   - Has exactly 4 component schemas, including `Problem`, whose `type` enum is the 11
     `ProblemTypes` URIs.
6. `services/payment/src/test/java/com/themistra/payment/events/PaymentEventPayloadContractTest.java`
   - 5 tests.
7. `services/payment/src/test/java/com/themistra/payment/common/PaymentsOpenApiContractTest.java`
   - 17 tests, including the named `shouldConformToPaymentsOpenApiContract`.

## Files modified (exactly as planned)

1. `services/payment/pom.xml`
   - Added test-scope `jackson-dataformat-yaml`, BOM-managed with no version, in its own
     commented block (sibling-identical).
   - Removed the stale clause in the T08 comment that said the dependency was "still
     deliberately NOT added".

2. `services/payment/src/test/java/com/themistra/payment/T01SkeletonRegressionTest.java`.
   **Not in the plan; forced by reality (deviation 0 below).** One assertion flipped from
   `doesNotContain` to `contains`.

No production class was touched.

## Mapping to acceptance criteria

| AC | Where it holds | Verified by |
|---|---|---|
| AC1 | `payments.yaml` paths and 4 components | `everyControllerHandlerIsDocumentedInPaymentsYaml`, `paymentsYamlDocumentsNoRouteThatDoesNotHaveARealHandler`, `componentSchemasAreExactlyTheFourExpected`, `everyComponentSchemaMatchesItsRealRecordShape` (properties = record components in both directions; required = components minus nullable), `exportEndpoint*`, `invoiceUuidPathParameterIsDeclared`, `everyOperationResponse/RequestBodyReferencesTheExpectedSchema` |
| AC2 | 4 event schemas | `<event>MatchesItsSchema` ×4: properties = components, exact `required` set, `additionalProperties: false`, real instances (including null-valued variants) have no undeclared or wrongly-null fields |
| AC3 | every `amount` is `type: string` | the AC7 type mapping (`BigDecimal`/`String` → `string`) |
| AC4 | both directions plus self-guards | `expectedResponseSchemasCoverEveryControllerRoute`, `expectedRequestSchemasCoverEveryRequestBodyHandler`, mutation check below |
| AC5 | `pom.xml` | one dependency added; full-suite result below |
| AC6 | — | `createInvoiceRequestAcceptsADecimalStringAmount` |
| AC7 | — | `assertTypeMatches` in both classes, `invoiceStateSchemaEnumMatchesInvoiceStateValues`, `createInvoiceRequestChainEnumMatchesItsValidationPattern` |
| AC8 | — | `everyOperationRequiresBearerAuthWithMerchantScope` |
| AC9 | — | `problemTypeEnumMatchesProblemTypesCatalogue` (reflection over `ProblemTypes`, size asserted to be 11) |
| AC10 | each schema `description` | the partition-key assertion inside each `<event>MatchesItsSchema` |

## Verification

- **New tests:** 22/22 green (17 OpenAPI + 5 event).
- **Mutation check.** These are temporary edits, reverted afterwards (files restored from a
  scratch copy and confirmed unchanged):
  - Setting `payment-finalized.amount` to `type: number` made
    `paymentFinalizedPayloadMatchesItsSchema` fail (`amount type expected "string"`).
  - Renaming the path `{invoiceUuid}` → `{invoiceId}` made both route-completeness tests and the
    named umbrella test fail.
  - So the guards catch drift; they don't just pass by default.
- **Full suite:** `mvn -o test` in `services/payment` gives **483/483 green** (T23 baseline 461
  plus 22 new). The first run was 482/483; see deviation 0.

## Deviations, disclosed

0. **`T01SkeletonRegressionTest` tripwire flipped. This file is outside the frozen brief's Files
   to Modify.**
   - The first full-suite run failed on one assertion:
     `paymentPomExcludesKmsChainClientsAndDeferredDependencies`, at `T01SkeletonRegressionTest.java:125`.
   - That assertion checked the pom does *not* contain `jackson-dataformat-yaml`. Its message was
     `"jackson-dataformat-yaml lands at task 24, not this task"`, so it was a deliberate tripwire
     meant to flip at exactly this task. Phases 0, 2 and 4 all missed it.
   - AC5 requires both the new dependency *and* a green suite, and those cannot both hold
     without this flip. It is the same pattern T08 used on the ShedLock line directly above
     (`.contains("net.javacrumbs.shedlock")`).
   - The assertion now reads `.as("T24: jackson-dataformat-yaml now expected (PaymentsOpenApiContractTest)").contains("jackson-dataformat-yaml")`.
   - The rest of the test is unchanged.
   - Flagged for Phase 8/9 review.
1. **Tests written in Phase 6, not Phase 10.** The Phase 6 prompt says not to write tests here
   unless the task is test-only. T24's deliverable *is* R31's contract tests: the brief's Files
   to Create lists both classes, and the Phase 5 plan scheduled them here, following the crypto
   T23 contracts-task precedent. Phase 10 should review and extend them, not re-author them.
2. **Small sharpenings beyond the plan's wording. All are tighter, none looser.**
   - The event tests assert required fields are present *and non-null*.
   - They also assert any field serialized as `null` is in the expected-nullable set.
   - `everyOperationResponseReferencesTheExpectedSchema` also asserts each operation documents
     *exactly* its one success status. This stops a stray `200` being added next to the POST's
     `201`.
3. **Two descriptive sentences in `payments.yaml` beyond the plan, both verified against code.**
   - The export description says it returns *attested* payments with `finalizedAt` in
     `[from, to]` (`TransactionExportRepository.java:46-48`).
   - `GET /invoices/{invoiceUuid}` notes it returns 404, not 403, for another merchant's invoice
     (`InvoiceController.java:80-82`, L10).
   - `Problem.required = [type, title, status]` matches what every handler sets
     (`ApiExceptionHandler`, `ResourceServerConfig.writeProblemJson:106-109`).

## Open Questions

None new. The partition-key drift (G2) and `tokenDecimals` primitive-default note (Phase 5) are
carried forward unchanged.
