# 4. Design — how to build it

## 4a. LOCKED decisions — implement exactly, do NOT deviate

- L1. **2-of-3 quorum, no single-provider truth.** No single provider's answer ever leaves the service as fact (`ARCHITECTURE.md` §6.1, `docs/service-languages.pdf` §3.2). Every emitted fact required ≥2 of 3 independent providers to agree. This is the service's reason to exist — it is not a tunable that can be disabled.
- L2. **Disagreement → `HELD`, ops-alerted, never auto-resolved.** On provider disagreement the fact is held and surfaced to ops; it is never silently resolved and never resolved in any party's favor (`ARCHITECTURE.md` §6.1).
- L3. **Observation log is verbatim and written first.** Each provider's raw response is persisted verbatim (Postgres `chain` schema + S3 snapshot) *before* the quorum decision, so any past attestation can be re-derived and defended (`ARCHITECTURE.md` §6.1, §5 — 7-year retention).
- L4. **Finality is a per-chain policy object, not a global constant.** Ethereum = beacon `finalized` checkpoint; Tron = solidified block (~19 conf) (`ARCHITECTURE.md` §6.2). Adding a chain adds a policy object; no confirmation count is hardcoded across chains. Attestation only ever happens at finality.
- L5. **Deterministic idempotency key on every event.** Every emitted event carries `chain:txhash:eventtype` (`ARCHITECTURE.md` §3.4); the same tx will be observed multiple times and consumers dedupe on this key.
- L6. **Reorg is a first-class transition.** A reorg walks the watcher cursor/checkpoint backward and emits `chain.tx.reorged` (`ARCHITECTURE.md` §6.2). No forward-derived state survives a reorg that invalidates it.
- L7. **Token identity is contract address only.** Tokens are matched by `<chain, contractAddress>` against a **signed, versioned** canonical allowlist; anything else is `UNKNOWN_TOKEN`, surfaced loudly (`ARCHITECTURE.md` §6.3). A token symbol is never used to decide identity.
- L8. **Address validation is mandatory.** EIP-55 checksum on all EVM addresses; Base58Check on Tron (`ARCHITECTURE.md` §6.3). Invalid addresses are rejected at the boundary.
- L9. **Address-poisoning flagging.** When a payer address closely resembles (prefix/suffix match) a previously seen counterparty but differs, flag it on the observation so it propagates downstream (`ARCHITECTURE.md` §6.3).
- L10. **Attestation only at proven finality.** `POST /attest` refuses to sign unless the referenced tx passed quorum + finality (+ screening L12). Signing from anything less is impossible by construction (`ARCHITECTURE.md` §6.4, §7 step 10).
- L11. **KMS-only signing, single path.** Attestation keys are generated in AWS KMS and never leave it; the receipt digest is sent to KMS for signing (`ARCHITECTURE.md` §6.4). `kms:Sign` on the attestation key is reachable **only** from the attest module — enforced by ArchUnit (package ban) *and* by IAM (only the Crypto Service role may call `kms:Sign`, `ARCHITECTURE.md` §8). Receipts embed the key id; verification public keys are published at a well-known URL. The attest logic must be structured to be portable into a Nitro Enclave later (§6.4 stretch) — no host-only assumptions in the sign path.
- L12. **Screening gates attestation, fail-closed.** Before signing, the counterparty address is screened (Chainalysis/TRM/Elliptic per Q2); an OFAC/sanctioned hit → `BLOCKED`, compliance queue, no signature (`ARCHITECTURE.md` §6.6). If the screening API is unreachable, attest **fails closed** (no signature) unless the author overrides via Q3.
- L13. **Secrets discipline.** No provider API key, DB credential, or KMS key ARN is committed. External Secrets Operator injects them; validated `@ConfigurationProperties` fail startup on missing/invalid config in non-local profiles (`ARCHITECTURE.md` §8; auth `target-design.md` §16).
- L14. **Sidecars are translation-only.** Any TS sidecar observes and translates one chain into the adapter/quorum contract; it holds no quorum authority, no signing access, and no business state (`docs/service-languages.pdf` §3.2). The Java core treats sidecar output as one more provider answer subject to quorum.
- L15. **Module boundaries.** Package-by-feature under `com.themistra.crypto`; no feature module imports another feature module's entity. Shared plumbing lives in `common`. Enforced by ArchUnit, mirroring the auth service.

