# crypto · T23 · Phase 5 — Implementation Plan

Every file traces to the frozen brief's (`artifacts/04-frozen-task-brief.md`) Files to Create/Modify
sections. Field lists below were re-verified by direct source reading in this phase (`RegisterWatchRequest`/
`Response`, `TxLifecyclePublisher`'s 3 payload records, `ReorgedPayload`, `DegradationReason`,
`AttestOutcome`) — not copied from Phase 0's own summary without re-checking.

## Files to create

1. `contracts/api/crypto-internal.yaml`
2. `contracts/events/chain/tx-seen.v1.schema.json`
3. `contracts/events/chain/tx-confirmed.v1.schema.json`
4. `contracts/events/chain/tx-finalized.v1.schema.json`
5. `contracts/events/chain/tx-reorged.v1.schema.json`
6. `contracts/events/chain/provider-degraded.v1.schema.json`
7. `services/crypto/src/test/java/com/themistra/crypto/common/CryptoInternalOpenApiContractTest.java`
8. `services/crypto/src/test/java/com/themistra/crypto/watch/SeenPayloadContractTest.java`
9. `services/crypto/src/test/java/com/themistra/crypto/watch/ConfirmedPayloadContractTest.java`
10. `services/crypto/src/test/java/com/themistra/crypto/watch/FinalizedPayloadContractTest.java`
11. `services/crypto/src/test/java/com/themistra/crypto/reorg/ReorgedPayloadContractTest.java`
12. `services/crypto/src/test/java/com/themistra/crypto/provider/ProviderDegradedPayloadContractTest.java`

## Files to modify

1. `services/crypto/pom.xml` — add `jackson-dataformat-yaml` (test-scope), copied exactly from
   `services/auth/pom.xml`'s own dependency block (no explicit `<version>`, inherited from Spring
   Boot's BOM).

## `contracts/api/crypto-internal.yaml` — planned structure

```
openapi: 3.0.3
info: {title: crypto-service API, description: ..., version: "1.0"}
servers: [{url: /}]
paths:
  /internal/v1/watches:
    post:  {operationId: registerWatch, security: [bearerAuth: [internal.crypto:write]],
            requestBody: RegisterWatchRequest, responses: {200: RegisterWatchResponse}}
  /internal/v1/watches/{watchId}:
    delete: {operationId: unregisterWatch, security: [bearerAuth: [internal.crypto:write]],
             parameters: [{name: watchId, in: path, required: true, schema: {type: string, format: uuid}}],
             responses: {204: no content}}
  /internal/v1/attest:
    post:  {operationId: attest, security: [bearerAuth: [internal.crypto:write]],
            requestBody: AttestRequest, responses: {200: AttestResponse}}
  /.well-known/themistra-verification-keys:
    get:   {operationId: verificationKeys, security: [], responses: {200: VerificationKeysResponse}}
components:
  securitySchemes:
    bearerAuth: {type: http, scheme: bearer, bearerFormat: JWT}
  schemas:
    RegisterWatchRequest:   {required: [invoiceUuid, chain, address, tokenContractAddress, expectedAmount, expiresAt],
                              properties: {invoiceUuid: uuid, chain: enum[ETHEREUM,TRON], address: string,
                              tokenContractAddress: string, expectedAmount: string, expiresAt: date-time}}
    RegisterWatchResponse:  {required: [watchId, status], properties: {watchId: uuid, status: string}}
    AttestRequest:          {required: [receiptDigestSha256, chain, txHash],
                              properties: {receiptDigestSha256: string, chain: enum[ETHEREUM,TRON], txHash: string}}
    AttestResponse:         {required: [outcome], properties: {signature: string, kmsKeyId: string,
                              signedAt: date-time, outcome: enum[SIGNED,BLOCKED], reason: string}}
    PublicKeyInfo:          {required: [kid, kmsKeyId, alg, publicKeyPem],
                              properties: {kid: string, kmsKeyId: string, alg: string, publicKeyPem: string}}
    VerificationKeysResponse: {required: [keys], properties: {keys: {type: array, items: $ref PublicKeyInfo}}}
```
Verified field-for-field against `RegisterWatchRequest.java:22-28`, `RegisterWatchResponse.java:10`,
`AttestRequest.java` (T21), `AttestResponse.java` (T21), `PublicKeyInfo.java`/`VerificationKeysResponse.java`
(T22) — re-read directly in this phase, not assumed from memory.

