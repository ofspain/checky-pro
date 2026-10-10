# Feature Spec: Intelligence Service — Phase 2

| Field | Value |
|---|---|
| Spec ID | `INTELLIGENCE-PHASE2` |
| Version | `0.1` |
| Author (senior/owner) | `<name>` |
| Implementer | `TBD` |
| Status | `DRAFT` |
| Target repo / service | `services/intelligence` (does not exist yet — greenfield) |
| Skills to load | `spec-authoring`, `code-review` |
| Standing rules | [`agents.md`](agents.md) in this directory is authoritative for `services/intelligence`. This spec references it and does not restate or override it except where §4a says so explicitly. |

## 0. TL;DR

The Intelligence Service is Themistra's evidence platform (`new_features.md` Phase 2,
`ARCHITECTURE.md` §9) — a new consumer of existing Phase 1 infrastructure, zero changes to any
already-built service. It accepts evidence uploads (screenshots, receipts, PDFs) and bare
transaction-hash submissions, interprets uploaded images via an external vision model (Claude's
own vision capability), runs in-house integrity checks (EXIF/metadata, perceptual-hash near-
duplicate detection), and correlates everything into a per-transaction evidence graph where
on-chain facts always win over AI-extracted ones. It owns the `intelligence` schema and is the
foundation Dispute Service (Phase 3, not built) will later consume.

**One real cross-service prerequisite, found and specced during this task's own authoring, not
yet built:** automatic evidence collection from a bare transaction hash needs a new, small,
read-only lookup endpoint on crypto-service (`spec/crypto-service` R29/L16/task 30) — its existing
`registerWatch` only watches an address prospectively for a future payment and cannot resolve a
hash from something that already happened. See `design.md` §4c and §11 Q1.

## 1. Context & why now

`new_features.md` Phase 2 names four capabilities: AI evidence interpretation, evidence integrity
analysis, multi-evidence correlation, and automatic evidence collection from a tx hash.
`ARCHITECTURE.md` §9 already provisions for this as a "new **Intelligence Service** consuming
existing Kafka topics + S3 evidence bucket; zero changes to Phase 1 services" — this spec is that
service's own first task.

