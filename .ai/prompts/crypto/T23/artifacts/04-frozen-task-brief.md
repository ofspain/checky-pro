STATUS: FROZEN

# crypto · T23 · Phase 4 — Frozen Task Brief

## Phase 3 findings — dispositions

All 6 findings accepted. Finding #3's factual claim (the real `ProviderDegradedPublisher` idempotency
key format) was independently verified directly against source before acceptance.

| # | Finding | Disposition | Resolution |
|---|---|---|---|
| 1 | Brief says "3 routes" but lists 4 | **ACCEPTED** | Wording fix: "4 routes". |
| 2 | `DELETE /internal/v1/watches/{watchId}`'s path parameter is not explicitly required to be declared | **ACCEPTED** | The OpenAPI spec's `DELETE` operation must declare the `watchId` path parameter (`type: string`, `format: uuid`), mirroring `auth.yaml`'s own `{accountUuid}` convention. `CryptoInternalOpenApiContractTest` gains an assertion that the parameter is present. |
| 3 | `provider-degraded`'s real idempotency-key format (`{chain}:{provider}:degraded:{occurredAt}:{UUID}`, verified directly against `ProviderDegradedPublisher.java:51`) is never surfaced, leaving consumers to wrongly infer an L5 violation | **ACCEPTED (verified)** | The schema's own `description` explicitly documents the real format and states L5's `chain:txhash:eventtype` format applies to the four `chain.tx.*` events only — `provider-degraded` is a documented, deliberate exception (a provider can degrade/recover/degrade again, so a fixed key would collide with itself; the class's own already-existing Javadoc gives this reasoning, now surfaced in the contract too). |
| 4 | The `AttestResponse` contract test needs both `SIGNED` and `BLOCKED` variants serialized, not just one | **ACCEPTED** | `CryptoInternalOpenApiContractTest`'s `realInstancesByComponentName()`-equivalent step for `AttestResponse` is exercised with two real instances (one `signed(...)`, one `blocked(...)`), each checked against the schema — proving fields present in only one variant are correctly `not` listed as `required`. |
| 5 | No enum-drift guard for `AttestResponse.outcome` | **ACCEPTED** | New test asserting `AttestOutcome.values()` are all covered by `crypto-internal.yaml`'s `AttestResponse.outcome` enum, mirroring the `DegradationReason` guard already required. |
| 6 | AC4 (money discipline) has no executable enforcement | **ACCEPTED** | New test walking all 6 contract files (the OpenAPI spec + 5 event schemas), asserting every property whose name is in a fixed monetary-field list (`expectedAmount`, `amount`) declares `type: string`, never `type: number`/`type: integer`. |

## Task

Author `contracts/api/crypto-internal.yaml` and the five `contracts/events/chain/*.v1.schema.json`
files describing crypto-service's already-shipped internal API and emitted events, plus contract tests
proving the real code conforms.

## Purpose

Close the "contract does not exist yet" gap every prior task in this package has disclosed since T11.
Purely retrospective/documentation work — no production code changes.

## Scope

