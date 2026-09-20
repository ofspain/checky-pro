# crypto · T23 · Phase 0 — Repository Understanding

## 1. Architecture summary

`services/crypto` is the Spring Boot 3.5.4 / Java 21 module implementing Themistra's crypto-payment
verification and attestation platform. T15-T22 built the full internal API surface
(`POST/DELETE /internal/v1/watches`, `POST /internal/v1/attest`,
`GET /.well-known/themistra-verification-keys`) and the full event-publishing surface (5 emitted
events: `chain.tx.seen`, `chain.tx.confirmed`, `chain.tx.finalized`, `chain.tx.reorged`,
`chain.provider.degraded`), all hand-written with **no formal contract anywhere in the repo**. This
task's job is purely retrospective: author the OpenAPI spec and 5 JSON Schemas that describe what
already exists and is already shipped, then add contract tests proving the real code matches those
documents — no production code changes are expected.

`services/auth` already solved this exact problem for its own domain (T33-era work): a hand-written
OpenAPI YAML (`contracts/api/auth.yaml`) plus 3 event JSON Schemas
(`contracts/events/auth/*.v1.schema.json`), each backed by a plain-Jackson structural contract test
(no JSON-Schema-validation library is used anywhere in this repo) — `AuthOpenApiContractTest` and
`UserLifecycleEventPayloadContractTest` are the two direct, load-bearing precedents this task must
mirror, named explicitly by this task's own statement.

## 2. Existing code this task touches

**Internal API DTOs (read-only, source of truth for the OpenAPI spec):**
- `watch/dto/RegisterWatchRequest.java` — `{invoiceUuid: UUID, chain: String (ETHEREUM|TRON), address:
  String, tokenContractAddress: String, expectedAmount: String (decimal), expiresAt: Instant}`. Own
  Javadoc already disclaims: "not generated from `contracts/api/crypto-internal.yaml` - that file does
  not exist anywhere in this repository yet."
- `watch/dto/RegisterWatchResponse.java` — `{watchId: UUID, status: String}`.
- `attest/AttestRequest.java` — `{receiptDigestSha256: String (64 hex chars),
  chain: String (ETHEREUM|TRON), txHash: String}`. Same disclaimer.
- `attest/AttestResponse.java` — one record covering both `200` shapes (`@JsonInclude(NON_NULL)`):
  `{signature, kmsKeyId, signedAt, outcome, reason}`. `REFUSED` never produces this record — it's a
  `409 problem+json` via `AttestationRefusedException`, never a `200`.
- `attest/PublicKeyInfo.java` / `VerificationKeysResponse.java` — `{kid, kmsKeyId, alg, publicKeyPem}`
  wrapped in `{keys: [...]}`.
- `watch/WatchController.java`, `attest/AttestController.java`, `attest/VerificationKeysController.java`
  — exact HTTP methods/paths/status codes: `POST /internal/v1/watches` → `200`;
  `DELETE /internal/v1/watches/{watchId}` → `204`; `POST /internal/v1/attest` → `200`;
  `GET /.well-known/themistra-verification-keys` → `200` (public, `Cache-Control: no-store`).

**Event payload records (read-only, source of truth for the 5 JSON Schemas):**
- `watch/TxLifecyclePublisher.java` — `SeenPayload`, `ConfirmedPayload` (both:
  `idempotencyKey, watchId, invoiceUuid, chain, txHash, tokenContractAddress, confirmations (int),
  occurredAt`), and `FinalizedPayload` (`idempotencyKey, watchId, invoiceUuid, chain, txHash,
  tokenContractAddress, amount (String, BigDecimal.toPlainString()), fromAddress, toAddress,
  occurredAt` — **no `confirmations` field at all**, a deliberate, already-documented design decision:
  "no durable source by finality time").
- `reorg/ReorgDetector.java` — `ReorgedPayload` (`idempotencyKey, watchId, invoiceUuid, chain, txHash,
  tokenContractAddress, occurredAt` — no amount field).
- `provider/ProviderDegradedPublisher.java` — `Payload` (`chain, provider, reason (DegradationReason
  enum), occurredAt` — no `idempotencyKey`/`watchId`/`invoiceUuid` field inside the payload body at all;
  the idempotency key is passed separately to `OutboxPublisher.publish(...)`, not embedded). This
  class's own Javadoc already flags itself: "a concrete implementation for task 23 (Contracts) to later
  formalize into `contracts/events/chain/provider-degraded.v1.schema.json`, not itself a contract file."
- `events/EventTopics.java` — the exact aggregate-type-to-topic mapping already shipped:
  `tx-seen→chain.tx.seen`, `tx-confirmed→chain.tx.confirmed`, `tx-finalized→chain.tx.finalized`,
  `tx-reorged→chain.tx.reorged`, `provider→chain.provider.degraded`. Confirmed to exactly match
  `design.md`'s own VERBATIM `EventTopics` block — no drift to reconcile.

**Contracts directory — confirmed still entirely absent for crypto:**
- No `contracts/api/crypto-internal.yaml` exists anywhere in the repo.
- No `contracts/events/chain/` directory exists at all (only `contracts/events/auth/` exists, with 3
  files: `user-lifecycle.v1.schema.json`, `email-requested.v1.schema.json`, `security-audit.v1.schema.json`).
- `contracts/README.md` already describes the intended `events/`/`api/` layout and states models are
  meant to be *generated* from this directory in CI — in practice, `services/auth`'s own DTOs are all
  hand-written with disclaimers (matching what T23 will also need to disclaim for crypto).