This is the foundation Phase 3 (Dispute Resolution, `ARCHITECTURE.md` §9: "Dispute Service reads
the hash-chain ledger + observation log") depends on — `new_features.md`'s own Phase 3 vision
explicitly compares "claimant statements, blockchain facts, uploaded evidence, extracted AI
observations" when validating a claim, so Dispute Service needs this service's own output, not
just crypto-service's/payment-service's raw records. Phases 4 (Reputation) and 5 (Fraud) are
further downstream of Phase 3's own output and are not addressed by this task.

Nothing exists yet for this domain — no code, no spec, no `services/intelligence` directory.

## 2. Scope

**In scope**

- `evidence` module: upload accept/store (S3 WORM), metadata-only retrieval, dedup by digest (R1-R5).
- `collection` module: bare-tx-hash submission, polling crypto-service's new lookup endpoint,
  collected-facts recording, reorg-aware invalidation (R6-R10).
- `extraction` module: external vision-model submission and structured-field extraction, with
  model/version/confidence always attached (R11-R14).
- `integrity` module: in-house EXIF/metadata extraction and perceptual-hash near-duplicate
  detection only — explicitly not pixel-level editing-detection (R15-R19).
- `correlation` module: the per-transaction-hash evidence graph, on-chain-wins conflict recording
  (R20-R23).
- Access control and audit (R24-R26), mirroring every other Themistra service's identical shape.
- Contract artifacts: a new `contracts/api/intelligence.yaml` (if any endpoint needs documenting
  beyond this service's own authenticated-only surface) and
  `contracts/events/intelligence/correlation-updated.v1.schema.json` (O7).

**Explicitly out of scope**

- **Dispute adjudication, claim validation, dispute narratives, timeline reconstruction, smart
  contract dispute analysis** — all `new_features.md` Phase 3, a separate future service (L1).
- **Wallet reputation, merchant trust passport, counterparty behaviour graph** — Phase 4, not
  addressed; depends on Phase 3's own output existing first.
- **Fraud detection, institutional APIs, cross-chain correlation** — Phase 5, not addressed.
- **True pixel-level editing-detection / error-level analysis (ELA)** — explicitly deferred (L3,
  R19); a future, separately-reviewed task's own decision if ever added.
- **Any dependency on payment-service's own invoice concept** — does not exist yet; evidence
  anchors to `<chain, txHash, account>` only (L4).
- **The new crypto-service lookup endpoint's own implementation** — specced in
  `spec/crypto-service` (R29/L16/task 30), not built as part of this task. This task's own R6-R10
  are blocked on that task landing first (§11 Q1).
- **A broader, role-based (ADMIN/reviewer) read path** — self-only access for this task (L11);
  Dispute Service's own future reviewer workflow is the natural place for one.

## 3. Requirements — acceptance criteria (EARS)

See [`requirements.md`](requirements.md).

## 4. Design — how to build it

See [`design.md`](design.md).

## 5. Data model & schema changes

See [`design.md`](design.md#5-data-model--schema-changes).

## 6. Package & file map

See [`design.md`](design.md#6-package--file-map).

## 7. Tasks — ordered execution plan

See [`tasks.md`](tasks.md).

## 8. Test plan — named tests

Unit tests (plain JUnit, fixed `Clock`, a capturing fake `VisionExtractionClient` and a capturing
fake crypto-service lookup client) cover upload/dedup, polling/invalidation, extraction, integrity
analysis, and correlation. Integration tests use Testcontainers (Postgres + Kafka); the real vision
API and the real crypto-service endpoint are never called in tests.

- `shouldStoreEvidenceFileInS3AndPersistEvidenceItemBeforeAck` → R1
- `shouldReturnExistingItemOnByteIdenticalReupload` → R2
- `shouldRejectOversizedOrDisallowedContentTypeWithoutStoring` → R3
- `shouldNeverExposeRawFileBytesViaAnyApiResponse` → R4
- `shouldNeverDeleteOrOverwriteAStoredEvidenceFile` → R5
- `shouldBeginPollingOnTxHashOnlySubmissionWithNoFile` → R6
- `shouldAppendNewMilestoneFactWithoutOverwritingAnEarlierOne` → R7
- `shouldMarkFactsInvalidatedOnReorgDetectedByLaterPoll` → R8
- `shouldRecordAtMostOneRowPerEvidenceItemAndMilestone` → R9
- `shouldStopPollingAtTerminalOutcomeAndRetainCollectedFacts` → R10
- `shouldSubmitImageOrPdfToVisionModelAndPersistExtractionResult` → R11
- `shouldRecordModelIdentifierVersionAndConfidencePerField` → R12
- `shouldRecordFailedExtractionStatusWithoutInlineRetryOrBlockingEvidenceAccess` → R13
- `shouldNeverExposeAnExtractedFieldWithoutConfidenceAndModelIdentifier` → R14
- `shouldExtractExifMetadataInHouseWithoutExternalCall` → R15
- `shouldComputePerceptualHashAndCompareAgainstPriorEvidenceForSameHash` → R16
- `shouldRecordDuplicateSuspectedFindingBelowThreshold` → R17
- `shouldRecordMetadataStrippedFindingWhenExifWhollyAbsent` → R18
- `shouldNeverClaimEditingDetectionBeyondTheNamedChecks` → R19
- `shouldCreateOrUpdateOneEvidenceGraphPerTransactionHash` → R20
- `shouldRecordDisagreementFindingWithoutPreferringEitherValue` → R21
- `shouldMarkOnChainFactAuthoritativeWhenFieldsConflict` → R22
- `shouldReturnFullEvidenceGraphForAccountsOwnTransactionHash` → R23
- `shouldScopeEveryReadAndWriteToCallersOwnAccount` → R24
- `shouldRecordAuditEventForUploadPollingAndFindings` → R25
- `shouldReject401ForEveryNonActuatorEndpointWithoutAValidJwt` → R26
- `shouldPreventCrossModuleEntityImports` → L10

## 9. Verification checklist — implementer self-checks before raising PR

- [ ] All §3 acceptance criteria have a passing named test from §8.
- [ ] Every §4a LOCKED decision implemented as written (no silent deviation).
- [ ] Every §4c VERBATIM artifact copied exactly (client interface, crypto-service lookup
      contract, DDL, published event schema).
- [ ] **No evidence file's raw bytes are ever returned via any API response** (L9, R4) — a test
      asserts every evidence-related response DTO has no byte-array/stream field.
- [ ] **No extracted field appears on the wire without its model identifier and confidence**
      (L7, R14) — enforced at the DTO level, not just by convention.
- [ ] **On-chain facts are authoritative wherever a conflict is surfaced** (L6, R21/R22).
- [ ] The vision-API key is read by exactly one class (L12) — ArchUnit package-scope test.
- [ ] `mvn -pl services/intelligence verify` passes; Docker image builds.
- [ ] `contracts/events/intelligence/correlation-updated.v1.schema.json` matches the real
      published payload (contract test).
- [ ] **Not applicable yet:** crypto-service's own task 30 (the lookup endpoint this service's
      R6-R10 depend on) is a separate PR in a separate, CODEOWNERS-protected service — do not mark
      this checklist complete on the strength of this service's own code alone if R6-R10's own
      tests are stubbed against a not-yet-real endpoint.

## 10. Migration, rollout & rollback

**Schema**

- Greenfield: first migration is `V1__intelligence_baseline.sql` (`design.md` §4c), `intelligence`
  schema, Flyway DDL-only. No pre-existing schema to preserve.

**Code rollout**

- **Hard dependency, in order:** crypto-service's own task 30 (R29/L16) must be deployed before
  this service's `collection` module can do anything useful — R6-R10 would otherwise poll an
  endpoint that does not exist. Everything else in this spec (upload, extraction, integrity,
  correlation for uploaded evidence) has no such dependency and can ship independently.
- This service needs a new auth-service client registration (O1) with the `internal.crypto:write`
  scope to call crypto-service's new endpoint.
- Rolling update on EKS; ≥ 2 replicas. No leader-election/sharding concern at this scope (unlike
  crypto-service's own watcher-assignment problem) — polling is per-evidence-item, not a
  persistent subscription.

**Emergency rollback**

- Revert to the previous image. Evidence uploads are idempotent (L8); a re-driven poll re-derives
  the same facts (idempotent by `<evidenceItemId, eventType>`). The vision-API call is not
  retried inline on failure (R13), so a rollback mid-extraction leaves evidence items in a
  `FAILED` extraction status rather than a half-written one — safe to resume by re-triggering
  extraction once the new image is up (a manual/scheduled re-trigger is O8's own open question).

## 11. Open questions for the author

- Q1. **Crypto-service's own task 30 (R29/L16) is specced but not built.** This task's own R6-R10
  cannot function until `GET /internal/v1/transactions/{chain}/{txHash}` exists. Owner action:
  decide when to pick up that task — before, in parallel with, or after this service's own upload/
  extraction/integrity/correlation modules, which have no such dependency.
- Q2. **Vision model version pinning.** `design.md` L2 names `claude-sonnet-5-5` as the default,
  configurable model. Owner action: confirm this default, or name a different one, and confirm
  whether a model upgrade later requires re-running extraction on existing evidence or only
  applies going forward (recommend: going forward only — the original evidence is immutable and
  re-analyzable later per agents.md's own standing rule, so nothing is lost by not backfilling).
- Q3. **Published event consumer.** O7's `intelligence.evidence.correlation_updated` event is a
  forward design for a Dispute Service that does not exist yet. Owner action: confirm this is
  worth publishing now (cheap, decouples cleanly) versus deferring until Dispute Service's own
  spec exists and can state its actual consumption needs (recommend: publish now, it is low-cost
  and the outbox pattern is already standard here).
- Q4. **O2-O6, O8 (perceptual-hash library/threshold, max file size/content types, EXIF library,
  polling backoff/timeout, async-retry policy)** — all tagged "implementer may propose, author
  approval required" in `design.md` §4b. Not blockers for starting the upload/extraction/integrity
  modules; each needs a concrete proposal + sign-off before its own task is built.
- Q5. **S3 bucket provisioning.** `infra/` is currently an empty placeholder (per the
  `infra-pivot-before-resuming-services` memory note) — no CDK stack exists yet for the evidence
  bucket named in L5. Owner action: confirm whether this service's own first task includes
  authoring that CDK stack, or whether it is a separate infra task this spec assumes exists by the
  time `services/intelligence` is actually built.
