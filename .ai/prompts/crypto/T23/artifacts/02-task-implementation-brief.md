# crypto · T23 · Phase 2 — Task Implementation Brief

## Task

Author `contracts/api/crypto-internal.yaml` and the five `contracts/events/chain/*.v1.schema.json`
files describing crypto-service's already-shipped internal API and emitted events, plus contract tests
proving the real code conforms — mirroring `services/auth`'s own `AuthOpenApiContractTest`/
`UserLifecycleEventPayloadContractTest` precedents exactly.

## Purpose

Close the "contract does not exist yet" gap every prior task in this package has disclosed since T11.
Purely retrospective/documentation work — no production code changes.

## Scope

**In:**
- `contracts/api/crypto-internal.yaml` (OpenAPI 3.0.3, mirrors `auth.yaml`'s exact structural
  conventions: `info`/`servers`/`paths`/`components.schemas`/`components.securitySchemes`):
  - 3 routes: `POST /internal/v1/watches` (`security: [{bearerAuth: [internal.crypto:write]}]`,
    request `RegisterWatchRequest`, `200` response `RegisterWatchResponse`),
    `DELETE /internal/v1/watches/{watchId}` (same security, `204` no body),
    `POST /internal/v1/attest` (same security, request `AttestRequest`, `200` response
    `AttestResponse` — documents the shared `SIGNED`/`BLOCKED` shape as one schema with optional
    fields, matching the real `@JsonInclude(NON_NULL)` record exactly; the `409` error path is
    **out of scope**, mirroring auth's own R47-precedent scoping to success responses only),
    `GET /.well-known/themistra-verification-keys` (`security: []`, public, `200` response
    `VerificationKeysResponse`).
  - `components.schemas`: `RegisterWatchRequest`, `RegisterWatchResponse`, `AttestRequest`,
    `AttestResponse`, `PublicKeyInfo`, `VerificationKeysResponse` — each field list copied exactly from
    the real record (Phase 0), monetary fields (`expectedAmount`) documented `type: string`, never
    `type: number`.
  - `components.securitySchemes.bearerAuth`: `{type: http, scheme: bearer, bearerFormat: JWT}`,
    mirroring `auth.yaml`'s identical scheme (the actual JWT/scope mechanics are unchanged, already
    implemented by `ResourceServerConfig`, T03).
- `contracts/events/chain/tx-finalized.v1.schema.json` — copied **verbatim, byte-for-byte** from
  `design.md` §4c's own already-given JSON text (not re-derived) — the one schema the spec itself
  already fully specifies.
- `contracts/events/chain/tx-seen.v1.schema.json`, `tx-confirmed.v1.schema.json` — near-identical
  envelope to `tx-finalized`'s, minus `amount`/`fromAddress`/`toAddress`/`addressPoisoningFlag`
  (`SeenPayload`/`ConfirmedPayload` carry none of these), **plus `confirmations` as a required field**
  (both records declare it as a primitive `int`, always present) — matches `design.md`'s own prose
  guidance ("`confirmations` on `seen`/`confirmed`") exactly.
- `contracts/events/chain/tx-reorged.v1.schema.json` — same shared envelope as `tx-seen`/`tx-confirmed`
  minus `confirmations` (`ReorgedPayload` has none).
