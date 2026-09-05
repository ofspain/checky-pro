# 4. Design — how to build it

## 4a. LOCKED decisions — implement exactly, do NOT deviate

- L1. **Verification state machine.** Payment states are exactly `CREATED → WATCHING → SEEN → CONFIRMING → FINALIZED → ATTESTED`, with the only permitted reversals being `CONFIRMING → SEEN` and `SEEN → WATCHING` on a reorg (`ARCHITECTURE.md` §3.3). No other transition may be added. Forward transitions are driven only by consumed `chain.tx.*` events (and the watch-ack for `CREATED → WATCHING`); `ATTESTED` is reached only via the receipt path (L2).
- L2. **A receipt is issued only from `FINALIZED`, never earlier.** This is the single most load-bearing rule in the service (`ARCHITECTURE.md` §3.3, §7 step 10). No code path signs or issues a receipt from `SEEN` or `CONFIRMING`. Attestation runs after `FINALIZED`; `ATTESTED` is set only after the signature is obtained, the receipt is stored in S3, and the ledger entry is appended, all durably.
- L3. **This service never touches a blockchain or KMS.** All chain facts arrive as Kafka events; the receipt signature is obtained by calling the Crypto Service `POST /attest` (§4c). The service holds no RPC endpoints, no chain client, and no signing key. An ArchUnit rule bans importing web3j, chain SDKs, and any AWS KMS signing client.
- L4. **Finality is not computed here.** The service reacts to `chain.tx.finalized` from the Crypto Service; it never decides finality from confirmation counts. Confirmation counts from `chain.tx.confirmed` are recorded for display/audit only.
- L5. **Idempotent, dedupe-on-key consumers.** Every consumed event is deduplicated on its deterministic key `chain:txhash:eventtype` (`ARCHITECTURE.md` §3.4) using a `processed_events` table with the key as PK, inside the same transaction that applies the effect. At-least-once delivery must never double-apply.
- L6. **Money is `NUMERIC`/`BigDecimal` only.** Amounts are stored `NUMERIC` and handled as `BigDecimal`; no `float`/`double`/`Double`/`Float` appears on any money-carrying field or DTO. Token amounts also carry `token_decimals` so base-unit and human amounts are both exact. ArchUnit forbids floating-point money types. Currency/token is identified by `<chain, contractAddress>`, never by symbol (`ARCHITECTURE.md` §6.3).
- L7. **Append-only hash-chain ledger.** `ledger_entries` is append-only — no `UPDATE`/`DELETE` ever (the service DB role has INSERT+SELECT only on it, mirroring the auth `auth_audit` pattern). Each entry stores `prev_hash` = the previous entry's `entry_hash`; `entry_hash` = SHA-256 over the canonical serialization of the entry payload concatenated with `prev_hash`. The genesis entry uses an all-zero `prev_hash` (`ARCHITECTURE.md` §6.5).
- L8. **Outbox for every publish.** `invoice.created`, `payment.seen`, `payment.finalized`, and `receipt.issued` are written to the outbox in the same transaction as the state change and relayed to Kafka (`ARCHITECTURE.md` §4 rule 1). No direct Kafka producer call from domain code.
- L9. **Receipts are WORM.** Signed receipts are written once to the S3 Object Lock bucket in **compliance mode**; there is no overwrite or delete path in application code (`ARCHITECTURE.md` §5). The object key embeds the `receiptUuid`; a second write to the same key is a bug, not an update.
- L10. **Zero trust at the edge.** Every non-internal endpoint requires a valid JWT validated as an OAuth2 resource server against the Auth Service JWKS (`ARCHITECTURE.md` §3.1). The service never assumes ingress authenticated the caller. Merchant-scoped reads are additionally filtered to the caller's account UUID (`sub`), returning `404` for cross-merchant access (R28).
- L11. **Reorg is a first-class transition, not an error.** A `chain.tx.reorged` event walks the state machine backward (L1). Because no receipt exists before `FINALIZED`, a reorg before finality never requires revoking a receipt. A reorg after `ATTESTED` is out of Phase 1 policy scope and MUST raise an ops alert and move the payment to `HELD` rather than silently rewriting an attested record — record it in §11 if it occurs.
- L12. **Module boundaries.** Package-by-feature under `com.themistra.payment`; no feature module imports another feature module's entity class. Shared plumbing lives in `common`. Enforced by ArchUnit, mirroring the auth service.
- L13. **Secrets discipline.** No secret, credential, DB password, or bucket/KMS identifier is committed. External Secrets Operator injects values; validated `@ConfigurationProperties` fail startup on missing/invalid config in non-local profiles (`ARCHITECTURE.md` §8; auth `target-design.md` §16).

