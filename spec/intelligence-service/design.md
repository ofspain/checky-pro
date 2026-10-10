# 4. Design — how to build it

## 4a. LOCKED decisions — implement exactly, do NOT deviate

- L1. **Service scope: evidence platform only, not dispute resolution.** This service interprets,
  scores, and correlates evidence (new_features.md Phase 2). It decides no outcomes and owns no
  dispute workflow. Dispute Service (ARCHITECTURE.md §9, new_features.md Phase 3) is a separate,
  future service that consumes this service's own published events — not a module here. Confirmed
  by the author.
- L2. **AI evidence interpretation uses an external vision-capable LLM, not an in-house model or
  plain OCR.** Confirmed by the author: structured-field extraction (merchant, wallet, token,
  amount, date, transaction reference, chain, status) is submitted to Claude's own vision
  capability via the Messages API (default model: `claude-sonnet-5-5`, configurable — see §4c for
  the client interface). No custom-trained model, no regex/OCR-only extraction.
- L3. **Evidence integrity analysis is in-house only — no third-party forensics API, and no
  pixel-level editing-detection/ELA.** Confirmed by the author. Scope is exactly: EXIF/metadata
  extraction, perceptual-hash near-duplicate detection, and metadata-absence flagging (R15–R19).
  A true editing-detection capability is explicitly deferred — adding one later is itself a new,
  LOCKED decision in a follow-up task's own spec, not an incremental extension of this one.
- L4. **Evidence anchors to `<chain, transactionHash, uploadingAccountUuid>` — never to an
  invoice.** Confirmed by the author: payment-service's own invoice concept does not exist yet,
  and this service's scope must never depend on it. A future Dispute Service may join evidence to
  an invoice by whatever correlation key payment-service eventually exposes; that join lives
  there, not here.
- L5. **Evidence storage is S3 with Object Lock, compliance mode (WORM).** Confirmed by the author
  — matches `ARCHITECTURE.md` §2/§9's own already-stated plan, the same pattern already used for
  receipts. A stored evidence file is never deleted or overwritten (R5).
- L6. **On-chain facts always win over AI-extracted facts.** Where an extracted field (R11)
  disagrees with an on-chain fact collected from crypto-service's own quorum-checked lookup (R7)
  for the same transaction hash, the on-chain fact is the authoritative value in every API response
  (R21/R22) — never silently reconciled in either direction, never averaged, never "most recent
  wins."
- L7. **Every extracted field is returned with its model/vendor identifier, model version, and
  confidence — never as a bare value.** (R12/R14). A caller cannot mistake an extraction for a
  verified fact from the response shape alone; this is enforced at the DTO level (no field exists
  on the wire without its sibling confidence/model fields).
- L8. **Idempotency.** Each poll of crypto-service's on-demand lookup endpoint (R7, design §4c) is
  recorded at most once per `<evidenceItemId, eventType>` pair (`uq_collected_facts_item_type`,
  §4c) — a repeated poll that returns the same terminal outcome never creates a duplicate row. An
  evidence upload is deduped by `<accountUuid, transactionHash, sha256Digest>` (R2) — a
  byte-identical re-upload for the same account and hash returns the existing item, never a
  duplicate row.
- L9. **Evidence files are never logged, never returned as raw bytes via any API, and never
  attached to an error response.** (agents.md, R4). Only derived metadata and analysis results are
  ever returned.
- L10. **Module boundaries.** Package-by-feature under `com.themistra.intelligence`; no feature
  module imports another feature module's entity. Shared plumbing lives in `common`. Enforced by
  ArchUnit, mirroring every other Themistra service.
- L11. **Access control is self-only for this task.** (R24). A caller may only read or write their
  own uploads and collected facts. A broader, role-based (e.g. ADMIN/reviewer) read path is
  explicitly out of this task's scope (§2) — Dispute Service's own future reviewer workflow is the
  natural place for it, not a retrofit here.
- L12. **The vision-API key is read by exactly one class.** Mirrors crypto-service's own
  KMS-isolation precedent (L11 there): the external vision client wrapper is the only class in
  this service holding the API key; no other class constructs an HTTP call to the vendor directly.
  Enforced by ArchUnit (package-scoped — only `intelligence.extraction.VisionExtractionClient` may
  reference the HTTP client configured with the key).

