<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review). -->

# crypto · T23 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `crypto-service` |
| **Task** | T23 — Contracts |
| **Model** | Kimi 2.7 |
| **Consumed** | `artifacts/10-test-generation.md` · current contract files (`contracts/api/crypto-internal.yaml`, `contracts/events/chain/*.v1.schema.json`) · current test sources |
| **Produces** | `artifacts/11-test-review.md` |

## Note on repository state

The Phase 8 missing-contract-files issue was resolved in Phase 9 (`21e056a`); all six contract files are now committed and the tests compile against them. This review evaluates the tests against the **current** committed contracts and acceptance criteria.

## Named test status

`CryptoInternalOpenApiContractTest.shouldConformToCryptoInternalOpenApiContract` exists and delegates to the seven constituent checks as required by `package.md` §8. The two self-guarding tests (`expectedResponseSchemasCoverEveryControllerRoute`, `expectedRequestSchemasCoverEveryRequestBodyHandler`) are present and run independently. No duplicate tests or obvious flakiness were found.

## Gaps

### 1. Event-payload tests do not verify `idempotencyKey` requiredness for tx events or its absence in `provider-degraded` (AC3 gap)

- **Gap:** `SeenPayloadContractTest`, `ConfirmedPayloadContractTest`, `FinalizedPayloadContractTest`, and `ReorgedPayloadContractTest` iterate `schema.get("required")` and assert each listed field is present in the serialized payload, but they never assert that `idempotencyKey` itself is listed in `required`. Conversely, `ProviderDegradedPayloadContractTest` does not assert that `idempotencyKey` is absent from `provider-degraded`'s `properties` (or `required`).
- **Why it matters:** AC3 requires the schemas to "correctly document `idempotencyKey`'s presence (4 events) or its deliberate, explained absence (`provider-degraded`)." A regression could remove `idempotencyKey` from `required` in a tx-event schema or accidentally add it to `provider-degraded`, and the current payload tests would still pass. The Phase 10 traceability matrix incorrectly states that the tests assert `idempotencyKey` is in `required`.
- **Suggested test:** Add two small assertions to the event tests: (a) for tx events, `assertThat(schema.requiredContains("idempotencyKey")).isTrue()`; (b) for `provider-degraded`, `assertThat(schema.properties.has("idempotencyKey")).isFalse()`.

### 2. `additionalProperties: false` is not enforced for any event schema

- **Gap:** None of the five event payload tests inspect the `additionalProperties` keyword.
- **Why it matters:** The schemas declare `additionalProperties: false` to prevent forward-incompatible producer drift. If a future change removes this keyword, strict-schema consumers will accept extra fields that the contract does not authorize, silently breaking compatibility.
- **Suggested test:** Assert `schema.get("additionalProperties").asBoolean() == false` in each event payload contract test.

### 3. Event schema property types/formats/enums are not verified

- **Gap:** The payload tests only check field presence and declaration. They do not assert that `watchId` uses `format: uuid`, that `occurredAt` uses `format: date-time`, that `confirmations` is `type: integer`, or that `chain`/`reason` enums match their Java counterparts.
- **Why it matters:** A schema regression (e.g., `watchId` becoming plain `string`, `occurredAt` losing `date-time`, `confirmations` becoming `number`) would not fail any existing test, weakening the contract's value for consumers and codegen.
- **Suggested test:** Add a shared helper that, given a schema property, asserts declared `type`/`format`/`enum` against a table of expected values for each event schema; or extend each test to spot-check critical fields.

### 4. OpenAPI security requirements are not contract-tested

- **Gap:** `CryptoInternalOpenApiContractTest` does not inspect `paths.*.*.security` or `components.securitySchemes`. It cannot verify that the three internal endpoints require `bearerAuth: [internal.crypto:write]` or that the verification-keys endpoint declares `security: []`.
- **Why it matters:** Security is part of the contract (the frozen brief §Scope explicitly lists the scope for each internal route). A regression that drops `security` from an internal route or omits `bearerAuth` from the schema would not be caught.
- **Suggested test:** Add `everyInternalRouteRequiresBearerAuthWithInternalCryptoWriteScope` and `publicVerificationKeysEndpointHasNoSecurity`, plus an assertion on `components.securitySchemes.bearerAuth`.

### 5. `RegisterWatchRequest.chain` / `AttestRequest.chain` enum drift against `Chain` Java enum is unguarded

- **Gap:** The schemas declare `enum: [ETHEREUM, TRON]` for both request `chain` fields. The component-shape test checks only that the field is declared, not that its enum matches `com.themistra.crypto.adapter.Chain.values()`.
- **Why it matters:** Adding a new `Chain` value on the Java side without updating the contract would create a runtime value that the schema rejects, but the existing tests would pass.
- **Suggested test:** Add an enum-drift guard mirroring `ProviderDegradedPayloadContractTest.everyDegradationReasonValueIsCoveredByTheSchemaEnum`: iterate `Chain.values()` and assert each appears in the schema enum for both `RegisterWatchRequest.chain` and `AttestRequest.chain`.

### 6. `RegisterWatchResponse.status` type and value are not verified

- **Gap:** `everyComponentSchemaMatchesItsRealDtoShape` checks that `status` is declared and present, but not that it is `type: string` (rather than enum) or that the example value `"REGISTERED"` is allowed.
- **Why it matters:** The DTO deliberately serializes `status` as a plain string (`WatchController` converts `watch.status().name()`). If the schema drifts to an enum or a different type, the component test would still pass.
- **Suggested test:** Assert `RegisterWatchResponse.properties.status.type == "string"` and that the property has no `enum`.

### 7. AC6 (`tx-finalized` verbatim fidelity to `design.md`) is not enforced by CI

- **Gap:** `tx-finalized.v1.schema.json` is asserted only via `FinalizedPayloadContractTest` (payload conforms to schema). There is no automated test proving the file is byte-for-byte identical to `design.md` §4c's JSON snippet.
- **Why it matters:** AC6 explicitly requires verbatim fidelity. A future edit that changes `description`, `required`, or adds/removes properties would not fail any JUnit test, even though it would violate AC6.
- **Suggested test:** Embed the expected `tx-finalized` JSON text in a test and assert `Files.readString(SCHEMA_PATH).trim().equals(expected)`.

### 8. `provider-degraded` schema `reason` enum could silently gain extra values

- **Gap:** `everyDegradationReasonValueIsCoveredByTheSchemaEnum` checks that all Java enum values are in the schema, but not that the schema contains no values absent from `DegradationReason`.
- **Why it matters:** A schema that adds a future reason not yet implemented in Java would pass the existing test while causing deserialization/validation issues for consumers.
- **Suggested test:** Assert the schema enum and `DegradationReason.values()` are the same set (equal size and contains-all-both-directions).

## Minor notes (not gated)

- `MoneyFieldsAreDecimalStringsContractTest` only checks `type: string` when a monetary field is present; it does not assert that `expectedAmount`/`amount` exist where expected. This is acceptable because the component/payload tests already enforce presence via `required`.
- `CryptoInternalOpenApiContractTest.parseSchemaNode` only handles `$ref` schema nodes. This is fine for the current contract, but any future inline/array response schema will cause an `IllegalStateException`. Consider adding `arrayOfRef` handling if the contract ever evolves.