## 4b. OPEN decisions — implementer/Claude MAY propose

- O1. **Amount-tolerance policy (Q1).** Decide how transferred amount is compared to invoice amount: exact-match-only vs a configurable tolerance band. Propose the comparison (on base units, using `token_decimals`) and the resulting `PAID`/`UNDERPAID`/`OVERPAID` invoice states. Default to exact match for stablecoins; **do not finalize until Q1 is answered.**
- O2. **Invoice expiry TTL and post-expiry finalization (Q3).** Propose a default validity window and the behaviour when a payment finalizes after expiry (attest anyway vs `HELD` for ops). Recommend one; proceed if low-risk.
- O3. **On-chain anchor mechanism (Q4).** The daily head anchor is a chain write; this service owns no chain access (L3). Propose routing the anchor through the Crypto Service (a new internal endpoint, e.g. `POST /anchors`) vs another mechanism, name the chain, and describe failure handling. **Blocker for R26 — do not implement until Q4 is answered.**
- O4. **Receipt generation (Q5).** Propose the canonical signed-JSON receipt shape and the PDF rendering approach (library, template). The **signed digest is over the canonical JSON**, so pin that shape before writing the attest call.
- O5. **Tax-export format (Q6).** Propose export formats (CSV and/or JSON) and the exact column set for tax-ready history; recommend defaults.
- O6. **Consumer topology.** Propose whether the four `chain.tx.*` topics are consumed by one consumer group with a type switch or one listener per event type, and the partition-key alignment (payments keyed by invoice/address vs chain event key) so ordering per payment is preserved. Recommend one; proceed if low-risk.

## 4c. VERBATIM artifacts — copy exactly, do not paraphrase

### Payment state enum (Java)

```java
public enum PaymentState {
    CREATED, WATCHING, SEEN, CONFIRMING, FINALIZED, ATTESTED, HELD
}
// Permitted transitions (and NO others):
//   CREATED    -> WATCHING            (watch acknowledged by Crypto Service)
//   WATCHING   -> SEEN                (chain.tx.seen)
//   SEEN       -> CONFIRMING          (chain.tx.confirmed)
//   CONFIRMING -> FINALIZED           (chain.tx.finalized)
//   SEEN       -> FINALIZED           (chain.tx.finalized arriving without a prior confirmed)
//   FINALIZED  -> ATTESTED            (receipt signed + stored + ledger-appended)
//   CONFIRMING -> SEEN                (chain.tx.reorged, reversal)
//   SEEN       -> WATCHING            (chain.tx.reorged before confirm, reversal)
//   any        -> HELD                (attest BLOCKED, or post-attest reorg — ops-alerted)
```

### Invoice state enum (Java)

```java
public enum InvoiceState {
    OPEN, PAID, UNDERPAID, OVERPAID, EXPIRED, HELD
}
```

### New configuration keys (add to `application.properties`)

```properties
# --- Crypto Service (internal REST) ---
themistra.payment.crypto.base-url=${CRYPTO_SERVICE_BASE_URL:http://crypto-service}
themistra.payment.crypto.attest-path=/internal/v1/attest
themistra.payment.crypto.watch-path=/internal/v1/watches
themistra.payment.crypto.connect-timeout-ms=2000
themistra.payment.crypto.read-timeout-ms=5000

# --- Invoice / expiry (defaults; confirm via Q1/Q3) ---
themistra.payment.invoice.default-ttl-minutes=60
themistra.payment.invoice.amount-tolerance-base-units=0

# --- Receipts / S3 WORM ---
themistra.payment.receipt.bucket=${RECEIPT_BUCKET:}
themistra.payment.receipt.kms-public-keys-url=${KMS_PUBLIC_KEYS_URL:}

# --- Ledger anchor (ShedLock) ---
themistra.payment.ledger.anchor.cron=0 3 * * *

# --- Invoice expiry sweep (ShedLock) ---
themistra.payment.invoice.expiry-sweep.cron=0 */5 * * *
```

