# crypto · T23 · Phase 1 — Specification Extraction

## Business Rules

- **R28.** Where `contracts/api/crypto-internal.yaml` and `contracts/events/chain/*` are authored,
  internal responses and emitted events conform to them.

## Locked Decisions

- **L5.** Every emitted event carries the deterministic idempotency key `chain:txhash:eventtype` — the
  schemas this task authors must document `idempotencyKey` in exactly this format (already honored by
  every real payload record found in Phase 0, `ProviderDegradedPublisher.Payload` excepted, which passes
  its idempotency key to `OutboxPublisher` separately rather than embedding it in the payload body — a
  genuine, disclosed structural difference this task must describe accurately, not paper over).
- **L11 (unmodified, relevant only for accurate description).** Receipts embed the key id; verification
  public keys are published at a well-known URL — the OpenAPI spec's own `/.well-known/...` and
  `/attest` documentation must describe this shape faithfully, already fully implemented (T20-T22).

## Files involved

**Existing, read-only source of truth (from Phase 0):**
- `watch/dto/RegisterWatchRequest.java`/`RegisterWatchResponse.java`, `attest/AttestRequest.java`/
  `AttestResponse.java`, `attest/PublicKeyInfo.java`/`VerificationKeysResponse.java` — the exact DTO
  shapes the OpenAPI spec's `components.schemas` must match field-for-field.
- `watch/WatchController.java`, `attest/AttestController.java`,
  `attest/VerificationKeysController.java` — the exact routes/methods/status codes the OpenAPI spec's
  `paths` must match.
- `watch/TxLifecyclePublisher.java` (`SeenPayload`, `ConfirmedPayload`, `FinalizedPayload`),
  `reorg/ReorgDetector.java` (`ReorgedPayload`), `provider/ProviderDegradedPublisher.java` (`Payload`) —
  the exact field lists the 5 JSON Schemas must match.
- `events/EventTopics.java` — the aggregate-type-to-topic mapping, already verified to match
  `design.md`'s own VERBATIM block exactly.
- `design.md` §4c's `chain.tx.finalized` schema (the only full verbatim JSON Schema example given).

**Precedent, to mirror the shape/style of (not to modify):**
- `services/auth/src/test/java/com/themistra/auth/common/AuthOpenApiContractTest.java` (486 lines) —
  the direct precedent for this task's own named test.
- `services/auth/src/test/java/com/themistra/auth/account/event/UserLifecycleEventPayloadContractTest.java`
  (76 lines) — the direct precedent this task's own task statement names explicitly.
- `contracts/api/auth.yaml` — the OpenAPI authoring style to mirror (structure, security-scheme
  convention, `components.schemas` shape).
- `contracts/events/auth/user-lifecycle.v1.schema.json` — the JSON Schema authoring style to mirror.

**New, this task's own deliverables:**
- `contracts/api/crypto-internal.yaml`.
- `contracts/events/chain/tx-seen.v1.schema.json`, `tx-confirmed.v1.schema.json`,
  `tx-finalized.v1.schema.json`, `tx-reorged.v1.schema.json`, `provider-degraded.v1.schema.json` (the
  exact filenames `design.md` §6 already names).
- A crypto-service equivalent of `AuthOpenApiContractTest` (exact class name to be proposed at Phase 2).
- A crypto-service equivalent of `UserLifecycleEventPayloadContractTest`, likely one per event or one
  parameterized test class (exact shape a Phase 2 design decision).

## Dependencies

`com.fasterxml.jackson.dataformat:jackson-dataformat-yaml` (test-scope; confirmed absent from
`services/crypto/pom.xml`, present in `services/auth/pom.xml` for the identical need — this task will
need to add it, mirroring auth's own addition). No JSON-Schema-validation library (confirmed: none
exists anywhere in this repo; the established, deliberate pattern is plain-Jackson structural
verification, not real schema validation).

## Acceptance Criteria

1. **AC1 (R28, named test).** `shouldConformToCryptoInternalOpenApiContract` passes: every real
   controller route (`/internal/v1/watches` POST/DELETE, `/internal/v1/attest` POST,
   `/.well-known/themistra-verification-keys` GET) is documented in `crypto-internal.yaml`, and no
   documented route lacks a real handler.
2. **AC2 (R28, schema fidelity).** Every `components.schemas` entry in `crypto-internal.yaml` matches
   its real DTO's actual serialized field set exactly (required fields present, no undeclared fields).
3. **AC3 (R28, event contract fidelity).** For each of the 5 events, a contract test proves the real
   payload record's serialized JSON satisfies its own schema's `required`/`properties` declaration
   exactly (including `ProviderDegradedPublisher.Payload`'s genuinely different, non-tx envelope shape).
4. **AC4 (L5).** Every one of the 5 schemas documents `idempotencyKey` (or, for
   `provider-degraded`, documents its absence from the payload body accurately, since that event's key
   is passed to the outbox separately) in the `chain:txhash:eventtype` format.
5. **AC5 (money discipline, `agents.md`).** Every schema/spec field carrying a monetary value
   (`expectedAmount`, `FinalizedPayload.amount`) is documented as a decimal string, never `type: number`.
6. **AC6 (enum-drift guard, mirrors auth's own established pattern).** Any enum-typed field
   (`DegradationReason` on `provider-degraded`) has a contract test asserting every real Java enum value
   is covered by the schema's own declared `enum` array.

## Tests required

- **Named test (`package.md` §8):** `shouldConformToCryptoInternalOpenApiContract` → R28.
- **5 event-payload contract tests** (one per event, or parameterized), mirroring
  `UserLifecycleEventPayloadContractTest`'s exact structural-check technique.
- **The auth precedent's own two self-maintenance guard tests** (`AuthOpenApiContractTest`'s
  "expectation tables cover every real route" tests) — likely needed here too if this task adopts the
  same expectation-table technique, a Phase 2 design decision.

## Open Questions

Three genuine, disclosed gaps from Phase 0, requiring explicit Phase 2 design proposals rather than
blocking this task outright:

- **`design.md` §4c gives a full verbatim JSON Schema only for `chain.tx.finalized`** — the other 4 must
  be extrapolated from the shared-envelope prose guidance plus each event's own real payload record
  (already gathered). Not a hard blocker — the real payload records are the actual source of truth
  regardless of whether `design.md` spells out every schema verbatim.
- **`ProviderDegradedPublisher.Payload`'s structurally different envelope** (no
  `idempotencyKey`/`watchId`/`invoiceUuid` field in the payload body itself) needs an explicit Phase 2
  design decision on how `provider-degraded.v1.schema.json` should be shaped relative to the other 4's
  shared envelope — not a blocker, since the real record's field list is already known.
- **Whether R28's "internal responses and emitted events" wording extends to validating outbound
  *requests* too** (vs. auth's own precedent, which scopes `AuthOpenApiContractTest` to success
  responses only) — a Phase 2 proposal, informed by the more defensible literal reading of R28's own
  text (responses/events only), not a hard blocker.