**`AttestResponse.outcome`'s documented enum is `[SIGNED, BLOCKED]`, deliberately not all 3
`AttestOutcome.values()`** — `REFUSED` is structurally impossible as a `200` response value (a refusal
is always a `409`, produced by a completely different path, `AttestationRefusedException`, never by
this record). This is a refinement of Phase 3 Finding #5's literal wording ("`AttestOutcome.values()`
are all covered"): the enum-drift guard implemented in Phase 6 will assert the schema's enum equals
exactly `{"SIGNED", "BLOCKED"}` — the actual set of values this specific DTO can ever produce — not the
full 3-value `AttestOutcome` enum, since demanding `"REFUSED"` appear in a `200`-only response schema
would itself be a fidelity violation, not a fidelity improvement. Documented here explicitly so this
deviation from the frozen brief's literal wording is visible, not silent.

## `contracts/events/chain/tx-finalized.v1.schema.json` — content

Copied byte-for-byte from `design.md` §4c's own given JSON (already fully specified there, re-verified
in Phase 3 challenge — no changes, no re-derivation).

## `contracts/events/chain/tx-seen.v1.schema.json` / `tx-confirmed.v1.schema.json` — planned structure

```
required: [idempotencyKey, watchId, chain, txHash, tokenContractAddress, confirmations, occurredAt]
properties: {idempotencyKey: string, watchId: uuid, invoiceUuid: uuid (optional), chain: enum[ETHEREUM,TRON],
             txHash: string, tokenContractAddress: string, confirmations: integer, occurredAt: date-time}
additionalProperties: false
```
Verified against `SeenPayload`/`ConfirmedPayload`'s identical real field list
(`TxLifecyclePublisher.java:118-126`, re-read directly this phase): both records declare exactly these
8 fields, `confirmations` as a primitive `int` (always present, hence required).

## `contracts/events/chain/tx-reorged.v1.schema.json` — planned structure

```
required: [idempotencyKey, watchId, chain, txHash, tokenContractAddress, occurredAt]
properties: {idempotencyKey, watchId, invoiceUuid (optional), chain, txHash, tokenContractAddress, occurredAt}
additionalProperties: false
```
Verified against `ReorgedPayload` (`reorg/ReorgDetector.java:80-82`, re-read directly this phase) — 7
fields, no `confirmations`, no `amount`.

## `contracts/events/chain/provider-degraded.v1.schema.json` — planned structure

```
$id: .../provider-degraded.v1.schema.json
description: "Emitted via the outbox when ProviderHealthTracker transitions a provider to unhealthy
  (R5). Partition key = {chain}:{provider}. Idempotency key = {chain}:{provider}:degraded:{occurredAt}:{UUID}
  (Finding #3) - deliberately NOT the chain:txhash:eventtype format L5 fixes for chain.tx.* events: a
  provider can degrade, recover, and degrade again, so a fixed key would collide with itself on the
  second episode against the outbox's own UNIQUE(idempotency_key). This event's idempotency key is
  passed to OutboxPublisher directly by ProviderDegradedPublisher and is not itself a field of this
  payload body."
required: [chain, provider, reason, occurredAt]
properties: {chain: enum[ETHEREUM,TRON], provider: string, reason: enum[UNHEALTHY,LAGGING,REPEATED_DISAGREEMENT],
             occurredAt: date-time}
additionalProperties: false
```
Verified against `ProviderDegradedPublisher.Payload` (4 fields) and `DegradationReason`'s 3 real enum
values (`UNHEALTHY`, `LAGGING`, `REPEATED_DISAGREEMENT`) — both re-read directly this phase.

## `CryptoInternalOpenApiContractTest` — planned method list

Mirrors `AuthOpenApiContractTest`'s exact 9-method structure, scaled down to 4 routes / 6 schemas:

