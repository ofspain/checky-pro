-- Crypto service baseline schema. DDL + constraints only — no functions, no procedures,
-- no triggers, no business logic in the database (decision D-005).
-- Owns the `chain` schema per ARCHITECTURE §3.4.

-- ===== Watches =====
-- What the payment service has asked us to look for. Registration records intent; the watcher
-- layer acts on it. A watch always carries an expiry: watching costs provider calls for as long
-- as it lives, and an invoice nobody pays is the common case.

CREATE TABLE watches (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    watch_uuid          UUID          NOT NULL UNIQUE,            -- external identifier; internal id never leaves the service
    caller_reference    VARCHAR(128)  NOT NULL UNIQUE,            -- the caller's own id (invoice); the idempotency key
    chain_id            VARCHAR(64)   NOT NULL,                   -- namespaced, e.g. eip155:1
    recipient_address   VARCHAR(128)  NOT NULL,                   -- normalised for matching; checksummed form is a presentation concern
    token_address       VARCHAR(128)  NOT NULL,                   -- contract address; a token is never identified by symbol (§6.3)
    expected_amount     NUMERIC(78,0) NOT NULL,                   -- base units; 78 digits covers uint256 exactly
    expires_at          TIMESTAMPTZ   NOT NULL,
    status              VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    -- SATISFIED is admitted but unreachable until something can observe a payment. Admitting it
    -- now costs nothing and avoids a migration to add a value.
    CONSTRAINT chk_watch_status CHECK (status IN ('ACTIVE','EXPIRED','CANCELLED','SATISFIED')),
    CONSTRAINT chk_watch_amount_positive CHECK (expected_amount > 0)
);

-- The watcher layer's read path: active watches for a chain, matched by recipient.
CREATE INDEX idx_watches_active_by_chain
    ON watches (chain_id, recipient_address)
    WHERE status = 'ACTIVE';

-- Expiry is evaluated on read rather than by a sweeper, so this supports the time predicate.
CREATE INDEX idx_watches_expires_at ON watches (expires_at) WHERE status = 'ACTIVE';