### `EventTopics` aggregate-to-topic mapping (`EventTopics.java`)

```java
private static final Map<String, String> TOPIC_BY_AGGREGATE_TYPE = Map.of(
        "invoice", "payments.invoice.created",
        "payment-seen", "payments.payment.seen",
        "payment-finalized", "payments.payment.finalized",
        "receipt", "payments.receipt.issued"
);
```

### Consumed topics (owned by the Crypto Service — see `spec/crypto-service`)

```
chain.tx.seen       -> WATCHING -> SEEN
chain.tx.confirmed  -> SEEN     -> CONFIRMING (records confirmations)
chain.tx.finalized  -> */SEEN/CONFIRMING -> FINALIZED
chain.tx.reorged    -> reversal transitions (L11)
```
Every consumed event carries the deterministic idempotency key `chain:txhash:eventtype`.

### Crypto Service internal contract this service depends on (authoritative copy in `spec/crypto-service/design.md` §4c)

```
POST /internal/v1/watches
  body: { invoiceUuid, chain, address, tokenContractAddress, expectedAmount, expiresAt }
  200:  { watchId, status: "REGISTERED" }
DELETE /internal/v1/watches/{watchId}   -> 204

POST /internal/v1/attest
  body: { receiptDigestSha256: "<hex>", chain, txHash }
  200:  { signature: "<base64>", kmsKeyId, signedAt, outcome: "SIGNED" }
  200:  { outcome: "BLOCKED", reason }   // OFAC/compliance hit — NO receipt (R20)
```
If this contract changes, this service's `attestation` module and the `receipt` flow must be updated together. Cross-service calls are internal (service-to-service JWT, scope `internal.crypto:write`), not merchant-facing.

### First Flyway migration `V1__payments_baseline.sql`