## 4b. OPEN decisions — implementer/Claude MAY propose

- O1. **Provider set & quorum N per chain (Q1).** Propose the 3 launch providers per chain and whether N/threshold is fixed (3 / 2-of-3) or per-chain configurable. Recommend fixed 2-of-3 for launch. Behind a `ProviderSet` abstraction so a self-hosted node slots in later without code change.
- O2. **Watcher transport & concurrency (Q5).** Propose per-provider websocket-subscription vs polling and the polling-interval budget, and how thousands of watches map onto Java 21 virtual threads (`docs/service-languages.pdf` §3.1). Recommend one; proceed if low-risk.
- O3. **Cursor/checkpoint granularity.** Propose how per-chain cursor/checkpoint state is persisted (per-watch vs per-chain head) so reorg walk-back (L6) is correct and cheap. Recommend one.
- O4. **Screening client shape (Q2).** Behind a `ScreeningClient` interface with a fail-closed stub until the vendor is chosen; propose the adapter once Q2 is answered and pin it in §4c.
- O5. **Multi-replica watcher assignment.** Propose how watched addresses are partitioned across replicas (e.g. ShedLock-leased shards, Kafka-partition-affinity, or a Redis-backed assignment) so no address is double-driven and none is dropped on a pod restart. Recommend one; author approval required (operational blast radius).
- O6. **Anchor-write endpoint (Q6).** If the Payment Service routes its daily ledger anchor through this service, propose `POST /internal/v1/anchors`, the chain, and a **low-value operational key distinct from the attestation key**. Do not reuse the attestation key. Blocked until Q6 answered.

## 4c. VERBATIM artifacts — copy exactly, do not paraphrase

### Chain-adapter interface (`adapter/ChainAdapter.java`)

```java
public interface ChainAdapter {
    Chain chain();                                   // ETHEREUM | TRON
    TxResult getTx(String txHash);                   // provider-scoped; quorum compares across adapters
    TokenInfo getTokenInfo(String contractAddress);  // identity by address only (L7)
    Subscription subscribeAddress(String address, ObservationSink sink); // watcher layer
    FinalityStatus getFinalityStatus(String txHash); // evaluated against the per-chain FinalityPolicy (L4)
}
```
Each provider is one instance of a `ChainAdapter` (or a TS sidecar surfaced as one, L14). The `quorum` module fans a fact out across the provider adapters for a chain and compares.

### Per-chain finality policy (VERBATIM table — do not paraphrase into a constant)

```
ETHEREUM : block is at or below the beacon-chain `finalized` checkpoint. (NOT a block-count.)
TRON     : block is solidified (~19 confirmations toward the solidified block).
BASE/ARB : (later) L2 confirmed AND batch settled on L1 before attestation.
SOLANA   : (later) `finalized` commitment level.
```
Only ETHEREUM and TRON policy objects ship at launch.

### Quorum outcome enum

```java
public enum QuorumOutcome { AGREED, HELD, UNKNOWN_TOKEN }
// AGREED  -> at least 2-of-3 providers matched the fact (L1)
// HELD    -> providers disagreed; ops-alerted; no downstream event; manual resolution only (L2, L3)
// UNKNOWN_TOKEN -> contract address not on the signed allowlist (L7, R14)
```

### Internal API (authoritative; the Payment Service depends on this — `spec/payment-service/design.md` §4c mirrors it)

```
POST /internal/v1/watches      (scope internal.crypto:write)
  body: { invoiceUuid, chain, address, tokenContractAddress, expectedAmount, expiresAt }
  200:  { watchId, status: "REGISTERED" }
DELETE /internal/v1/watches/{watchId}   -> 204

POST /internal/v1/attest       (scope internal.crypto:write)
  body: { receiptDigestSha256: "<hex>", chain, txHash }
  precondition: txHash has AGREED quorum + met finality + passed screening (L10, L12)
  200:  { signature: "<base64>", kmsKeyId, signedAt, outcome: "SIGNED" }
  200:  { outcome: "BLOCKED", reason }        // sanctioned counterparty (R21)
  409:  problem+json                          // quorum/finality not met (R23)

GET  /.well-known/themistra-verification-keys    (public)
  200: { keys: [ { kid, kmsKeyId, alg, publicKeyPem } ] }   // (R24; alg per Q7)
```
`expectedAmount` and any monetary value are **decimal strings in token base units, never JSON numbers**.

