<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge). Sonnet is the default working model; escalate to Opus/Fable ONLY when a phase needs architectural reasoning beyond the frozen brief. Kimi 2.7 runs the adversarial review phases (3, 8, 11). Human Approval gates (4, 9) are decisions, not model runs. -->

# crypto · T23 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `crypto-service` |
| **Task** | T23 — Contracts |
| **Spec section** | Contracts, sidecar contract, hardening |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

**Task statement (verbatim from `spec/crypto-service/tasks.md`, task 23):**
> **Contracts.** Author `contracts/api/crypto-internal.yaml` and the five `contracts/events/chain/*.v1.schema.json` (amounts as decimal strings). Add contract tests mirroring the auth `UserLifecycleEventPayloadContractTest` pattern (R28).

**Spec package:** `spec/crypto-service/` → `package.md` · `requirements.md` · `design.md` · `tasks.md` · `agents.md`

---

## Findings

### 1. Brief says "3 routes" but lists 4 routes

- **Issue:** The Scope section opens with "3 routes:" and then enumerates `POST /internal/v1/watches`, `DELETE /internal/v1/watches/{watchId}`, `POST /internal/v1/attest`, and `GET /.well-known/themistra-verification-keys` — four routes. This is a simple inconsistency that could cause a brief reader to wonder whether one route is intentionally excluded.
- **Severity:** Low
- **Evidence:** `02-task-implementation-brief.md` line 20 vs. lines 21-28.
- **Recommended brief amendment:** Change "3 routes:" to "4 routes:".

### 2. `DELETE /internal/v1/watches/{watchId}` path parameter is not explicitly documented in the brief

- **Issue:** The brief mentions the route and its `204` response, but it does not explicitly require the OpenAPI spec to declare the `{watchId}` path parameter. Auth's `auth.yaml` precedent documents path parameters through the path string plus explicit `parameters` entries (e.g., `{accountUuid}`). Omitting the parameter from the brief risks an under-specified contract.
- **Severity:** Low
- **Evidence:** `02-task-implementation-brief.md` lines 22-23; contrast with `contracts/api/auth.yaml` path-parameter declarations for `/admin/accounts/{accountUuid}`.
- **Recommended brief amendment:** Add a bullet under the DELETE route: "path parameter `watchId` (`type: string`, `format: uuid`)". Add the parameter to the acceptance criteria or Required Tests (e.g., `CryptoInternalOpenApiContractTest` should verify the documented operation declares the parameter).

### 3. `provider-degraded`'s idempotency key format creates an unresolved tension with L5

- **Issue:** `design.md` §4a-L5 (and `package.md` §8/R12) states: "Every emitted event carries the deterministic idempotency key `chain:txhash:eventtype`." The brief resolves this for `provider-degraded` by noting the key is passed to `OutboxPublisher` separately and is absent from the payload body. However, the payload body is not the only place an idempotency key can be documented; a consumer reading `provider-degraded.v1.schema.json` will see no `idempotencyKey` field and may conclude the event violates L5. The actual key format used by `ProviderDegradedPublisher` is `{chain}:{provider}:degraded:{occurredAt}:{UUID}`, which is neither `chain:txhash:eventtype` nor documented anywhere in the brief.
- **Severity:** Medium
- **Evidence:** `02-task-implementation-brief.md` lines 46-51 (brief's resolution) vs. `design.md` §4a-L5 / `package.md` §8; `services/crypto/src/main/java/com/themistra/crypto/provider/ProviderDegradedPublisher.java:15-19` and `:59` (actual key format).
- **Recommended brief amendment:** Either (a) explicitly state that L5 applies only to `chain.tx.*` events and `provider-degraded` is a documented exception, or (b) document the actual idempotency-key format for `provider-degraded` in the schema's `description`/meta or in a separate `contracts/events/chain/README.md`. Do not leave the consumer to infer the exception.

### 4. `AttestResponse` contract test should verify both `SIGNED` and `BLOCKED` serializations

- **Issue:** The brief documents `AttestResponse` as one schema with optional fields, matching `@JsonInclude(NON_NULL)`. A contract test that only serializes the `SIGNED` variant would pass even if the schema incorrectly marked `reason` as required (because `SIGNED` has no `reason`), and a test that only serializes `BLOCKED` would pass even if `signature`/`kmsKeyId`/`signedAt` were incorrectly required. The structural-comparison technique from the auth precedent checks that serialized fields are declared and required fields are present, but it does not prove that optional fields are truly optional.
- **Severity:** Medium
- **Evidence:** `02-task-implementation-brief.md` lines 24-26; `services/crypto/src/main/java/com/themistra/crypto/attest/AttestResponse.java:22-28`.
- **Recommended brief amendment:** In Required Tests for `CryptoInternalOpenApiContractTest`, add: "`AttestResponse` is serialized in both `SIGNED` and `BLOCKED` variants against `components.schemas.AttestResponse`; every field present in only one variant is not listed in `required`."

### 5. No enum-drift guard for `AttestResponse.outcome`

- **Issue:** `AttestResponse.outcome` is serialized as the literal strings `"SIGNED"` and `"BLOCKED"`. The OpenAPI schema should document these as an enum. If a third outcome is added to `AttestOutcome` in the future, the schema will silently drift unless a test enforces coverage. The brief already mandates an enum-drift guard for `DegradationReason` in `provider-degraded`; the same reasoning applies here.
- **Severity:** Low
- **Evidence:** `02-task-implementation-brief.md` lines 24-26 and AC5 (enum-drift guard for `DegradationReason` only); `services/crypto/src/main/java/com/themistra/crypto/attest/AttestResponse.java:19-20`.
- **Recommended brief amendment:** Add to AC5 (or as a new AC): "`AttestOutcome.values()` are all covered by `crypto-internal.yaml`'s `AttestResponse.outcome` enum, proven by a dedicated test." Add the corresponding method to `CryptoInternalOpenApiContractTest`.

### 6. AC4 (money discipline) has no executable enforcement

- **Issue:** AC4 states that no monetary field is `type: number`. The structural contract tests (both OpenAPI and JSON Schema) check required-field presence and absence of undeclared fields, but they do not inspect `type` declarations. A schema author could accidentally write `type: number` for `expectedAmount` or `amount`, and the existing tests would still pass.
- **Severity:** Medium
- **Evidence:** `02-task-implementation-brief.md` lines 31-32 and AC4; the auth precedent's `everyComponentSchemaMatchesItsRealDtoShape()` only checks field names, not types.
- **Recommended brief amendment:** Add a Required Test: "A dedicated test walks `crypto-internal.yaml` and all five event schemas and asserts that every property whose name matches the monetary field list (`expectedAmount`, `amount`) has `type: string` and never `type: number` or `type: integer`."

---

## Confirmed safe/resolved

- **No production code change:** The brief correctly scopes T23 as retrospective contract/documentation work only.
- **Auth precedent alignment:** Mirroring `AuthOpenApiContractTest` and `UserLifecycleEventPayloadContractTest` is the right pattern for this repo.
- **409 exclusion:** Matches auth's R47 precedent (success responses only) and is consistent with `ApiExceptionHandler` being a cross-cutting concern.
- **`tx-finalized` verbatim fidelity:** Copying from `design.md` §4c prevents re-derivation drift.
- **`provider-degraded` different envelope:** Accurately reflects `ProviderDegradedPublisher.Payload`'s actual field list rather than forcing it into the tx-event shape.
- **`jackson-dataformat-yaml` test-scope dependency:** Correctly scoped and matches auth's POM.