```sql
-- Payment & Verification Service baseline (payments schema).
-- Money is NUMERIC only. Token identified by (chain, contract_address), never symbol.
-- ledger_entries is append-only: the service DB role is granted INSERT + SELECT only.

CREATE SCHEMA IF NOT EXISTS payments;
SET search_path TO payments;

CREATE TABLE invoices (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    invoice_uuid UUID NOT NULL UNIQUE,
    merchant_account_uuid UUID NOT NULL,
    chain VARCHAR(32) NOT NULL,
    token_contract_address VARCHAR(128) NOT NULL,
    token_decimals SMALLINT NOT NULL,
    amount NUMERIC(78, 0) NOT NULL,          -- expected amount in token base units
    pay_to_address VARCHAR(128) NOT NULL,
    reference VARCHAR(140),
    state VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    watch_id UUID,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_invoice_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_invoice_state CHECK (state IN
        ('OPEN','PAID','UNDERPAID','OVERPAID','EXPIRED','HELD'))
);
CREATE INDEX idx_invoices_merchant ON invoices(merchant_account_uuid);
CREATE INDEX idx_invoices_state_expiry ON invoices(state, expires_at);

CREATE TABLE payments (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payment_uuid UUID NOT NULL UNIQUE,
    invoice_id BIGINT NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
    chain VARCHAR(32) NOT NULL,
    tx_hash VARCHAR(128) NOT NULL,
    from_address VARCHAR(128),
    to_address VARCHAR(128) NOT NULL,
    token_contract_address VARCHAR(128) NOT NULL,
    amount NUMERIC(78, 0) NOT NULL,          -- observed amount in token base units
    confirmations INT NOT NULL DEFAULT 0,
    state VARCHAR(16) NOT NULL DEFAULT 'CREATED',
    amount_discrepancy VARCHAR(16),          -- NULL | 'UNDERPAID' | 'OVERPAID'
    address_poisoning_flag BOOLEAN NOT NULL DEFAULT FALSE,
    seen_at TIMESTAMPTZ,
    confirmed_at TIMESTAMPTZ,
    finalized_at TIMESTAMPTZ,
    reorged_at TIMESTAMPTZ,
    attested_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_payment_chain_tx UNIQUE (chain, tx_hash),
    CONSTRAINT chk_payment_state CHECK (state IN
        ('CREATED','WATCHING','SEEN','CONFIRMING','FINALIZED','ATTESTED','HELD'))
);
CREATE INDEX idx_payments_invoice ON payments(invoice_id);
CREATE INDEX idx_payments_state ON payments(state);

-- Append-only per-payment verification history (why each state moved).
CREATE TABLE verification_records (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payment_id BIGINT NOT NULL REFERENCES payments(id) ON DELETE CASCADE,
    from_state VARCHAR(16),
    to_state VARCHAR(16) NOT NULL,
    trigger_event_key VARCHAR(200),          -- chain:txhash:eventtype (NULL for internal)
    confirmations INT,
    detail JSONB,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_verification_records_payment ON verification_records(payment_id, occurred_at);

CREATE TABLE receipts (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    receipt_uuid UUID NOT NULL UNIQUE,
    payment_id BIGINT NOT NULL UNIQUE REFERENCES payments(id) ON DELETE RESTRICT,
    digest_sha256 CHAR(64) NOT NULL,
    signature TEXT NOT NULL,
    kms_key_id VARCHAR(256) NOT NULL,
    s3_bucket VARCHAR(128) NOT NULL,
    s3_key VARCHAR(256) NOT NULL,
    signed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Tamper-evident append-only hash chain (L7). No UPDATE/DELETE ever.
CREATE TABLE ledger_entries (
    seq BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    receipt_id BIGINT NOT NULL UNIQUE REFERENCES receipts(id) ON DELETE RESTRICT,
    prev_hash CHAR(64) NOT NULL,             -- SHA-256 of previous entry_hash; genesis = 64 zeros
    entry_hash CHAR(64) NOT NULL UNIQUE,     -- SHA-256(canonical_payload || prev_hash)
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Daily on-chain anchor of the ledger head (ARCHITECTURE.md §6.5). Mechanism per Q4.
CREATE TABLE ledger_anchors (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    head_entry_hash CHAR(64) NOT NULL,
    anchor_chain VARCHAR(32),
    anchor_tx_hash VARCHAR(128),
    anchored_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Consumer idempotency ledger (L5): dedupe chain.tx.* on deterministic key.
CREATE TABLE processed_events (
    event_key VARCHAR(200) PRIMARY KEY,      -- chain:txhash:eventtype
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Transactional outbox (mirrors libs/java/outbox).
CREATE TABLE outbox (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(128) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ
);
CREATE INDEX idx_outbox_unpublished ON outbox(created_at) WHERE published_at IS NULL;

-- ShedLock for multi-replica scheduled jobs (anchor + expiry sweep).
CREATE TABLE shedlock (
    name VARCHAR(64) PRIMARY KEY,
    lock_until TIMESTAMPTZ NOT NULL,
    locked_at TIMESTAMPTZ NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);
```

