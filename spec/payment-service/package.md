# Feature Spec: Payment & Verification Service — Phase 1

| Field | Value |
|---|---|
| Spec ID | `PAY-PHASE1` |
| Version | `0.1` |
| Author (senior/owner) | `<name>` |
| Implementer | `TBD` |
| Status | `DRAFT` |
| Target repo / service | `services/payment` |
| Skills to load | `spec-authoring`, `code-review` |
| Standing rules | [`agents.md`](agents.md) in this directory is authoritative for `services/payment` (distilled from `ARCHITECTURE.md`, `docs/service-languages.pdf`, the ADRs, and the sibling `spec/auth-service`). This spec references it and does not restate or override it except where §4a says so explicitly. |

## 0. TL;DR

The Payment & Verification Service is the **core Phase 1 domain**: it creates invoices, registers on-chain watches with the Crypto Service, drives every payment through a reorg-aware verification state machine (`CREATED → WATCHING → SEEN → CONFIRMING → FINALIZED → ATTESTED`), and — only at finality — issues a tamper-evident, KMS-signed receipt anchored in an append-only hash-chain ledger and stored in S3 Object Lock. It owns the `payments` schema, consumes `chain.tx.*` events, and publishes `invoice.created`, `payment.seen`, `payment.finalized`, and `receipt.issued`. It never talks to a blockchain and never holds key material.

## 1. Context & why now

`ARCHITECTURE.md` §3.3 makes this service the thing Phase 1 actually sells: "the simplest and most trusted method of verifying stablecoin payments" (`new_features.md`, Phase 1). The two failures the whole platform is designed against — Themistra attesting to something false, or someone credibly claiming our records were altered (`ARCHITECTURE.md` §1) — are both *owned here*: this service decides when a payment is real enough to attest, and it maintains the record that must survive an insider-tampering claim.

The Crypto Service produces chain facts; the Auth Service issues identity. Neither is useful to a merchant until this service turns "a transaction happened" into "here is a signed receipt you can hand to your accountant or a court." Nothing about invoices, the state machine, the ledger, or receipts exists yet — this spec builds all of it.

Build order (`ARCHITECTURE.md` §10) places this service in weeks 5–9, after Auth and after the Crypto Service's `chain.tx.*` contract and `POST /attest` endpoint are defined (see `spec/crypto-service`). This spec is authored against that contract.

## 2. Scope

**In scope**

- `invoice` module: invoice creation, lifecycle, expiry, merchant-scoped listing/retrieval, and `invoice.created` emission.
- `payment` module: payment records and the reorg-aware verification state machine, driven by consumed `chain.tx.*` events.
- `verification` module: the per-payment verification record (what we observed, when, and why a state moved).
- `receipt` module: receipt issuance at finality, the append-only **hash-chain ledger**, the daily on-chain anchor, and receipt storage in S3 Object Lock (WORM).
- `attestation` module: the outbound REST client to the Crypto Service (`POST /attest` for the receipt signature, `POST/DELETE /watches` for watch registration).
- `export` module: tax-ready transaction history and exports.
- `consumer` module: idempotent Kafka consumers for `chain.tx.seen|confirmed|finalized|reorged`.
- `events` module: transactional outbox publisher/relay (from `libs/java/outbox`).
- Contract artifacts: `contracts/api/payments.yaml`, and `contracts/events/payments/*.schema.json` for the four published events.
- Supporting code: validated `@ConfigurationProperties`, RFC 9457 error handling, resource-server JWT validation, ArchUnit module-boundary tests.

**Explicitly out of scope**

- **Any direct blockchain access.** All chain facts arrive as `chain.tx.*` events; all chain reads and the KMS signature come from the Crypto Service. This service holds no RPC endpoints and no signing keys.
- **Computing finality or confirmations.** Per-chain finality is the Crypto Service's authority (`ARCHITECTURE.md` §6.2); this service reacts to `chain.tx.finalized`, it never counts confirmations to decide finality itself.
- **Quorum / provider logic, token allowlist, address-poisoning detection** — all owned by the Crypto Service (§6.1, §6.3). This service consumes their results (e.g. an address-poisoning flag on an observation) and surfaces them.
- **Compliance/OFAC screening decisions** — performed in the Crypto Service attest path (§6.6). This service honours a `BLOCKED` attest outcome; it does not call a screening API.
- **Sending email / in-app / webhooks** — the Notification Service consumes this service's events and delivers (`spec/notification-service`).
- Phase 2+ concerns: AI evidence interpretation, dispute resolution, reputation, the evidence graph (`new_features.md` Phases 2–5). These arrive as *new consumers* of the events and ledger this service already produces.
- Payment/invoice **authorization policy of other services** — this service applies its own rules to the JWT authorities Auth issues; it does not evaluate Auth's or Crypto's internal policy.
- Extracting the outbox to `libs/java/outbox` beyond consuming it; infra/CDK changes beyond what the service manifest expects.

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