### First Flyway migration `V1__chain_baseline.sql`

```sql
-- Crypto Service baseline (chain schema). Observation log is append-only and written
-- verbatim before any quorum decision (L3): service DB role has INSERT + SELECT only on it.

CREATE SCHEMA IF NOT EXISTS chain;
SET search_path TO chain;

CREATE TABLE watches (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    watch_id UUID NOT NULL UNIQUE,
    invoice_uuid UUID NOT NULL,
    chain VARCHAR(32) NOT NULL,
    address VARCHAR(128) NOT NULL,
    token_contract_address VARCHAR(128) NOT NULL,
    expected_amount NUMERIC(78, 0) NOT NULL,     -- token base units, exact
    status VARCHAR(16) NOT NULL DEFAULT 'REGISTERED',
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    unregistered_at TIMESTAMPTZ,
    CONSTRAINT chk_watch_status CHECK (status IN ('REGISTERED','UNREGISTERED','EXPIRED'))
);
CREATE INDEX idx_watches_chain_address ON watches(chain, address) WHERE status = 'REGISTERED';

-- Verbatim, append-only record of what each provider said (L3). Never UPDATE/DELETE.
CREATE TABLE observations (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    chain VARCHAR(32) NOT NULL,
    tx_hash VARCHAR(128) NOT NULL,
    provider VARCHAR(64) NOT NULL,               -- e.g. alchemy | quicknode | trongrid | sidecar:solana
    fact_type VARCHAR(32) NOT NULL,              -- existence | amount | token | confirmations | finality
    raw_response JSONB NOT NULL,                 -- verbatim provider payload
    s3_snapshot_key VARCHAR(256),                -- WORM snapshot pointer
    observed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_observations_tx ON observations(chain, tx_hash, fact_type);

-- The quorum decision per (tx, fact): what N providers said and the outcome.
CREATE TABLE quorum_decisions (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    chain VARCHAR(32) NOT NULL,
    tx_hash VARCHAR(128) NOT NULL,
    fact_type VARCHAR(32) NOT NULL,
    outcome VARCHAR(16) NOT NULL,                -- AGREED | HELD | UNKNOWN_TOKEN
    agreeing_count SMALLINT NOT NULL,
    provider_count SMALLINT NOT NULL,
    decided_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_quorum_tx_fact UNIQUE (chain, tx_hash, fact_type),
    CONSTRAINT chk_quorum_outcome CHECK (outcome IN ('AGREED','HELD','UNKNOWN_TOKEN'))
);

-- Per-provider health for chain.provider.degraded (R5).
CREATE TABLE provider_health (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    chain VARCHAR(32) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    healthy BOOLEAN NOT NULL DEFAULT TRUE,
    last_ok_at TIMESTAMPTZ,
    last_disagreement_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_provider_health UNIQUE (chain, provider)
);

-- Per-chain / per-watch reorg-safe cursor (L6). Granularity per O3.
CREATE TABLE chain_cursors (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    chain VARCHAR(32) NOT NULL,
    watch_id UUID,
    last_block BIGINT NOT NULL,
    last_finalized_block BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Signed, versioned canonical-token allowlist (L7). Seeded, never runtime-edited.
CREATE TABLE token_allowlist (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    chain VARCHAR(32) NOT NULL,
    contract_address VARCHAR(128) NOT NULL,
    symbol VARCHAR(32) NOT NULL,                 -- display only, never used for identity
    decimals SMALLINT NOT NULL,
    version INT NOT NULL,
    signature TEXT NOT NULL,                     -- signature over the allowlist entry/version
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_token_allowlist UNIQUE (chain, contract_address, version)
);

-- Compliance/OFAC screening results per counterparty (L12, R21).
CREATE TABLE screening_results (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    chain VARCHAR(32) NOT NULL,
    address VARCHAR(128) NOT NULL,
    tx_hash VARCHAR(128),
    outcome VARCHAR(16) NOT NULL,                -- CLEARED | BLOCKED | ERROR
    provider VARCHAR(64) NOT NULL,
    raw_response JSONB,
    screened_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_screening_outcome CHECK (outcome IN ('CLEARED','BLOCKED','ERROR'))
);
CREATE INDEX idx_screening_address ON screening_results(chain, address);

-- Attestation audit: every signature request and its outcome (append-only).
CREATE TABLE attestations (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    chain VARCHAR(32) NOT NULL,
    tx_hash VARCHAR(128) NOT NULL,
    receipt_digest CHAR(64) NOT NULL,
    outcome VARCHAR(16) NOT NULL,                -- SIGNED | BLOCKED | REFUSED
    kms_key_id VARCHAR(256),
    signed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_attest_outcome CHECK (outcome IN ('SIGNED','BLOCKED','REFUSED'))
);

-- Transactional outbox (mirrors libs/java/outbox).
CREATE TABLE outbox (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,       -- chain:txhash:eventtype (L5)
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,
    CONSTRAINT uq_outbox_idempotency UNIQUE (idempotency_key)
);
CREATE INDEX idx_outbox_unpublished ON outbox(created_at) WHERE published_at IS NULL;

CREATE TABLE shedlock (
    name VARCHAR(64) PRIMARY KEY,
    lock_until TIMESTAMPTZ NOT NULL,
    locked_at TIMESTAMPTZ NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);
```

