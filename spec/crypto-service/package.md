# Feature Spec: Crypto Service — Phase 1

| Field | Value |
|---|---|
| Spec ID | `CRYPTO-PHASE1` |
| Version | `0.1` |
| Author (senior/owner) | `<name>` |
| Implementer | `TBD` |
| Status | `DRAFT` |
| Target repo / service | `services/crypto` (**CODEOWNERS-protected**) |
| Skills to load | `spec-authoring`, `code-review` |
| Standing rules | [`agents.md`](agents.md) in this directory is authoritative for `services/crypto` (distilled from `ARCHITECTURE.md`, `docs/service-languages.pdf`, `SECURITY-THREAT-MODEL.md`, the ADRs, and the sibling `spec/auth-service`). This spec references it and does not restate or override it except where §4a says so explicitly. |

## 0. TL;DR

The Crypto Service is the **only** component that talks to blockchains and the **only** holder of the path to `kms:Sign` on the attestation key. It fetches every verification fact (tx existence, amount, token, confirmations, finality) from N independent RPC providers, treats a fact as true only on **2-of-3 quorum**, applies **per-chain finality policy**, treats reorgs as first-class events, validates tokens by contract address against a signed allowlist, flags address poisoning, screens counterparties for OFAC, and exposes an internal `POST /attest` that returns a KMS signature for a receipt digest — while logging every provider answer verbatim so any past attestation can be re-derived and defended. It owns the `chain` schema and publishes `chain.tx.{seen,confirmed,finalized,reorged}` and `chain.provider.degraded`.

## 1. Context & why now

This service is Themistra's differentiator and its single largest correctness risk. `ARCHITECTURE.md` §1–§2 frames the whole platform's core threat as "an attacker makes Themistra attest to something false." Everything that could cause a false attestation — quorum arbitration, finality policy, reorg-safe state, token identity, and the KMS signing path — is deliberately concentrated in this one Java service and one review pipeline (`docs/service-languages.pdf` §3.1, §3.2) so it can be audited as a unit.

`ARCHITECTURE.md` §10 places it in weeks 3–8 — "the hard, differentiating engineering — staff it accordingly" — before the Payment Service depends on its `chain.tx.*` contract and `POST /attest`. `SECURITY-THREAT-MODEL.md` must be completed before the first line of code here (`ARCHITECTURE.md` §6.7): threats #1 (rogue provider), #2 (fake USDT contract), #3 (reorg after "confirmed"), #4 (stolen server creds), #6 (address poisoning) are all mitigated in this service. Nothing exists yet beyond the README and an empty `sidecars/` folder; this spec builds the launch scope (Tron + Ethereum).

## 2. Scope

**In scope (launch: Tron + Ethereum)**

- `adapter` module: one adapter per chain behind a common interface (`getTx`, `getTokenInfo`, `subscribeAddress`, `getFinalityStatus`); web3j for EVM, Tron via TronGrid/java-tron gRPC.
- `provider` module: N-provider fan-out per chain, provider health tracking, and `chain.provider.degraded` emission.
- `quorum` module: 2-of-3 agreement per fact; disagreement → `HELD` + ops alert, never auto-resolved.
- `finality` module: per-chain finality policy objects (Ethereum beacon `finalized` checkpoint; Tron solidified block).
- `watch` module: the internal watch-registration API and the long-running watcher layer (subscriptions/polling on virtual threads).
- `reorg` module: reorg detection and `chain.tx.reorged` emission with backward-safe cursors.
- `token` module: signed, versioned canonical-token allowlist per chain, contract-address identity, EIP-55 / Base58 checksum validation, and address-poisoning similarity flagging.
- `screening` module: counterparty wallet-risk / OFAC screening at attest time; sanctioned hit → `BLOCKED`.
- `attest` module: the internal `POST /attest` endpoint — the sole path to `kms:Sign` — and the published-verification-keys well-known endpoint.
- `observation` module: the raw provider-observation log (verbatim), in the `chain` schema and mirrored to S3.
- `events` module: transactional outbox for all published `chain.*` events.
- Contract artifacts: `contracts/api/crypto-internal.yaml` (internal watch + attest API) and `contracts/events/chain/*.schema.json`.

**Explicitly out of scope**