Every requirement in `requirements.md` maps to at least one named test. Unit tests use a fixed `Clock` and plain JUnit (no Spring context) for the state machine and ledger; integration tests use Testcontainers (Postgres + Kafka) and a WireMock stand-in for the Crypto Service.

- `shouldCreateInvoiceAndRegisterWatch` → R1
- `shouldEmitInvoiceCreatedInSameTransactionAsInsert` → R2
- `shouldRejectInvoiceWithNonAllowlistedTokenReferenceShape` → R3
- `shouldRejectInvoiceAmountThatIsZeroOrNegativeOrFloat` → R4
- `shouldExpireInvoiceAndUnregisterWatchAfterTtl` → R5
- `shouldTransitionCreatedToWatchingOnWatchAck` → R6
- `shouldTransitionToSeenOnChainTxSeen` → R7
- `shouldEmitPaymentSeenOnFirstSeen` → R8
- `shouldTransitionToConfirmingOnChainTxConfirmed` → R9
- `shouldTransitionToFinalizedOnChainTxFinalized` → R10
- `shouldWalkBackwardConfirmingToSeenOnReorg` → R11
- `shouldWalkBackwardSeenToWatchingOnReorgBeforeConfirm` → R12
- `shouldDedupeDuplicateChainEventsByEventKey` → R13
- `shouldIgnoreOutOfOrderOrStaleChainEvents` → R14
- `shouldFlagUnderpaymentAndOverpaymentAgainstInvoiceAmount` → R15
- `shouldSurfaceAddressPoisoningFlagFromObservation` → R16
- `shouldIssueReceiptOnlyFromFinalizedState` → R17
- `shouldNeverIssueReceiptBeforeFinalized` → R18
- `shouldRequestKmsSignatureViaCryptoAttestEndpoint` → R19
- `shouldNotIssueReceiptWhenAttestReturnsBlocked` → R20
- `shouldAppendLedgerEntryWithPrevHashChainingToHead` → R21
- `shouldDetectBrokenHashChainOnVerification` → R22
- `shouldStoreReceiptInS3ObjectLockWorm` → R23
- `shouldTransitionToAttestedAfterReceiptStoredAndLedgerAppended` → R24
- `shouldEmitReceiptIssuedAfterAttestation` → R25
- `shouldAnchorLedgerHeadOnChainDaily` → R26
- `shouldExportTaxReadyHistoryForMerchantAndDateRange` → R27
- `shouldScopeInvoiceAndReceiptReadsToOwningMerchant` → R28
- `shouldReturnProblemJsonWithNoInternalDetailOn4xx` → R29
- `shouldRouteEachPublishedEventToItsPaymentsTopic` → R30
- `shouldConformToPaymentsOpenApiContract` → R31
- `shouldPreventCrossModuleEntityImports` → L1/L12
- `shouldRejectFloatingPointMoneyTypesAtArchUnit` → L6

## 9. Verification checklist — implementer self-checks before raising PR

- [ ] All §3 acceptance criteria have a passing named test from §8.
- [ ] Every §4a LOCKED decision implemented as written (no silent deviation).
- [ ] Every §4c VERBATIM artifact copied exactly (state enum, DDL, event schemas, Crypto API shapes).
- [ ] **A receipt is issued only from `FINALIZED`** — a test asserts no code path signs from `SEEN`/`CONFIRMING`.
- [ ] Money is `BigDecimal`/`NUMERIC` end-to-end; no `double`/`float`/`Double` on any money field (ArchUnit-enforced).
- [ ] Every consumed `chain.tx.*` handler is idempotent and dedupes on the event key; replaying the same event twice is a no-op.
- [ ] The hash chain verifies head-to-tail after a full register→finalize→attest flow; tampering with any row is detectable.
- [ ] Receipts land in the S3 Object Lock bucket in compliance mode; no code path can overwrite an existing receipt object.
- [ ] The service never imports an AWS KMS signing client and never reads a chain RPC endpoint (ArchUnit-enforced package ban).
- [ ] Every published event is emitted through the outbox in the same transaction as the state change; `EventTopics` maps every aggregate type.
- [ ] `mvn -pl services/payment verify` passes (unit + integration + Testcontainers).
- [ ] `contracts/api/payments.yaml` covers every non-internal endpoint and error response; event payloads validate against `contracts/events/payments/*`.