**In:**
- `contracts/api/crypto-internal.yaml` (OpenAPI 3.0.3, mirrors `auth.yaml`'s structural conventions):
  - **4 routes** (Finding #1): `POST /internal/v1/watches` (`security: [{bearerAuth: [internal.crypto:write]}]`,
    request `RegisterWatchRequest`, `200` response `RegisterWatchResponse`);
    `DELETE /internal/v1/watches/{watchId}` (same security, explicit `watchId` path parameter — `type:
    string`, `format: uuid` — Finding #2, `204` no body); `POST /internal/v1/attest` (same security,
    request `AttestRequest`, `200` response `AttestResponse` documenting the shared `SIGNED`/`BLOCKED`
    shape with an `outcome` enum covering both values, Finding #5; `409` out of scope, mirrors auth's
    own R47 precedent); `GET /.well-known/themistra-verification-keys` (`security: []`, public, `200`
    response `VerificationKeysResponse`).
  - `components.schemas`: `RegisterWatchRequest`, `RegisterWatchResponse`, `AttestRequest`,
    `AttestResponse` (with `outcome` enum `[SIGNED, BLOCKED]`), `PublicKeyInfo`,
    `VerificationKeysResponse` — field lists copied exactly from the real records; `expectedAmount`
    documented `type: string`.
  - `components.securitySchemes.bearerAuth`: `{type: http, scheme: bearer, bearerFormat: JWT}`.
- `contracts/events/chain/tx-finalized.v1.schema.json` — copied verbatim, byte-for-byte, from
  `design.md` §4c.
- `contracts/events/chain/tx-seen.v1.schema.json`, `tx-confirmed.v1.schema.json` — shared envelope
  minus `amount`/`fromAddress`/`toAddress`/`addressPoisoningFlag`, plus required `confirmations`.
- `contracts/events/chain/tx-reorged.v1.schema.json` — same shared envelope minus `confirmations`.
- `contracts/events/chain/provider-degraded.v1.schema.json` — genuinely different envelope (no
  `idempotencyKey`/`watchId`/`invoiceUuid`/`txHash`/`tokenContractAddress`); required: `chain`,
  `provider`, `reason` (enum covering every `DegradationReason` value), `occurredAt`. `description`
  explicitly documents the real idempotency-key format and its L5 exception (Finding #3).
- `common.CryptoInternalOpenApiContractTest` — mirrors `AuthOpenApiContractTest`'s technique exactly:
  `Route` record, `CONTROLLERS = {WatchController, AttestController, VerificationKeysController}`,
  reflection-based route enumeration, explicit expectation tables with self-maintenance guards,
  `realInstancesByComponentName()` (two entries for `AttestResponse`, Finding #4), a path-parameter
  presence check (Finding #2), an `outcome` enum-drift guard (Finding #5), and one aggregate
  `shouldConformToCryptoInternalOpenApiContract()` test.
- 5 event-payload contract tests, one class per payload record: `watch.SeenPayloadContractTest`,
  `watch.ConfirmedPayloadContractTest`, `watch.FinalizedPayloadContractTest`,
  `reorg.ReorgedPayloadContractTest`, `provider.ProviderDegradedPayloadContractTest` (the last carries
  the `DegradationReason` enum-drift guard).
- A new, dedicated money-discipline test (Finding #6) walking all 6 contract files, asserting every
  property named in a fixed monetary-field list is `type: string`.
- `services/crypto/pom.xml` — add `jackson-dataformat-yaml` (test-scope), mirroring
  `services/auth/pom.xml`.

**Out:**
- Any production code change.
- Documenting the `409` error response shape.
- Validating outbound requests against the schema (test enforcement is response/event-scoped only;
  `components.schemas` still documents request shapes for human/codegen value).
- A JSON-Schema-validation library or an OpenAPI/JSON-Schema codegen plugin.

## Business Rules

- **R28.** Internal responses and emitted events conform to `contracts/api/crypto-internal.yaml` and
  `contracts/events/chain/*`.

## Locked Decisions

- **L5.** Every emitted event carries `chain:txhash:eventtype` — applies to the four `chain.tx.*`
  events; `provider-degraded` is a documented, deliberate exception with its own, different,
  already-implemented key format (Finding #3).

## Dependencies

`com.fasterxml.jackson.dataformat:jackson-dataformat-yaml` (new, test-scope). No other new dependency.

## Inputs / Outputs / State Changes

Unchanged from Phase 2: no inputs, 6 new contract files + 6 new test classes + 1 `pom.xml` dependency,
no state changes.

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

- Every existing controller, DTO/record, and payload class this task documents.
- `contracts/api/auth.yaml`, `contracts/events/auth/*`.
- Any file under `spec/`.

## Acceptance Criteria

- **AC1 (R28, named test).** `shouldConformToCryptoInternalOpenApiContract` passes: all 4 real routes
  documented (including the `watchId` path parameter, Finding #2), no orphan documented routes, every
  `components.schemas` entry matches its real DTO exactly (both `AttestResponse` variants, Finding #4),
  every operation's response/request `$ref` matches the expectation tables, expectation tables cover
  every real route.
- **AC2 (R28, event contract fidelity).** All 5 event-payload contract tests pass.
- **AC3 (L5).** All 5 schemas correctly document `idempotencyKey`'s presence (4 events) or its
  deliberate, explained absence (`provider-degraded`, Finding #3).
- **AC4 (money discipline), now executable (Finding #6).** A dedicated test walking all 6 contract files
  asserts every monetary-named property is `type: string`.
- **AC5 (enum-drift guards).** `DegradationReason.values()` covered by `provider-degraded`'s `reason`
  enum; `AttestOutcome.values()` covered by `AttestResponse.outcome`'s enum (Finding #5).
- **AC6 (verbatim fidelity).** `tx-finalized.v1.schema.json` byte-for-byte identical to `design.md`
  §4c's given JSON text.

## Required Tests

Unchanged in structure from Phase 2, expanded per the 6 accepted findings above:
`CryptoInternalOpenApiContractTest` (named test + constituent methods, now including the path-parameter
check, both `AttestResponse` variants, and the `outcome` enum-drift guard); 5 payload contract tests
(`ProviderDegradedPayloadContractTest` carrying its own enum-drift guard); the new money-discipline test
(Finding #6, likely its own small test class or folded into one of the above — a Phase 5 placement
decision).

## Constraints

Unchanged from Phase 2.

## Open Questions

No blockers.