- **Any custody of user funds or user private keys** (`ARCHITECTURE.md` §1). The only key material is the platform attestation key, and it never leaves KMS.
- **Chains beyond Tron + Ethereum at launch.** Base, BSC, Arbitrum, Solana are roadmap (`ARCHITECTURE.md` §3.4, §6.2); the adapter interface must not preclude them, but they are not built here.
- **TypeScript chain sidecars.** `sidecars/` is reserved for chains whose Java tooling is thin (e.g. Solana later). At launch both chains are covered by Java tooling, so no sidecar ships. When one does, it is **translation-only** — no quorum authority, no signing, no business state (`docs/service-languages.pdf` §3.2).
- **Invoice / receipt / ledger domain** — owned by the Payment Service (`spec/payment-service`). This service returns a signature; it does not know what a receipt means.
- **Self-hosted Tron/Ethereum nodes** as a hard launch dependency — roadmap month 1–3; until then three *commercially independent* providers (§6.1). The quorum code must not assume a self-hosted provider exists.
- **Nitro Enclaves** (`ARCHITECTURE.md` §6.4 stretch, quarter 2–3) — the attest path must be enclave-portable but enclaves are not built in Phase 1.
- Identity/token issuance (Auth Service); notification delivery (Notification Service).

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

Unit tests (plain JUnit, fixed `Clock`, mocked providers) cover quorum, finality policy, reorg walks, token validation, and address-poisoning. Integration tests use Testcontainers (Postgres + Kafka) with **fake provider adapters** that can be scripted to agree, disagree, lag, or reorg — the real RPC providers are never called in tests.

- `shouldTreatFactAsTrueOnlyWhenTwoOfThreeProvidersAgree` → R1
- `shouldHoldFactAndAlertWhenProvidersDisagree` → R2
- `shouldNeverAutoResolveDisagreementInPayersFavor` → R3
- `shouldLogEveryProviderResponseVerbatimToObservationLog` → R4
- `shouldEmitProviderDegradedWhenAProviderIsUnhealthy` → R5
- `shouldRequireBeaconFinalizedCheckpointForEthereumFinality` → R6
- `shouldRequireSolidifiedBlockForTronFinality` → R7
- `shouldEmitChainTxSeenOnQuorumAgreedFirstSighting` → R8
- `shouldEmitChainTxConfirmedWithConfirmationCount` → R9
- `shouldEmitChainTxFinalizedOnlyAtPerChainFinality` → R10
- `shouldEmitChainTxReorgedAndWalkCursorBackwardOnReorg` → R11
- `shouldCarryDeterministicIdempotencyKeyOnEveryEmittedEvent` → R12
- `shouldIdentifyTokenByContractAddressNotSymbol` → R13
- `shouldSurfaceUnknownTokenForNonAllowlistedContract` → R14
- `shouldValidateEip55ChecksumForEvmAddresses` → R15
- `shouldValidateBase58ChecksumForTronAddresses` → R16
- `shouldFlagAddressPoisoningOnPrefixSuffixSimilarity` → R17
- `shouldRegisterWatchAndReturnWatchId` → R18
- `shouldUnregisterWatchOnDelete` → R19
- `shouldReturnKmsSignatureFromAttestForValidDigest` → R20
- `shouldReturnBlockedFromAttestOnSanctionedCounterparty` → R21
- `shouldOnlyAllowAttestPathToInvokeKmsSign` → R22
- `shouldRejectAttestWhenQuorumOrFinalityNotMet` → R23
- `shouldPublishVerificationKeysAtWellKnownUrl` → R24
- `shouldTreatSidecarOutputAsJustAnotherProviderAnswer` → R25
- `shouldRouteEachChainEventToItsTopic` → R26
- `shouldRequireInternalScopeForWatchAndAttestEndpoints` → R27
- `shouldConformToCryptoInternalOpenApiContract` → R28
- `shouldPreventCrossModuleEntityImports` → L15

## 9. Verification checklist — implementer self-checks before raising PR

- [ ] All §3 acceptance criteria have a passing named test from §8.
- [ ] Every §4a LOCKED decision implemented as written (no silent deviation).
- [ ] Every §4c VERBATIM artifact copied exactly (interface, finality table, DDL, event schemas, attest/watch API).
- [ ] **No single-provider answer ever leaves the service as fact** — a test asserts every emitted fact passed 2-of-3 quorum (L1).
- [ ] **`kms:Sign` is reachable only from the attest path** — an ArchUnit + integration test asserts no other package can invoke the signer (L11, R22).
- [ ] Every provider response is persisted verbatim to the observation log before the quorum decision (L3).
- [ ] Finality is decided by the per-chain policy object, never a global confirmation constant (L4).
- [ ] Reorg walks the cursor backward and emits `chain.tx.reorged`; no forward state survives a reorg it invalidates (L6).
- [ ] Tokens are matched only by `<chain, contractAddress>`; a non-allowlisted contract yields `UNKNOWN_TOKEN`, never a symbol guess (L7).
- [ ] Every emitted event carries the deterministic key `chain:txhash:eventtype` (L5).
- [ ] Attest refuses to sign unless quorum + finality (+ screening) passed (L10, L12).
- [ ] No secret, provider API key, or KMS key ARN is committed; External Secrets injects them; no AWS SDK misuse leaks key material (L13).
- [ ] `mvn -pl services/crypto verify` passes (unit + integration with fake providers); Docker image builds.
- [ ] `contracts/api/crypto-internal.yaml` and `contracts/events/chain/*` cover every internal endpoint and every emitted event.