- `private record Route(String method, String path) {}`
- `CONTROLLERS = List.of(WatchController.class, AttestController.class, VerificationKeysController.class)`
- `everyControllerHandlerIsDocumentedInAuthYaml` → renamed `everyControllerHandlerIsDocumentedInCryptoInternalYaml`
- `cryptoInternalYamlDocumentsNoRouteThatDoesNotHaveARealHandler`
- `everyComponentSchemaMatchesItsRealDtoShape` — `realInstancesByComponentName()` provides **two**
  entries under different synthetic keys for `AttestResponse` (`AttestResponse-signed`,
  `AttestResponse-blocked`, Finding #4), both checked against the same `AttestResponse` schema.
- `everyWatchIdPathParameterIsDeclared` (Finding #2, new) — asserts the `DELETE
  /internal/v1/watches/{watchId}` operation's `parameters` array contains a `watchId` entry
  (`in: path`, `required: true`).
- `everyOperationResponseReferencesTheExpectedSchema` / `everyOperationRequestBodyReferencesTheExpectedSchema`
  — explicit expectation tables (4 routes × 2 tables, far smaller than auth's 30-route tables).
- `attestOutcomeSchemaEnumMatchesTheTwoValuesAttestResponseCanActuallyProduce` (Finding #5, refined per
  this phase's own note above) — asserts the schema's `AttestResponse.outcome` enum equals exactly
  `{"SIGNED", "BLOCKED"}`.
- `shouldConformToCryptoInternalOpenApiContract` — the named test, delegates to all of the above.
- `expectedResponseSchemasCoverEveryControllerRoute` / `expectedRequestSchemasCoverEveryRequestBodyHandler`
  — self-maintenance guards, mirrored exactly.
- `everyMonetaryFieldIsADecimalStringNeverANumber` (Finding #6, new) — walks
  `components.schemas.*.properties` for any property named `expectedAmount`; asserts `type: string`.
  (This test's event-schema counterpart lives in the same class or a small shared helper — see below.)

## 5 event-payload contract test classes — planned method list (each mirrors `UserLifecycleEventPayloadContractTest`)

- `SeenPayloadContractTest.serializedPayloadMatchesTheDocumentedSchema` — builds a real `SeenPayload`,
  asserts required-present/no-undeclared-fields against `tx-seen.v1.schema.json`.
- `ConfirmedPayloadContractTest.serializedPayloadMatchesTheDocumentedSchema` — same, `tx-confirmed`.
- `FinalizedPayloadContractTest.serializedPayloadMatchesTheDocumentedSchema` — same, `tx-finalized`,
  with a real non-null `amount`/`fromAddress`/`toAddress` instance (proving the schema's optional fields
  are correctly named, not just the required ones).
- `ReorgedPayloadContractTest.serializedPayloadMatchesTheDocumentedSchema` — same, `tx-reorged`.
- `ProviderDegradedPayloadContractTest.serializedPayloadMatchesTheDocumentedSchema` — same,
  `provider-degraded`.
- `ProviderDegradedPayloadContractTest.everyDegradationReasonValueIsCoveredByTheSchemaEnum` (AC5) —
  mirrors `everyAccountStatusValueIsCoveredByTheSchemaEnum`'s identical technique, asserting all 3
  `DegradationReason.values()` are in the schema's `reason` enum.
- **Money-discipline test placement (Finding #6) — decided here:** a single, standalone
  `contracts/MoneyFieldsAreDecimalStringsContractTest.java` (new, in `com.themistra.crypto` root test
  package, no natural single owner among the 6 payload/DTO classes) walks all 6 contract files (the
  OpenAPI YAML's `components.schemas` plus the 5 event JSON schemas) and asserts every property named
  `expectedAmount` or `amount` declares `type: string`. Centralizing this in one file (rather than
  duplicating a per-file check across `CryptoInternalOpenApiContractTest` and each event test) avoids
  6 near-identical assertions scattered across 6 files for what is fundamentally one cross-cutting rule.

## Entities / repositories / services used

None — this task touches no runtime code.

## Execution order

1. **`pom.xml`** dependency addition (needed before any test in this task can compile).
2. **`contracts/api/crypto-internal.yaml`** (no dependency on the event schemas).
3. **5 event JSON schemas**, `tx-finalized` first (a pure copy, zero design risk), then `tx-seen`/
   `tx-confirmed` (identical to each other), then `tx-reorged`, then `provider-degraded` (the most
   structurally different, benefits from the other 4 being settled first).
4. **`CryptoInternalOpenApiContractTest`** (depends on the YAML being finished).
5. **5 event-payload contract tests**, same order as their schemas.
6. **`MoneyFieldsAreDecimalStringsContractTest`** last (depends on all 6 contract files existing).

## Open Questions

No blockers (unchanged from the frozen brief). One planning-level refinement made explicit above: the
`AttestResponse.outcome` enum-drift guard checks the DTO's actual 2-value range, not the full 3-value
`AttestOutcome` enum, since `REFUSED` is structurally never serialized into this record.