- `contracts/events/chain/provider-degraded.v1.schema.json` — **a genuinely different envelope**
  (Phase 0/1's own disclosed gap, resolved here): no `idempotencyKey`/`watchId`/`invoiceUuid`/`txHash`/
  `tokenContractAddress` field at all, since `ProviderDegradedPublisher.Payload` has none of them (the
  idempotency key is passed to `OutboxPublisher` separately, never embedded in this payload). Required:
  `chain`, `provider`, `reason`, `occurredAt`. `reason` documented as a `string` `enum` listing every
  real `DegradationReason` value (AC6's own enum-drift-guard target).
- **Crypto-internal contract test** — `common.CryptoInternalOpenApiContractTest`, mirroring
  `AuthOpenApiContractTest`'s exact technique: a `Route` record, `CONTROLLERS = {WatchController,
  AttestController, VerificationKeysController}`, reflection-based route enumeration, explicit
  `expectedResponseSchemas()`/`expectedRequestSchemas()` tables with their own self-maintenance guard
  tests, `realInstancesByComponentName()` building one real instance per schema, and one aggregate
  `shouldConformToCryptoInternalOpenApiContract()` test matching the spec's own named test exactly.
- **5 event-payload contract tests**, one class per payload record (proportionate to 5 genuinely
  different envelope shapes — not one contrived parameterized abstraction across them), each mirroring
  `UserLifecycleEventPayloadContractTest`'s exact technique, placed in the same package as the payload's
  own publisher class (mirrors auth's own same-package convention): `watch.SeenPayloadContractTest`,
  `watch.ConfirmedPayloadContractTest`, `watch.FinalizedPayloadContractTest`,
  `reorg.ReorgedPayloadContractTest`, `provider.ProviderDegradedPayloadContractTest` (the last one
  additionally carries `DegradationReason`'s own enum-drift-guard test, mirroring
  `everyAccountStatusValueIsCoveredByTheSchemaEnum`'s identical technique).
- `services/crypto/pom.xml` — add `com.fasterxml.jackson.dataformat:jackson-dataformat-yaml`
  (test-scope, no explicit version, inherited from Spring Boot's BOM), mirroring
  `services/auth/pom.xml`'s own identical addition for the identical need.

**Out:**
- Any production code change — every DTO/controller/payload this task describes is already shipped
  (T15-T22); this task documents, never modifies, them.
- Documenting the `409` error response shape for `/attest` — matches auth's own R47-precedent scoping
  (success responses only); `problem+json` error bodies are a cross-cutting, already-standardized shape
  (`common.ApiExceptionHandler`) not specific to this endpoint.
- Validating outbound *requests* against the schema (only responses/events, per R28's own literal
  wording and auth's own established precedent) — though `components.schemas` still documents request
  shapes for human/codegen value, exactly as auth's own `auth.yaml` does for its own request DTOs; only
  the *test* enforcement is response/event-scoped.
- A JSON-Schema-validation library — the established, deliberate repo-wide pattern (confirmed: none
  exists anywhere) is plain-Jackson structural verification, not real schema validation; introducing one
  now for a single task would be inconsistent, unrequested scope growth.
- An OpenAPI/JSON-Schema codegen plugin — confirmed absent repo-wide; DTOs stay hand-written.

## Business Rules

- **R28.** Internal responses and emitted events conform to `contracts/api/crypto-internal.yaml` and
  `contracts/events/chain/*`.

## Locked Decisions

- **L5.** Every emitted event carries `chain:txhash:eventtype` — all 5 schemas document
  `idempotencyKey` in this exact format (or, for `provider-degraded`, its deliberate absence from the
  payload body — see Scope).

## Dependencies

`com.fasterxml.jackson.dataformat:jackson-dataformat-yaml` (new, test-scope). No other new dependency.

## Inputs

None (contract files and tests only).

## Outputs

6 new contract files (`contracts/api/crypto-internal.yaml` + 5 event schemas); 6 new test classes; 1
`pom.xml` dependency addition.

## State Changes

None.

## Files to Create

- `contracts/api/crypto-internal.yaml`
- `contracts/events/chain/tx-seen.v1.schema.json`
- `contracts/events/chain/tx-confirmed.v1.schema.json`
- `contracts/events/chain/tx-finalized.v1.schema.json`
- `contracts/events/chain/tx-reorged.v1.schema.json`
- `contracts/events/chain/provider-degraded.v1.schema.json`
- `services/crypto/src/test/java/com/themistra/crypto/common/CryptoInternalOpenApiContractTest.java`
- `services/crypto/src/test/java/com/themistra/crypto/watch/SeenPayloadContractTest.java`
- `services/crypto/src/test/java/com/themistra/crypto/watch/ConfirmedPayloadContractTest.java`
- `services/crypto/src/test/java/com/themistra/crypto/watch/FinalizedPayloadContractTest.java`
- `services/crypto/src/test/java/com/themistra/crypto/reorg/ReorgedPayloadContractTest.java`
- `services/crypto/src/test/java/com/themistra/crypto/provider/ProviderDegradedPayloadContractTest.java`

## Files to Modify

- `services/crypto/pom.xml` — add `jackson-dataformat-yaml` (test-scope).

## Files NOT to Modify

- Every existing controller, DTO/record, and payload class named in Phase 0/this brief's Scope — this
  task documents them, never changes them.
- `contracts/api/auth.yaml`, `contracts/events/auth/*` (a different service's own contracts).
- Any file under `spec/`.

## Acceptance Criteria

- **AC1 (R28, named test).** `shouldConformToCryptoInternalOpenApiContract` passes: all 3 real routes
  documented, no orphan documented routes, every `components.schemas` entry matches its real DTO
  exactly, every operation's response/request `$ref` matches the expectation tables, and the
  expectation tables themselves cover every real route (self-maintenance guards).
- **AC2 (R28, event contract fidelity).** All 5 event-payload contract tests pass: each real payload
  record's serialized JSON satisfies its own schema's `required`/`properties` declaration exactly.
- **AC3 (L5).** All 5 schemas correctly document `idempotencyKey`'s presence (4 events) or its
  deliberate absence from the payload body (`provider-degraded`).
- **AC4 (money discipline).** No schema/spec field carrying a monetary value is ever `type: number`.
- **AC5 (enum-drift guard).** `DegradationReason.values()` are all covered by
  `provider-degraded.v1.schema.json`'s own `reason` enum, proven by a dedicated test.
- **AC6 (verbatim fidelity).** `tx-finalized.v1.schema.json`'s content is byte-for-byte identical to
  `design.md` §4c's own given JSON text.

## Required Tests

- `CryptoInternalOpenApiContractTest` — the named test plus its constituent purpose-named methods and
  self-maintenance guards, mirroring `AuthOpenApiContractTest`'s exact 9-method structure (7 conformance
  checks + 2 table-maintenance guards).
- 5 payload contract tests, each with (at minimum) a "serialized payload matches the documented schema"
  test; `ProviderDegradedPayloadContractTest` additionally carries the `DegradationReason` enum-drift
  guard (AC5).

## Constraints

- **Performance:** none — static-artifact parsing only, no runtime infrastructure.
- **Security:** none — no new endpoint, no new secret; the OpenAPI spec documents an already-enforced
  scope requirement, it does not create one.
- **Thread-safety:** not applicable (test-only, static analysis).
- **Transaction:** not applicable.
- **Module boundaries:** contract tests may reference each payload record's public type directly (all 5
  are already `public` nested records, confirmed in Phase 2 research) — no visibility change needed.
- **Null handling:** not applicable — this task builds no new runtime logic.

## Open Questions

No blockers. All three Phase 1 open questions are resolved above: the 4 non-`tx-finalized` schemas are
derived from each event's own real payload record field list (not invented); `provider-degraded`'s
genuinely different envelope is documented accurately rather than forced into the shared 4-event shape;
R28's scope is read literally (responses/events only, matching auth's own established precedent), not
extended to request-body validation.