### Published event schema — `payments.receipt.issued` (`contracts/events/payments/receipt-issued.v1.schema.json`)

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://checky.pro/contracts/events/payments/receipt-issued.v1.schema.json",
  "title": "payments.receipt.issued (schema_version 1)",
  "description": "Published via the outbox when a payment reaches ATTESTED. Partition key = invoiceUuid.",
  "type": "object",
  "required": ["invoiceUuid", "paymentUuid", "receiptUuid", "chain", "txHash", "occurredAt"],
  "properties": {
    "invoiceUuid": { "type": "string", "format": "uuid" },
    "paymentUuid": { "type": "string", "format": "uuid" },
    "receiptUuid": { "type": "string", "format": "uuid" },
    "merchantAccountUuid": { "type": "string", "format": "uuid" },
    "chain": { "type": "string" },
    "txHash": { "type": "string" },
    "amount": { "type": "string", "description": "Token base units as a decimal string (never a JSON number)." },
    "tokenContractAddress": { "type": "string" },
    "kmsKeyId": { "type": "string" },
    "receiptUri": { "type": "string" },
    "occurredAt": { "type": "string", "format": "date-time" }
  },
  "additionalProperties": false
}
```

The other three published events (`payments.invoice.created`, `payments.payment.seen`, `payments.payment.finalized`) follow the same envelope shape; author them under `contracts/events/payments/` with `invoiceUuid` as the partition key and monetary amounts as **decimal strings, never JSON numbers**.

## 5. Data model & schema changes

Greenfield: `V1__payments_baseline.sql` (§4c) is the whole baseline. Aggregates:

- `Invoice` owns the merchant-facing intent: amount (base units, `NUMERIC(78,0)`), `token_decimals`, `<chain, tokenContractAddress>`, pay-to address, expiry, and lifecycle state.
- `Payment` owns the observed on-chain transaction and its verification `state`; one row per `<chain, tx_hash>`.
- `VerificationRecord` is the append-only per-payment transition log (including reorg reversals and ignored stale events).
- `Receipt` owns the signed artifact metadata; the signed object itself lives in S3 WORM.
- `LedgerEntry` owns the tamper-evident hash chain (append-only, L7); `LedgerAnchor` owns the daily on-chain head anchor.
- `ProcessedEvent` owns consumer idempotency; `Outbox` owns all cross-service messaging.

Money is `NUMERIC`/`BigDecimal` throughout (L6). No floating-point type is introduced anywhere in the schema or DTOs.

## 6. Package & file map

New files under `services/payment/src/main/java/com/themistra/payment/`:

```
invoice/
├── Invoice.java                          (entity)
├── InvoiceState.java                     (enum — §4c)
├── InvoiceRepository.java
├── InvoiceService.java                   (create, expire, mark paid/under/over)
├── InvoiceExpirySweepJob.java            (ShedLock)
├── dto/{CreateInvoiceRequest,InvoiceResponse}.java
├── event/InvoiceCreatedPayload.java
└── InvoiceController.java                (POST/GET /api/v1/invoices)

payment/
├── Payment.java                          (entity)
├── PaymentState.java                     (enum — §4c)
├── PaymentRepository.java
├── PaymentStateMachine.java              (pure logic, unit-testable — L1)
├── PaymentService.java                   (apply chain events, drive transitions)
└── dto/PaymentResponse.java

verification/
├── VerificationRecord.java               (entity, append-only)
├── VerificationRecordRepository.java
└── VerificationRecordService.java        (record transition + reversal + ignored)

consumer/
├── ChainEventConsumer.java               (idempotent listeners — L5)
├── ProcessedEvent.java / ProcessedEventRepository.java
└── dto/{ChainTxSeen,ChainTxConfirmed,ChainTxFinalized,ChainTxReorged}.java

receipt/
├── Receipt.java / ReceiptRepository.java
├── ReceiptService.java                   (issue-only-from-FINALIZED — L2)
├── ReceiptDigest.java                    (canonical JSON + SHA-256 — Q5)
├── ReceiptStore.java                     (S3 Object Lock WORM writer — L9)
├── ledger/
│   ├── LedgerEntry.java / LedgerEntryRepository.java
│   ├── LedgerService.java                (append + head advance — L7)
│   ├── LedgerVerifier.java               (recompute + report breaks — R22)
│   ├── LedgerAnchor.java / LedgerAnchorRepository.java
│   └── LedgerAnchorJob.java              (ShedLock — R26/Q4)
└── event/ReceiptIssuedPayload.java

attestation/
├── CryptoClient.java                     (RestClient: /attest, /watches — L3, §4c)
├── AttestRequest.java / AttestResponse.java
└── WatchRequest.java / WatchResponse.java

export/
├── TransactionExportService.java         (tax-ready history — Q6)
└── ExportController.java                 (GET /api/v1/exports/transactions)

events/
├── OutboxPublisher.java
└── EventTopics.java                      (§4c mapping)

common/
├── PublicEndpoints.java                  (only actuator health/info/prometheus)
├── ApiExceptionHandler.java             (RFC 9457)
├── ResourceServerConfig.java            (JWT validation vs Auth JWKS — L10)
└── config/*Properties.java              (validated @ConfigurationProperties — L13)
```

Tests mirror the layout under `src/test/java/com/themistra/payment/`. Contract files (new): `contracts/api/payments.yaml`, `contracts/events/payments/{invoice-created,payment-seen,payment-finalized,receipt-issued}.v1.schema.json`.