## 10. Migration, rollout & rollback

**Schema**

- Greenfield service: the first migration is `V1__payments_baseline.sql` (see [`design.md`](design.md#4c-verbatim-artifacts)). Flyway DDL-only, `payments` schema, one logical schema per service (`ARCHITECTURE.md` §5). There is no pre-existing schema to preserve (contrast the auth service's immutable V1–V4).
- Rollback: Flyway undo is not enabled. Because this is the first release, rollback = drop the `payments` schema in a non-production environment and redeploy. In production the service is not yet serving, so there is no live-data rollback path to design for the initial cut.

**Code rollout**

- Deploy order (`ARCHITECTURE.md` §10): the Crypto Service `chain.tx.*` topics and `POST /attest` / `POST /watches` endpoints must exist first. Until the Crypto Service is deployed, this service can be run against WireMock/Testcontainers only.
- Readiness gates on DB + Kafka + reachability of the Crypto Service base URL + resolution of the S3 receipt bucket and KMS-public-key well-known URL, so no pod serves `POST /invoices` before it can complete the full flow.
- Rolling update on EKS; ≥ 2 replicas. The scheduled ledger-anchor and invoice-expiry jobs are ShedLock-guarded so they run once across replicas.

**Emergency rollback**

- Revert to the previous image. Because publishing is outbox-driven and consumers are idempotent, an in-flight payment that was mid-state-transition at rollback is re-driven safely when the new image resumes: consumed `chain.tx.*` events are replayable, and no receipt exists until `FINALIZED`, so nothing signed needs revoking.

## 11. Open questions for the author

- Q1. **Amount tolerance.** What over/under-payment tolerance is acceptable before a payment is treated as satisfying an invoice — exact match only, or a configurable band (e.g. ±0 for stablecoins)? Drives R15 and the `PAID`/`UNDERPAID`/`OVERPAID` invoice states. Placeholder in `design.md` §4b-O1.
- Q2. **Multiple / partial payments per invoice.** Can one invoice be satisfied by several transactions (partial fills), or is it strictly one tx per invoice at launch? Affects the `payment`↔`invoice` cardinality in the schema (§5).
- Q3. **Invoice expiry TTL and behaviour.** Default invoice validity window, and what happens to a payment that finalizes *after* expiry — still attested, or `HELD` for ops? Drives R5. Placeholder in `design.md` §4b-O2.
- Q4. **On-chain anchor mechanism.** The daily hash-chain head anchor (`ARCHITECTURE.md` §6.5) is "a cheap tx embedding the head hash." Does this service request that anchoring tx through the Crypto Service (which owns all chain writes), and on which chain? Blocker for the anchor job (R26). Placeholder in `design.md` §4b-O3.
- Q5. **Receipt format & generator.** Receipt is "PDF/JSON" (`ARCHITECTURE.md` §5). Confirm both formats, the canonical JSON shape that gets signed, and the PDF library. Drives R19/R23.
- Q6. **Tax-export format.** Which export formats and columns are required for "tax-ready history" (CSV, JSON, per-jurisdiction schema)? Drives R27.
- Q7. **Attest request/response contract.** This spec assumes the Crypto Service `POST /attest` shape defined in `spec/crypto-service/design.md` §4c. Confirm it is authoritative, or paste the final signature so it can be pinned verbatim in `design.md` §4c.
- Q8. **Agents / standing-rules file.** **Resolved (2026-07-20):** `spec/payment-service/agents.md` now holds the durable rules and this spec references it. Open follow-up: whether to also seed a single repo-root `agents.md` for the platform-common section shared across all four service files (dedupe), or keep them self-contained per service.
