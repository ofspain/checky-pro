# payment · T24 · Phase 5 — Implementation Plan

Consumes: `artifacts/04-frozen-task-brief.md` (FROZEN)

Planning only, no code. Every file below traces to the frozen brief's Files sections. The two
test classes are this task's own deliverables (R31's named test), so they are written in Phase 6
alongside the contracts. This follows the crypto T23 contracts-task precedent. Phase 10 then
reviews and extends them; it does not author them from scratch.

All record shapes, nullability, topics and keys below were re-read from source this phase.

## Files to create

1. `contracts/events/payments/invoice-created.v1.schema.json`
2. `contracts/events/payments/payment-seen.v1.schema.json`
3. `contracts/events/payments/payment-finalized.v1.schema.json`
4. `contracts/events/payments/receipt-issued.v1.schema.json`
5. `contracts/api/payments.yaml`
6. `services/payment/src/test/java/com/themistra/payment/common/PaymentsOpenApiContractTest.java`
7. `services/payment/src/test/java/com/themistra/payment/events/PaymentEventPayloadContractTest.java`

## Files to modify

1. `services/payment/pom.xml`
   - Add `<dependency><groupId>com.fasterxml.jackson.dataformat</groupId><artifactId>jackson-dataformat-yaml</artifactId><scope>test</scope></dependency>`.
     It has no version (Boot BOM-managed), copied exactly from `services/notification/pom.xml:168`.
   - Update the standing T08 comment (`pom.xml:90-91`) so it no longer says the dependency is
     "still deliberately NOT added". This is a comment-only edit on the one authorized file.

## Event schemas: planned shape

Common to all four files:
- `$schema`: draft 2020-12
- `$id`: `https://checky.pro/contracts/events/payments/<file>`
- `title`: `"<topic> (schema_version 1)"`
- `type: object`
- `additionalProperties: false`
- `format: uuid` on every `UUID` component, `format: date-time` on every `Instant` component
- money `amount` is `type: string` with the "decimal string, never a JSON number" description
  (L6)
- `description` ends with `Partition key = <field>.` (G2)
- nullable fields are `type: ["string","null"]` and absent from `required` (Finding 2)

Topics come from `EventTopics.java:13-16`.

| File | Topic | Properties (record order) | Not required | Nullable | Partition key |
|---|---|---|---|---|---|
| `invoice-created` | `payments.invoice.created` | invoiceUuid, merchantAccountUuid, chain, tokenContractAddress, tokenDecimals (integer), amount, payToAddress, reference, expiresAt | reference | reference | invoiceUuid |
| `payment-seen` | `payments.payment.seen` | paymentUuid, invoiceUuid, chain, txHash, tokenContractAddress, confirmations (integer), occurredAt | none | none | paymentUuid |
| `payment-finalized` | `payments.payment.finalized` | paymentUuid, chain, txHash, fromAddress, toAddress, tokenContractAddress, amount, confirmations (integer), addressPoisoningFlag (boolean), occurredAt | fromAddress, toAddress | fromAddress, toAddress | paymentUuid |
| `receipt-issued` | `payments.receipt.issued` | Same as `design.md` §4c (11 props) | merchantAccountUuid, amount, tokenContractAddress, kmsKeyId, receiptUri (pinned `required` = the other 6) | none | receiptUuid |

`receipt-issued` is a copy of `design.md` §4c. The only change is that its description sentence
says `Partition key = receiptUuid.` instead of `invoiceUuid` (G1/G2).

## `payments.yaml`: planned shape

**Header**
- `openapi: 3.0.3`
- `info.title: payment-service API`, `info.version: "1.0"`
- `info.description` says per-route error responses are out of scope (R47) and that the
  `Problem` schema catalogues the error `type` URIs (G3)
- `servers: [{url: /}]`

**Paths.** Every operation has `security: [bearerAuth: [MERCHANT]]` (Finding 7).

| Route | operationId | Request | Success response |
|---|---|---|---|
| `POST /api/v1/invoices` | `createInvoice` | `$ref CreateInvoiceRequest` (required) | `201` `application/json` `$ref InvoiceResponse` |
| `GET /api/v1/invoices` | `listInvoices` | none | `200` `application/json` array of `$ref InvoiceResponse` |
| `GET /api/v1/invoices/{invoiceUuid}` | `getInvoice` | path param `invoiceUuid` (required, `string`/`uuid`) | `200` `$ref InvoiceResponse` |
| `GET /api/v1/exports/transactions` | `exportTransactions` | see parameters below | `200` with two content types, see below |

