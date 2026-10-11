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

-- Daily on-chain anchor of the ledger head (ARCHITECTURE.md §6.5).
-- DEFERRED for Phase 1/MVP (Q4, resolved 2026-10-11, O3) - kept in the baseline schema since it is
-- harmless and forward-provisioned, but no Phase 1 code path writes to this table. R26/task 21 are
-- not executed under this revision.
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