## 10. Migration, rollout & rollback

**Schema**

- Greenfield: first migration is `V1__chain_baseline.sql` (see [`design.md`](design.md#4c-verbatim-artifacts)), `chain` schema, Flyway DDL-only. No pre-existing schema to preserve. The signed token allowlist is seeded by a companion migration/config (per-chain official USDT/USDC contracts), never hand-edited at runtime.

**Code rollout**

- Deploy order (`ARCHITECTURE.md` §10): this service and its `chain.tx.*` topics + `POST /attest` must exist before the Payment Service can complete a flow. It can run standalone against fake provider adapters until real provider credentials are provisioned.
- Readiness gates on DB + Kafka + at least the quorum-minimum number of healthy providers per launch chain + KMS attestation-key reachability. A pod that cannot reach quorum-minimum providers for a chain must not serve watches for that chain.
- Rolling update on EKS; ≥ 2 replicas. Watcher assignment across replicas must be coordinated (O5) so a watched address is not double-driven; scheduled/leased work is ShedLock-guarded.
- IAM: **only the Crypto Service role may call `kms:Sign` on the attestation key** (`ARCHITECTURE.md` §8); an alarm fires on any use of that key outside this role.

**Emergency rollback**

- Revert to the previous image. Emitted events are idempotent (dedupe key L5) and the observation log is append-only, so a re-driven watcher re-derives the same facts. Because finality gates attestation, a rollback mid-verification never leaves a half-signed receipt (receipts live in the Payment Service and are only requested at finality).

## 11. Open questions for the author

- Q1. **Provider set & quorum N per chain.** Which 3 commercially-independent providers per launch chain (e.g. Alchemy + QuickNode + a third for Ethereum; TronGrid + ? + ? for Tron), and is N fixed at 3 with 2-of-3, or configurable per chain? Placeholder in `design.md` §4b-O1. Blocker for real deployment (not for fake-provider tests).
- Q2. **Screening provider (§6.6).** Chainalysis, TRM Labs, or Elliptic — chosen on pricing. Confirm the vendor and the exact request/response and error semantics so the `screening` client can be pinned in §4c. Until chosen, screening is behind an interface with a fail-closed stub. Blocker for R21.
- Q3. **Fail-open vs fail-closed on screening/quorum outages.** If the screening API is unreachable at attest time, does attest `BLOCK` (fail-closed) or proceed-with-flag? Given the platform's posture, the recommended default in `design.md` §4a-L12 is **fail-closed (no signature)** — confirm.
- Q4. **Confirmation-count semantics for Tron.** `chain.tx.confirmed` carries a confirmation count; for Ethereum this is block depth, for Tron it is confirmations toward the solidified block. Confirm the Tron count basis so the Payment Service displays it consistently.
- Q5. **Watcher subscription vs polling per provider.** Which launch providers support websocket subscription vs require polling, and the polling interval budget? Drives O2 and watcher-lag SLOs. Placeholder in `design.md` §4b-O2.
- Q6. **Anchor-write endpoint (Payment Q4).** The Payment Service's daily ledger anchor is an on-chain write and this service owns all chain writes. Should this service expose `POST /internal/v1/anchors` to submit that anchor tx, on which chain, and with which key (a low-value operational key, **not** the attestation key)? Blocker for Payment R26.
- Q7. **KMS signing key spec.** Confirm the KMS key type/algorithm for attestation (e.g. ECDSA P-256 / secp256k1 / RSA) and the digest/signature encoding, so receipts embed a verifiable `kmsKeyId` and the published verification keys match. Drives R20/R24 and the Payment receipt digest (Payment Q5).
- Q8. **Agents / standing-rules file.** **Resolved (2026-07-20):** `spec/crypto-service/agents.md` now holds the durable rules and this spec references it. Open follow-up: whether to also seed a single repo-root `agents.md` for the platform-common section shared across all four service files (dedupe), or keep them self-contained per service.
