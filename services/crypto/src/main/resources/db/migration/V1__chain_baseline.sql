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