Export parameters (Finding 9):
- query `from` and `to`: required, `string`/`date-time`
- query `format`: optional, `enum [csv, json]`, description notes it is case-insensitive
- header `Accept`: optional, description gives the selection rule from `ExportController`'s
  Javadoc

Export responses (Finding 3):
- `application/json`: array of `$ref TransactionExportRow`
- `text/csv`: `{type: string}`, description lists the 12-column header in `toCsv` order
- `headers.Content-Disposition`: documented as present on the CSV variant only

**`components.securitySchemes.bearerAuth`:** `type: http`, `scheme: bearer`, `bearerFormat: JWT`.

**`components.schemas`:** exactly these four.
- **`CreateInvoiceRequest`**
  - required: chain, tokenContractAddress, tokenDecimals, amount, payToAddress
  - `chain`: `enum [ETHEREUM, TRON]`
  - `tokenDecimals`: integer, 0–255
  - `amount`: `string`; description says string is canonical and a JSON number is also accepted
  - `reference`: `string`, `nullable: true`, `maxLength: 140`
- **`InvoiceResponse`**
  - 12 properties
  - required: every property except `reference`, which is `nullable: true`
  - `state`: inline `enum`, all six `InvoiceState` values
- **`TransactionExportRow`**: 12 properties, all required (the inner joins guarantee non-null)
- **`Problem`** (G3)
  - `type`: `string`/`uri`, `enum` of the 11 `ProblemTypes` URIs
  - `title`: string, `status`: integer, `detail`: string, `instance`: string/uri
  - required: `type`, `title`, `status`

## Public methods (signatures)

Both test classes are package-private with plain JUnit 5 + AssertJ + Jackson. Every `@Test`
method is `void ...() throws Exception`.

### `PaymentsOpenApiContractTest` (`com.themistra.payment.common`)

Constants and fields:
- `CONTRACT_PATH = Path.of("../../contracts/api/payments.yaml")`
- `CONTROLLERS = List.of(InvoiceController.class, ExportController.class)`
- `objectMapper`: same configuration as the notification sibling
- `yamlMapper = new YAMLMapper()`

Mirrored from `NotificationOpenApiContractTest` and crypto T23:
- `everyControllerHandlerIsDocumentedInPaymentsYaml`
- `paymentsYamlDocumentsNoRouteThatDoesNotHaveARealHandler`
- `componentSchemasAreExactlyTheFourExpected`: the `components.schemas` key set equals
  {CreateInvoiceRequest, InvoiceResponse, TransactionExportRow, Problem}.
- `everyComponentSchemaMatchesItsRealRecordShape` (AC1, AC7, Finding 2). For each record-backed
  component, it asserts:
  - the `properties` key set equals `getRecordComponents()` names (both directions)
  - the `required` set equals the expected set from `expectedRequired()`
  - each nullable field has `nullable: true`
  - each component's Java type matches its schema type/format via `assertTypeMatches`
  - a real instance serialized by `objectMapper` has only declared fields
- `invoiceStateSchemaEnumMatchesInvoiceStateValues` (AC7/Finding 8)
- `createInvoiceRequestChainEnumMatchesItsValidationPattern`: the enum equals {ETHEREUM, TRON}.
- `problemTypeEnumMatchesProblemTypesCatalogue` (AC9): uses reflection over `ProblemTypes`'
  `public static final URI` fields and compares as a set. It also asserts the size is 11.
- `everyOperationRequiresBearerAuthWithMerchantScope` (AC8): asserts the bearerAuth scheme is
  `http`/`bearer`, and every operation's `security[0].bearerAuth` equals `["MERCHANT"]`.
- `invoiceUuidPathParameterIsDeclared`: `in: path`, `required: true`, `string`/`uuid`.
- `exportEndpointDocumentsItsQueryParametersAndAcceptHeader` (Finding 9)
- `exportEndpointDocumentsJsonAndCsvContent` (Finding 3): checks both content types and the
  `Content-Disposition` header.
- `everyOperationResponseReferencesTheExpectedSchema`: the map carries the status code
  (`201` for POST, otherwise `200`).
- `everyOperationRequestBodyReferencesTheExpectedSchema`: one entry,
  `POST /api/v1/invoices` → `CreateInvoiceRequest`.
- `createInvoiceRequestAcceptsADecimalStringAmount` (AC6): reads the JSON with
  `"amount":"10.5"` into `CreateInvoiceRequest` and asserts `isEqualByComparingTo(new
  BigDecimal("10.5"))`.