### `EventTopics` aggregate-to-topic mapping (`EventTopics.java`)

```java
private static final Map<String, String> TOPIC_BY_AGGREGATE_TYPE = Map.of(
        "tx-seen", "chain.tx.seen",
        "tx-confirmed", "chain.tx.confirmed",
        "tx-finalized", "chain.tx.finalized",
        "tx-reorged", "chain.tx.reorged",
        "provider", "chain.provider.degraded"
);
```

### Published event schema — `chain.tx.finalized` (`contracts/events/chain/tx-finalized.v1.schema.json`)

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://checky.pro/contracts/events/chain/tx-finalized.v1.schema.json",
  "title": "chain.tx.finalized (schema_version 1)",
  "description": "Emitted via the outbox when a watched tx meets its per-chain finality policy under 2-of-3 quorum. Partition key = watchId. Idempotency key = chain:txHash:finalized.",
  "type": "object",
  "required": ["idempotencyKey", "watchId", "chain", "txHash", "tokenContractAddress", "amount", "occurredAt"],
  "properties": {
    "idempotencyKey": { "type": "string", "description": "chain:txHash:eventtype (L5)." },
    "watchId": { "type": "string", "format": "uuid" },
    "invoiceUuid": { "type": "string", "format": "uuid" },
    "chain": { "type": "string", "enum": ["ETHEREUM", "TRON"] },
    "txHash": { "type": "string" },
    "fromAddress": { "type": "string" },
    "toAddress": { "type": "string" },
    "tokenContractAddress": { "type": "string" },
    "amount": { "type": "string", "description": "Token base units as a decimal string (never a JSON number)." },
    "confirmations": { "type": "integer" },
    "addressPoisoningFlag": { "type": "boolean" },
    "occurredAt": { "type": "string", "format": "date-time" }
  },
  "additionalProperties": false
}
```
The other emitted events (`chain.tx.seen`, `chain.tx.confirmed`, `chain.tx.reorged`, `chain.provider.degraded`) share this envelope; author them under `contracts/events/chain/` with `watchId` as the partition key, `confirmations` on `seen`/`confirmed`, and no monetary JSON numbers.

## 5. Data model & schema changes

Greenfield: `V1__chain_baseline.sql` (§4c) is the whole baseline, in the `chain` schema. Aggregates:

- `Watch` owns a registered address-watch mapped to a Payment invoice.
- `Observation` is the verbatim, append-only provider-answer log (L3) — the defensible core of the platform.
- `QuorumDecision` records the per-fact outcome; `ProviderHealth` drives degraded events.
- `ChainCursor` owns reorg-safe per-chain/per-watch progress (L6).
- `TokenAllowlist` owns the signed canonical-token list (L7); `symbol` is display-only.
- `ScreeningResult` and `Attestation` own the compliance and signing audit trails.
- `Outbox` owns all `chain.*` publishing, keyed by the deterministic idempotency key.

Monetary/base-unit values are `NUMERIC`/`BigDecimal` and, on the wire, decimal strings. No floating-point money type is introduced.

## 6. Package & file map

New files under `services/crypto/src/main/java/com/themistra/crypto/`:

```
adapter/
├── ChainAdapter.java                     (interface — §4c)
├── Chain.java                            (enum: ETHEREUM, TRON)
├── eth/EthereumAdapter.java              (web3j)
├── tron/TronAdapter.java                 (TronGrid / java-tron gRPC)
├── model/{TxResult,TokenInfo,FinalityStatus,Subscription}.java
└── ObservationSink.java

