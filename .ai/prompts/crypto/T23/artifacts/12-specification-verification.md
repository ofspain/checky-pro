# crypto · T23 · Phase 12 — Specification Verification

## Traceability matrix

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| R28 — internal responses conform to `crypto-internal.yaml` | Yes | `contracts/api/crypto-internal.yaml` (all 4 routes, 6 schemas) | `CryptoInternalOpenApiContractTest.shouldConformToCryptoInternalOpenApiContract` (named test) | No | None. |
| R28 — emitted events conform to `contracts/events/chain/*` | Yes | All 5 `contracts/events/chain/*.v1.schema.json` | `SeenPayloadContractTest`, `ConfirmedPayloadContractTest`, `FinalizedPayloadContractTest`, `ReorgedPayloadContractTest`, `ProviderDegradedPayloadContractTest` | No | None. |
| L5 — `chain:txhash:eventtype` idempotency key, with `provider-degraded`'s documented exception | Yes | `provider-degraded.v1.schema.json`'s own `description` states the real `{chain}:{provider}:degraded:{occurredAt}:{UUID}` format and its rationale (verified against `ProviderDegradedPublisher.java:51` at Phase 3) | 4 tx-event tests assert `idempotencyKey` is in `required` (Phase 11 Gap 1); `ProviderDegradedPayloadContractTest` asserts `idempotencyKey` is absent from `properties` (Phase 11 Gap 1) | No | None. |
| AC1 — named test, all 4 routes + 6 schemas, path parameter, both `AttestResponse` variants, `$ref` tables | Yes | `crypto-internal.yaml` (all routes/schemas); `WatchController`/`AttestController`/`VerificationKeysController` reflected against it | `CryptoInternalOpenApiContractTest` (10 of its 14 methods; the other 4 are Phase 11 additions beyond AC1's literal scope) | No | None. |
| AC2 — all 5 event-payload contract tests pass | Yes | — | `SeenPayloadContractTest`, `ConfirmedPayloadContractTest`, `FinalizedPayloadContractTest`, `ReorgedPayloadContractTest`, `ProviderDegradedPayloadContractTest` — 7 test methods, all passing | No | None. |
| AC3 — `idempotencyKey` presence/absence correctly documented | Yes | All 5 event schemas (see L5 row) | Same as L5 row (Phase 11 Gap 1 closed the enforcement gap Kimi found in the pre-Phase-11 test bodies) | No | None. |
| AC4 — money discipline executable | Yes | `MoneyFieldsAreDecimalStringsContractTest.java` (`MONETARY_FIELD_NAMES = {"expectedAmount", "amount"}`) walking all 6 contract files | `MoneyFieldsAreDecimalStringsContractTest` (2 tests) | No | None. |
| AC5 — `DegradationReason`/`AttestOutcome` enum-drift guards | Yes | `provider-degraded.v1.schema.json`'s `reason` enum (`UNHEALTHY, LAGGING, REPEATED_DISAGREEMENT`, verified identical to `DegradationReason.values()`); `crypto-internal.yaml`'s `AttestResponse.outcome` enum (`SIGNED, BLOCKED`, deliberately narrower than the 3-value `AttestOutcome` Java enum — `REFUSED` is structurally impossible in a `200`) | `ProviderDegradedPayloadContractTest.everyDegradationReasonValueIsCoveredByTheSchemaEnum` (now bidirectional, Phase 11 Gap 8); `CryptoInternalOpenApiContractTest.attestResponseOutcomeSchemaEnumMatchesTheTwoValuesTheDtoCanActuallyProduce` | No | None. |
| AC6 — `tx-finalized` byte-for-byte fidelity to `design.md` §4c | Yes | `contracts/events/chain/tx-finalized.v1.schema.json`, verified identical to `design.md`'s fenced JSON block by both a manual `diff` (Phase 6/7) and, as of Phase 11, an automated structural comparison | `FinalizedPayloadContractTest.schemaIsByteForByteEquivalentToDesignMdSection4c` (Phase 11 Gap 7 — closes what was previously a manual-only check) | No | None. |
| Phase 3 Finding #4 (both `AttestResponse` variants tested against one schema) | Yes | `realInstancesByComponentName()`'s `"AttestResponse-signed"`/`"AttestResponse-blocked"` synthetic keys, resolved to the one `AttestResponse` schema (Phase 6 bug catch) | `everyComponentSchemaMatchesItsRealDtoShape` | No | None. |
| Phase 9 Finding #3 (`tx-finalized`'s `confirmations`/`addressPoisoningFlag` never emitted by `FinalizedPayload`) | Accepted, document-only | `TxLifecyclePublisher.FinalizedPayload` (watch/TxLifecyclePublisher.java:128-131) has neither field; both are optional in the schema, copied verbatim from `design.md` per the frozen brief's own explicit instruction | N/A — deliberately not fixed, per Phase 9's disposition | No (both fields are optional, not required) | Documented, intentional gap between the locked verbatim-copy instruction and current emission — flagged as a candidate follow-up, not a defect. |
| Phase 9 Findings #4/#5/#6 (`watchId` format assertion, unused import, `controllerRoutes()` disclosure) | Yes | `CryptoInternalOpenApiContractTest.java` (import removed; assertion strengthened; Javadoc added) | `watchIdPathParameterIsDeclaredOnTheDeleteOperation` | No | None. |
| Phase 11 Gaps #1/#2/#4/#5/#6/#7/#8 (idempotencyKey enforcement, `additionalProperties`, security, `Chain` enum-drift, `status` type, AC6 automation, bidirectional reason-enum) | Yes | See per-row evidence above and Phase 11's disposition table in `10-test-generation.md` | 5 new test methods + strengthened assertions in 6 existing tests | No | None. |
| Phase 11 Gap #3 (exhaustive per-field type/format checking) | Rejected — deliberate scope boundary | `UserLifecycleEventPayloadContractTest`'s own Javadoc discloses the same structural-only scoping; no AC calls for it | N/A | N/A | Documented rejection, not a gap in the delivered scope. |

## Principal-engineer review

**(1) Is the task fully complete?** Yes. All 6 files the frozen brief authorized exist, are committed,
and are correct: `contracts/api/crypto-internal.yaml` (4 routes, 6 schemas), and the 5
`contracts/events/chain/*.v1.schema.json` files. All 6 required test classes exist and pass (23 test
methods total after Phase 11). The one process gap discovered along the way — the review-prep commit
(`79e2a44`) never `git add`ed the 6 contract files, so Kimi's Phase 8 review ran against a git state
missing the primary deliverables — was caught, verified (the files existed on disk the whole time; only
the commit was incomplete), and fixed at Phase 9 (`21e056a`).

**(2) Does it satisfy every acceptance criterion?** Yes — AC1 through AC6 all have direct implementation
evidence and executable test coverage. Two of the six (AC3, AC6) initially had a real enforcement gap
that Kimi's Phase 11 review correctly identified and this pipeline closed: AC3's `idempotencyKey`
presence/absence was implied but never directly asserted, and AC6's verbatim-fidelity requirement was
checked once by hand rather than continuously in CI. Both are now directly, automatically enforced.

**(3) Does it violate any LOCKED decision?** No. L5's own exception clause for `provider-degraded` is
implemented and documented exactly as the spec requires, with the real vendor-code idempotency-key format
surfaced in the schema's `description` rather than left for consumers to wrongly infer an L5 violation.
No file under `spec/`, no existing controller/DTO/payload class, and neither `auth.yaml` nor
`contracts/events/auth/*` was touched — all confirmed via `git diff --stat` across every commit this task
produced.

**(4) Remaining risks?**
- **`confirmations`/`addressPoisoningFlag` on `tx-finalized` are currently unemitted** (Phase 9 Finding
  #3, accepted document-only) — both fields are optional and copied verbatim per the frozen brief's own
  locked instruction; not a defect, but worth revisiting if/when confirmation-count or
  address-poisoning-detection logic is actually wired into the finalized path.
- **Exhaustive per-field type/format/enum checking for the 5 event schemas remains out of scope**
  (Phase 11 Gap #3, rejected) — this mirrors the deliberate, already-established scope boundary of
  `UserLifecycleEventPayloadContractTest`'s identical technique in `services/auth`; a future task
  introducing a real JSON-Schema-validation library (explicitly out of this task's own scope) would
  close this more completely than more hand-written assertions could.
- **No new risk was introduced by this task** — it is purely additive documentation plus tests; no
  production code was touched at any point across all 4 review/resolution phases.

## Verdict

**PASS** — every requirement (R28, L5), every acceptance criterion (AC1-AC6), and every accepted finding
across Phase 3, Phase 8/9, and Phase 11 are implemented, tested, and traced to evidence. The one process
defect found along the way (untracked contract files) was caught by Kimi's Phase 8 review, verified
against the actual repository state, and fully resolved at Phase 9. The full module regression (743
tests) shows zero regressions and only the same 6 pre-existing, disclosed, unrelated failing tests
(`ProviderHealthRepositoryIntegrationTest`, `ObservationRepositoryIntegrationTest`,
`QuorumDecisionRepositoryIntegrationTest`, `TokenAllowlistRepositoryIntegrationTest` — all DB-permission
issues in modules this task never touches).