## 3. Established patterns to follow

- **OpenAPI contract test** (`services/auth/.../AuthOpenApiContractTest.java`, 486 lines) — the direct
  precedent for this task's own named test, `shouldConformToCryptoInternalOpenApiContract`. Parses the
  YAML with `jackson-dataformat-yaml`'s `YAMLMapper`; reflects over an explicit list of controller
  classes to enumerate real `(HTTP method, path)` routes; asserts every real route is documented and
  every documented route has a real handler; builds one real DTO instance per `components.schemas`
  entry and asserts (via plain Jackson serialization) that every schema-required field is present and
  no undeclared field leaks through; explicit expectation tables map each route to its expected
  request/response `$ref`, with self-maintenance guard tests ensuring those tables themselves don't go
  stale. Scoped to success responses only, matching R28's own literal "responses... SHALL conform"
  wording (error responses/`4xx`/`409` are out of scope, mirroring auth's own R47 precedent).
- **Event payload contract test** (`services/auth/.../UserLifecycleEventPayloadContractTest.java`, 76
  lines) — the direct precedent for the 5 event-schema tests. No JSON-Schema-validation library is used
  anywhere in this repo (a deliberate choice, documented in that class's own Javadoc: "a structural
  check... via plain Jackson, rather than adding a JSON-Schema-validation library for the sake of a
  single contract file"). The test loads the schema JSON as a plain `JsonNode` tree, builds a real
  payload instance, serializes it, and asserts (a) every field the schema's own `required` array names
  is present in the serialized JSON, (b) every field actually serialized is declared in the schema's
  `properties` (enforcing `additionalProperties: false` without a real validator), and (c) any enum-typed
  field's real Java `values()` are all covered by the schema's own `enum` array (an enum-drift guard).
- **No codegen plugin anywhere in this repo** — confirmed via a full-repo search for
  `openapi-generator`; DTOs stay hand-written, contracts are the checked, not the generated, artifact.
- **Money/decimal discipline** (`agents.md`, already honored by every existing payload): amounts are
  always decimal strings on the wire, never JSON numbers — `FinalizedPayload.amount` and
  `RegisterWatchRequest.expectedAmount`/`AttestRequest`'s digest are the concrete precedents to describe
  faithfully in the new schemas/spec, not to re-decide.
- **Contract file naming**: `design.md` §6 gives the exact, authoritative filename list:
  `tx-seen.v1.schema.json`, `tx-confirmed.v1.schema.json`, `tx-finalized.v1.schema.json`,
  `tx-reorged.v1.schema.json`, `provider-degraded.v1.schema.json` — hyphenated, matching the one
  filename `design.md` §4c already gives verbatim for `tx-finalized`.

## 4. Testing conventions

- Contract tests are plain JUnit, no Testcontainers, no real infrastructure — pure static-artifact
  verification (parse YAML/JSON, reflect over real classes, compare).
- `services/crypto/pom.xml` currently has **no** `jackson-dataformat-yaml` dependency (confirmed via
  direct search) — this task will need to add it, test-scoped, exactly mirroring
  `services/auth/pom.xml`'s own addition for its identical `AuthOpenApiContractTest` need.
- No JSON-Schema-validation library (e.g. `networknt`/`everit`) exists or is expected — the established,
  deliberate pattern is plain-Jackson structural checks, not real schema validation.

## 5. Known gaps / unknowns

- **`design.md` §4c gives a full, verbatim JSON Schema example for `chain.tx.finalized` only** — the
  other four events (`seen`, `confirmed`, `reorged`, `provider.degraded`) are described only in prose
  ("share this envelope... author them under `contracts/events/chain/` with `watchId` as the partition
  key, `confirmations` on `seen`/`confirmed`, and no monetary JSON numbers"). This task must extrapolate
  the other 4 schemas from the `tx-finalized` envelope shape plus each event's own real payload record's
  field list (already gathered in §2 above) — not a hard blocker, since the real payload records are the
  actual source of truth regardless, but a genuine gap in verbatim spec text worth flagging rather than
  silently assuming.
- **`ProviderDegradedPublisher.Payload` has no `idempotencyKey`/`watchId`/`invoiceUuid` field at all**,
  unlike the four `chain.tx.*` events — a structurally different envelope shape for this one event. I
  do not know whether the intended `provider-degraded.v1.schema.json` should therefore look meaningfully
  different from the other four's shared envelope, or whether the schema should still declare those
  fields as absent/not-applicable explicitly. A Phase 2 design decision.
- **`AttestResponse`'s `REFUSED` outcome is never actually serialized** — a `409 problem+json` response
  is produced by a different path (`AttestationRefusedException`/`ApiExceptionHandler`), not this
  record. Whether the OpenAPI spec should document the `409` response shape at all, given R28's own
  "success responses only" scoping precedent from auth's own R47, is a Phase 2 decision — not yet
  resolved.
- **Whether the OpenAPI spec documents the `internal.crypto:write` scope requirement as a security
  scheme** (mirroring `auth.yaml`'s own `security: [{bearerAuth: [ROLE]}]` convention) is a Phase 2
  design choice; no blocker, since `auth.yaml`'s own precedent already shows the shape to mirror.
- **R28's own wording says "internal responses and emitted events SHALL conform"** — I do not know
  whether this task is expected to validate outbound *requests* too (e.g. `RegisterWatchRequest`'s own
  shape), or only responses/events as R28's literal text says. Auth's own precedent scoped strictly to
  responses; the same scoping is the more defensible, literal reading here too, but this is a Phase 2
  proposal, not a settled fact.