provider/
├── ProviderSet.java                      (N adapters per chain — O1)
├── ProviderHealth.java / ProviderHealthRepository.java
└── ProviderDegradedPublisher.java        (chain.provider.degraded — R5)

quorum/
├── QuorumEvaluator.java                  (2-of-3, pure logic — L1, L2)
├── QuorumOutcome.java                    (enum — §4c)
├── QuorumDecision.java / QuorumDecisionRepository.java
└── HeldFactAlerter.java                  (ops alert on HELD — L2)

observation/
├── Observation.java / ObservationRepository.java   (append-only — L3)
└── ObservationSnapshotStore.java         (S3 WORM verbatim snapshot)

finality/
├── FinalityPolicy.java                   (interface)
├── EthereumFinalityPolicy.java           (beacon finalized checkpoint — L4)
└── TronFinalityPolicy.java               (solidified block — L4)

watch/
├── Watch.java / WatchRepository.java
├── WatchService.java                     (register/unregister — R18/R19)
├── Watcher.java                          (long-running, virtual threads — O2)
├── WatcherRegistry.java                  (multi-replica assignment — O5)
├── ChainCursor.java / ChainCursorRepository.java
└── WatchController.java                  (POST/DELETE /internal/v1/watches)

reorg/
└── ReorgDetector.java                    (cursor walk-back + chain.tx.reorged — L6, R11)

token/
├── TokenAllowlist.java / TokenAllowlistRepository.java   (signed, versioned — L7)
├── TokenValidator.java                   (address-only identity, UNKNOWN_TOKEN — R13/R14)
├── AddressValidator.java                 (EIP-55 / Base58Check — L8)
└── AddressPoisoningDetector.java         (prefix/suffix similarity — L9)

screening/
├── ScreeningClient.java                  (interface; fail-closed stub — L12, Q2)
└── ScreeningResult.java / ScreeningResultRepository.java

attest/
├── AttestController.java                 (POST /internal/v1/attest — L10)
├── AttestationService.java              (quorum+finality+screening gate → sign)
├── KmsSigner.java                        (SOLE kms:Sign caller — L11, R22)
├── Attestation.java / AttestationRepository.java
└── VerificationKeysController.java       (/.well-known/... — R24)

events/
├── OutboxPublisher.java
├── EventTopics.java                      (§4c mapping)
└── event/{TxSeen,TxConfirmed,TxFinalized,TxReorged,ProviderDegraded}Payload.java

common/
├── PublicEndpoints.java                  (actuator + /.well-known verification keys only)
├── ApiExceptionHandler.java             (RFC 9457)
├── ResourceServerConfig.java            (service-JWT, internal.crypto:write — R27)
└── config/*Properties.java              (validated @ConfigurationProperties — L13)
```

Tests mirror the layout under `src/test/java/com/themistra/crypto/`, using scripted fake `ChainAdapter`s. Contract files (new): `contracts/api/crypto-internal.yaml`, `contracts/events/chain/{tx-seen,tx-confirmed,tx-finalized,tx-reorged,provider-degraded}.v1.schema.json`. Any TS sidecar lives under `services/crypto/sidecars/<chain>/` (none at launch, L14).
