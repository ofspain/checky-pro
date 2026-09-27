-- Notification Service baseline (notifications schema). Delivery log is append-only and
-- dispute-grade (L3): the service DB role has INSERT + SELECT only on delivery_log.

CREATE SCHEMA IF NOT EXISTS notifications;
SET search_path TO notifications;

-- Projection of recipient contact info, fed by consumed auth events (O1/Q1). Not a live Auth read.
CREATE TABLE contact_projection (
    account_uuid UUID PRIMARY KEY,
    email CITEXT,
    display_name VARCHAR(200),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE channel_preferences (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_uuid UUID NOT NULL,
    category VARCHAR(16) NOT NULL,               -- SECURITY | PAYMENT | MARKETING
    channel VARCHAR(16) NOT NULL,                -- EMAIL | IN_APP | WEBHOOK | PUSH
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_pref UNIQUE (account_uuid, category, channel),
    CONSTRAINT chk_pref_category CHECK (category IN ('SECURITY','PAYMENT','MARKETING')),
    CONSTRAINT chk_pref_channel CHECK (channel IN ('EMAIL','IN_APP','WEBHOOK','PUSH'))
);

CREATE TABLE templates (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(64) NOT NULL,                   -- e.g. email.verify, receipt.issued
    channel VARCHAR(16) NOT NULL,
    version INT NOT NULL,
    subject VARCHAR(256),
    body TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_template UNIQUE (name, channel, version)
);

-- Idempotency ledger (L1): dedupe consumed events on their stable key.
CREATE TABLE processed_events (
    event_key VARCHAR(200) PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Dispute-grade, append-only delivery log (L3). Never UPDATE to erase a prior attempt.
CREATE TABLE delivery_log (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_uuid UUID,
    recipient VARCHAR(256),                       -- email address or in-app subject
    channel VARCHAR(16) NOT NULL,
    source_event_key VARCHAR(200) NOT NULL,
    template_name VARCHAR(64),
    template_version INT,
    attempt SMALLINT NOT NULL DEFAULT 1,
    outcome VARCHAR(16) NOT NULL,                 -- SENT | FAILED | SUPPRESSED | DEAD_LETTERED
    error_detail VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_delivery_outcome CHECK (outcome IN
        ('SENT','FAILED','SUPPRESSED','DEAD_LETTERED'))
);
CREATE INDEX idx_delivery_log_event ON delivery_log(source_event_key);
CREATE INDEX idx_delivery_log_account ON delivery_log(account_uuid, created_at);

-- In-app notification store (backs the SSE/websocket stream + unread read API).
CREATE TABLE inapp_notifications (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    notification_uuid UUID NOT NULL UNIQUE,
    account_uuid UUID NOT NULL,
    category VARCHAR(16) NOT NULL,
    title VARCHAR(256) NOT NULL,
    body TEXT NOT NULL,
    link VARCHAR(512),
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_inapp_unread ON inapp_notifications(account_uuid, created_at) WHERE read_at IS NULL;

-- Bounded-retry scheduling for transient failures (L7). Dead-lettering per O4.
CREATE TABLE delivery_retry (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_event_key VARCHAR(200) NOT NULL,
    account_uuid UUID,
    channel VARCHAR(16) NOT NULL,
    attempt SMALLINT NOT NULL,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_delivery_retry_due ON delivery_retry(next_attempt_at);

CREATE TABLE shedlock (
    name VARCHAR(64) PRIMARY KEY,
    lock_until TIMESTAMPTZ NOT NULL,
    locked_at TIMESTAMPTZ NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);