## 4b. OPEN decisions — implementer/Claude MAY propose

- O1. **Service client credentials for the crypto-service lookup call.** Propose the exact
  auth-service client registration (client id, `internal.crypto:write` scope grant) this service
  uses to call Crypto Service's new on-demand lookup endpoint (task 30) — mirrors an existing
  service-to-service client (`RegisteredClientSeeder`'s `serviceClient` pattern). Recommend one;
  low-risk, proceed.
- O2. **Perceptual-hash algorithm and library.** Propose pHash vs. dHash vs. aHash and a concrete
  Java library (pure-Java implementation preferred over a native dependency, for the same
  portability reason crypto-service's own sidecars stay translation-only). Recommend one.
- O3. **Near-duplicate threshold (Hamming distance cutoff) for R17.** Propose a starting value with
  the reasoning (e.g. a widely-cited pHash near-duplicate threshold); flag it as tunable once real
  evidence volume exists, not a one-time guess treated as final.
- O4. **Max evidence file size and allowed content types (R3).** Propose starting values (e.g.
  10 MB; `image/png`, `image/jpeg`, `application/pdf`). Recommend one.
- O5. **EXIF/metadata extraction library (R15).** Propose a concrete Java library (e.g.
  `metadata-extractor` or Apache Commons Imaging). Recommend one.
- O6. **Polling backoff schedule and timeout for R6-R10.** Propose the poll interval/backoff and
  the maximum time this service keeps polling crypto-service's lookup endpoint for a hash before
  giving up and recording a terminal `TIMED_OUT` collected-fact (distinct from `HELD` — a timeout
  means we never got a usable answer in time, not that providers disagreed). Recommend one.
- O7. **Published event(s) for a future Dispute Service.** This spec defines
  `intelligence.evidence.correlation_updated` (§4c) as the one event fired whenever an
  `EvidenceGraph` gains a new item or finding, so Dispute Service can subscribe later without this
  service ever depending on it (ARCHITECTURE.md §1.5's own "new consumers of existing events, not
  rewrites" principle). Propose the exact payload shape if the one in §4c needs adjustment once a
  real consumer exists; until then it is a best-effort forward design, not load-bearing.
- O8. **Retry policy for a failed vision-API call (R13).** No synchronous retry is allowed
  (locked, R13) — propose whether a later async retry job is worth adding now or deferred until a
  real failure rate is observed. Recommend deferring; log the decision either way.

## 4c. VERBATIM artifacts — copy exactly, do not paraphrase

### External vision client interface (`extraction/VisionExtractionClient.java`)

```java
public interface VisionExtractionClient {

    /**
     * Submits one evidence file for structured-field extraction. Never called with the
     * caller's own JWT or any session state — this is a pure byte-in, result-out boundary,
     * and the only class in this service permitted to hold the vision-API key (L12).
     */
    ExtractionOutcome extract(byte[] fileBytes, String contentType);

    record ExtractionOutcome(
            boolean success,
            String failureReason,            // null if success
            String modelIdentifier,          // e.g. "claude-sonnet-5-5", always set on success
            String modelVersion,              // vendor-reported version/snapshot id, if any
            Map<String, ExtractedField> fields // empty map if success but nothing extracted
    ) {}

    record ExtractedField(String value, Double confidence /* nullable */) {}
}
```

### Crypto Service's new on-demand lookup endpoint (R6-R10; `spec/crypto-service` task 30, L16 — **not yet built**)

**Real finding during this spec's own authoring, not assumed:** crypto-service's existing
`registerWatch` only watches an address prospectively for a *future* matching payment — no
tx-hash input, no backfill/lookback (confirmed directly against `WatchService.java`/
`ChainCursor.java`). It cannot resolve a hash the caller already has from a transaction that
already happened. Decided directly with the product owner: add a small, new, read-only endpoint
to crypto-service reusing its already-existing `ChainAdapter.getTx` + `QuorumEvaluator` — no new
verification logic, just a new on-demand trigger onto logic that already exists. Recorded as
`spec/crypto-service/requirements.md` R29, `design.md` L16, `tasks.md` task 30 — **specced, not
yet implemented.** This service's own R6-R10 depend on that task landing first.

```
GET /internal/v1/transactions/{chain}/{txHash}   (scope internal.crypto:write)
  200: { chain, txHash, existence: "AGREED"|"HELD", confirmations, tokenContractAddress,
         fromAddress, toAddress, amount, outcome: "AGREED"|"HELD"|"UNKNOWN_TOKEN" }
  404: problem+json   // all 3 providers agree the hash does not exist
```

This service calls it by **polling**, not by consuming `chain.tx.*` events, for exactly this
use case — a one-off, user-submitted hash was never the subject of a persistent watch, so there is
no watch-scoped event stream to subscribe to. Poll on a backoff schedule (O6) until the response's
`outcome` reaches a terminal state (an agreed, sufficiently-confirmed result, or a configured
timeout) and record each poll's raw response as a `collected_facts` row (§4c, `event_type` values
become `POLLED_SEEN`/`POLLED_CONFIRMED`/`POLLED_FINALIZED`/`POLLED_HELD`, not the `chain.tx.*`
names — this service never fabricates an event type crypto-service didn't itself report).

### First Flyway migration `V1__intelligence_baseline.sql`

```sql
CREATE SCHEMA IF NOT EXISTS intelligence;
SET search_path TO intelligence;

-- ===== Evidence =====

CREATE TABLE evidence_items (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    evidence_uuid       UUID        NOT NULL UNIQUE,
    account_uuid        UUID        NOT NULL,
    chain               VARCHAR(16) NOT NULL,                   -- ETHEREUM | TRON (matches crypto's own enum)
    tx_hash             VARCHAR(128) NOT NULL,
    kind                VARCHAR(16) NOT NULL,                   -- UPLOADED | AUTO_COLLECTED
    content_type        VARCHAR(64),                            -- null for AUTO_COLLECTED
    file_size_bytes      BIGINT,                                 -- null for AUTO_COLLECTED
    sha256_digest       CHAR(64),                                -- null for AUTO_COLLECTED
    s3_object_key       VARCHAR(512),                            -- null for AUTO_COLLECTED
    polling_started_at  TIMESTAMPTZ,                             -- set for AUTO_COLLECTED (R6)
    polling_stopped_at  TIMESTAMPTZ,                             -- set once a terminal outcome is reached (O6)
    uploaded_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_evidence_account_hash_digest UNIQUE (account_uuid, tx_hash, sha256_digest)
);
CREATE INDEX idx_evidence_items_tx_hash ON evidence_items(chain, tx_hash);
CREATE INDEX idx_evidence_items_account ON evidence_items(account_uuid);

CREATE TABLE collected_facts (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    evidence_item_id    BIGINT      NOT NULL REFERENCES evidence_items(id) ON DELETE CASCADE,
    event_type          VARCHAR(16) NOT NULL,                   -- POLLED_SEEN | POLLED_CONFIRMED | POLLED_FINALIZED | POLLED_HELD | TIMED_OUT
    payload             JSONB       NOT NULL,                   -- the real GET /internal/v1/transactions/{chain}/{txHash} response body, verbatim
    invalidated_at      TIMESTAMPTZ,                             -- set if a later poll shows the earlier result was reorg-invalidated (R8)
    occurred_at         TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_collected_facts_item_type UNIQUE (evidence_item_id, event_type)
);

CREATE TABLE extraction_results (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    evidence_item_id    BIGINT      NOT NULL REFERENCES evidence_items(id) ON DELETE CASCADE,
    status              VARCHAR(16) NOT NULL,                   -- SUCCESS | FAILED
    failure_reason      VARCHAR(255),
    model_identifier    VARCHAR(64),
    model_version       VARCHAR(64),
    fields              JSONB,                                   -- {fieldName: {value, confidence}}
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE integrity_findings (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    evidence_item_id    BIGINT      NOT NULL REFERENCES evidence_items(id) ON DELETE CASCADE,
    finding_type        VARCHAR(32) NOT NULL,                   -- DUPLICATE_SUSPECTED | METADATA_STRIPPED
    related_evidence_item_id BIGINT REFERENCES evidence_items(id), -- set for DUPLICATE_SUSPECTED
    detail              JSONB,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE evidence_graphs (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    chain               VARCHAR(16) NOT NULL,
    tx_hash             VARCHAR(128) NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_evidence_graphs_chain_hash UNIQUE (chain, tx_hash)
);

CREATE TABLE correlation_findings (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    evidence_graph_id   BIGINT      NOT NULL REFERENCES evidence_graphs(id) ON DELETE CASCADE,
    finding_type        VARCHAR(32) NOT NULL,                   -- FIELD_DISAGREEMENT
    detail              JSONB       NOT NULL,                   -- {field, extractedValue, onChainValue, evidenceItemId}
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Note: no `processed_event_keys` table — this service consumes no Kafka topic at launch
-- (agents.md "Events & messaging"; registerWatch-era design would have needed one for
-- chain.tx.* consumption, but the polling design has no event stream to dedupe against here.
-- Polling's own idempotency is `uq_collected_facts_item_type` above, R9/L8). Add one in a future
-- migration if this service ever becomes a Kafka consumer.

-- ===== Outbox (published intelligence.evidence.* events) =====

CREATE TABLE outbox (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    aggregate_type      VARCHAR(32) NOT NULL,
    aggregate_id        VARCHAR(128) NOT NULL,
    event_type          VARCHAR(64) NOT NULL,
    idempotency_key     VARCHAR(255) NOT NULL UNIQUE,
    payload             JSONB       NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at        TIMESTAMPTZ
);
```

### Published event schema — `intelligence.evidence.correlation_updated` (O7, best-effort until a real consumer exists)

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://checky.pro/contracts/events/intelligence/correlation-updated.v1.schema.json",
  "title": "intelligence.evidence.correlation_updated (schema_version 1)",
  "description": "Emitted via the outbox whenever an EvidenceGraph gains a new item or finding. Partition key = chain:txHash. Idempotency key = chain:txHash:correlation_updated:{evidenceGraphUpdatedAt}.",
  "type": "object",
  "required": ["idempotencyKey", "chain", "txHash", "occurredAt"],
  "properties": {
    "idempotencyKey": { "type": "string" },
    "chain": { "type": "string", "enum": ["ETHEREUM", "TRON"] },
    "txHash": { "type": "string" },
    "evidenceItemCount": { "type": "integer" },
    "hasDisagreement": { "type": "boolean" },
    "occurredAt": { "type": "string", "format": "date-time" }
  },
  "additionalProperties": false
}
```

## 5. Data model & schema changes

See `V1__intelligence_baseline.sql` above (§4c) — the first migration for a brand-new service, no
prior schema to migrate from. Seven tables: `evidence_items`, `collected_facts`,
`extraction_results`, `integrity_findings`, `evidence_graphs`, `correlation_findings`, and
`outbox` (publisher-side, mirrors every other Themistra service's identical pattern). No
consumer-side idempotency table — this service consumes no Kafka topic at launch (agents.md); its
own idempotency is `uq_collected_facts_item_type` (R9/L8) and `uq_evidence_account_hash_digest`
(R2). All under the `intelligence` schema — no cross-schema query, ever (agents.md).

## 6. Package & file map

```
com.themistra.intelligence/
├── evidence/           EvidenceItem, EvidenceItemRepository, EvidenceService, EvidenceController,
│                        UploadEvidenceRequest, EvidenceItemResponse
├── collection/          CollectedFact, CollectedFactRepository, AutoCollectionService,
│                        TransactionLookupClient (wraps crypto-service's new on-demand lookup
│                        endpoint, §4c — R6-R10), a scheduled/async poller (O6)
├── extraction/          ExtractionResult, ExtractionResultRepository, ExtractionService,
│                        VisionExtractionClient (interface, §4c), ClaudeVisionExtractionClient
│                        (the one implementation holding the API key, L12)
├── integrity/           IntegrityFinding, IntegrityFindingRepository, IntegrityAnalysisService,
│                        PerceptualHasher, ExifReader
├── correlation/         EvidenceGraph, EvidenceGraphRepository, CorrelationFinding,
│                        CorrelationFindingRepository, CorrelationService, OutboxRelay
├── audit/               mirrors every other service's existing audit module shape
└── common/              ProblemTypes, PublicEndpoints, Hashing, ClockConfig, ResourceServerConfig
```