- `shouldConformToPaymentsOpenApiContract`: the **named test**. It calls every method above in
  order.
- `expectedResponseSchemasCoverEveryControllerRoute`: self-guard, kept outside the umbrella
  exactly as in the sibling.
- `expectedRequestSchemasCoverEveryRequestBodyHandler`: self-guard. Its set is every controller
  method with a `@RequestBody` parameter.

### `PaymentEventPayloadContractTest` (`com.themistra.payment.events`)

Each `<event>MatchesItsSchema` method asserts all of the following (AC2, AC7, Finding 6):
- `additionalProperties` is present and `false`
- the `properties` key set equals the record component names
- the `required` set equals the expected set
- the nullable set gets `["string","null"]`, and every other field gets a single type
- `assertTypeMatches` passes for each component
- the serialized instances have every required field and no undeclared field
- the `description` contains `Partition key = <expected>.` (AC10)

Methods:
- `invoiceCreatedPayloadMatchesItsSchema`: two instances, `reference` set and `reference` null.
- `paymentSeenPayloadMatchesItsSchema`
- `paymentFinalizedPayloadMatchesItsSchema`: two instances, addresses set and addresses null.
- `receiptIssuedPayloadMatchesItsSchema`: the expected `required` is the pinned 6 (G1).
- `everySchemaHasTheFrozenIdAndTitle` (Finding 4): checks the `$id`, `$schema` and `title` for
  each of the four files.

## Private methods

The two classes have no shared test utility, because the brief authorizes no third file. The
small type-mapping helper is duplicated in each class, matching this repo's per-module duplicated
helper precedent.

- `PaymentsOpenApiContractTest`
  - `yamlRoutes()`, `controllerRoutes()`: copied from the sibling.
  - `actualResponseSchema(JsonNode, Route, String status)`, `parseSchemaNode(JsonNode)`,
    `refName(String)`
  - `expectedResponseSchemas()`, `expectedRequestSchemas()`, `realInstancesByComponentName()`,
    `expectedRequired()`, `expectedNullable()`
  - `requestBodyHandlerRoutes()`
  - `assertTypeMatches(String owner, RecordComponent c, JsonNode prop, boolean nullable)`
- `PaymentEventPayloadContractTest`
  - `readSchema(String file)`
  - `assertMatches(String file, Class<? extends Record> type, Set<String> required, Set<String> nullable, String partitionKey, Object... instances)`
  - `assertTypeMatches(...)`: the JSON-Schema variant, which reads a `type` array instead of
    `nullable`.

`assertTypeMatches` maps types as follows:

| Java type | Schema type | Format |
|---|---|---|
| `UUID` | `string` | `uuid` |
| `Instant` | `string` | `date-time` |
| `BigDecimal` | `string` | none |
| `String` | `string` | none |
| `short`, `int` | `integer` | none |
| `boolean` | `boolean` | none |
| `InvoiceState` | `string` | none (checked with an `enum`) |

Any other type fails loudly, so a new field type cannot pass unchecked.

## Entities / repositories / services used

None. The tests read records, controllers, `ProblemTypes` and `InvoiceState` by reflection and
construction only. They use no Spring context and no Testcontainers.

## Unit/integration tests required

The two classes above:
- the named `shouldConformToPaymentsOpenApiContract`
- 4 per-event methods plus the `$id`/title check
- the AC6 string-amount test

Afterwards, run the full payment-service suite to confirm AC5 (the T23 baseline was 461/461
green).

## Execution order

1. `pom.xml` dependency and comment, so both test classes can compile.
2. Event schemas, in this order:
   - `receipt-issued` (a pinned copy, so lowest risk)
   - `invoice-created`
   - `payment-seen`
   - `payment-finalized` (two nullable fields)
3. `payments.yaml`.
4. `PaymentEventPayloadContractTest`, then run it.
5. `PaymentsOpenApiContractTest`, then run it.
6. Run the full `services/payment` suite.

## Open Questions

No blockers. Two planning-level refinements, both within the frozen brief:
- `CreateInvoiceRequest.reference` is documented as `nullable: true`, not just non-required. A
  client may send an explicit `null`, and `@Size` accepts that. This mirrors the brief's own
  rule for `InvoiceResponse.reference`.
- `CreateInvoiceRequest.tokenDecimals` is a primitive `short`, so an omitted value would
  silently deserialize to `0`. The contract still lists it as `required`, because it documents
  what a client must send. Changing the DTO is out of scope (Files NOT to Modify).

The partition-key drift follow-up (G2) is carried forward unchanged.
