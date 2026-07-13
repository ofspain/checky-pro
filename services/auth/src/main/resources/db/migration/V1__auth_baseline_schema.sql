-- Auth service baseline schema. DDL + reference data only — no functions, no procedures,
-- no business logic in the database (decision D-005).
-- Design: services/auth/docs/architecture/target-design.md §11.

CREATE EXTENSION IF NOT EXISTS citext;

-- ===== Accounts =====

CREATE TABLE accounts (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_uuid        UUID        NOT NULL UNIQUE,            -- external subject ('sub' claim); internal id never leaves the service
    email               CITEXT      NOT NULL UNIQUE,
    email_verified      BOOLEAN     NOT NULL DEFAULT FALSE,
    password_hash       VARCHAR(100),
    status              VARCHAR(32) NOT NULL DEFAULT 'PENDING_VERIFICATION',
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_account_status CHECK (status IN
        ('PENDING_VERIFICATION','ACTIVE','LOCKED','SUSPENDED','DELETED'))
);

-- ===== MFA =====

CREATE TABLE mfa_enrollments (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id          BIGINT      NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    type                VARCHAR(16) NOT NULL DEFAULT 'TOTP',    -- schema admits WEBAUTHN later
    secret_encrypted    BYTEA       NOT NULL,                   -- AES-GCM, KMS-enveloped data key
    confirmed_at        TIMESTAMPTZ,
    last_used_at        TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_mfa_account_type UNIQUE (account_id, type)
);

CREATE TABLE recovery_codes (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id          BIGINT      NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    code_hash           CHAR(64)    NOT NULL,                   -- SHA-256, single use
    used_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_recovery_codes_account ON recovery_codes(account_id);

-- ===== RBAC: roles + role templates (bundles) =====

CREATE TABLE roles (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name                VARCHAR(64) NOT NULL UNIQUE,
    description         VARCHAR(255)
);

CREATE TABLE role_templates (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name                VARCHAR(64) NOT NULL UNIQUE,
    description         VARCHAR(255)
);

CREATE TABLE role_template_roles (
    role_template_id    BIGINT NOT NULL REFERENCES role_templates(id) ON DELETE CASCADE,
    role_id             BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (role_template_id, role_id)
);

CREATE TABLE account_roles (
    account_id          BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    role_id             BIGINT NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    granted_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    granted_by          UUID,                                   -- actor account_uuid; NULL = system
    PRIMARY KEY (account_id, role_id)
);

CREATE TABLE account_role_templates (
    account_id          BIGINT NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    role_template_id    BIGINT NOT NULL REFERENCES role_templates(id) ON DELETE CASCADE,
    granted_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    granted_by          UUID,
    PRIMARY KEY (account_id, role_template_id)
);

-- ===== Merchant API keys =====

CREATE TABLE api_keys (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    key_uuid            UUID        NOT NULL UNIQUE,
    account_id          BIGINT      NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    prefix              VARCHAR(16) NOT NULL,                   -- ck_live_xxxx: lookup handle, non-secret
    key_hash            CHAR(64)    NOT NULL UNIQUE,            -- SHA-256 of full key; plaintext shown once
    name                VARCHAR(100) NOT NULL,
    scopes              TEXT[]      NOT NULL DEFAULT '{}',
    last_used_at        TIMESTAMPTZ,
    expires_at          TIMESTAMPTZ,
    revoked_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_api_keys_prefix ON api_keys(prefix);
CREATE INDEX idx_api_keys_account ON api_keys(account_id);

-- ===== Verification / reset tokens =====

CREATE TABLE verification_tokens (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_id          BIGINT      NOT NULL REFERENCES accounts(id) ON DELETE CASCADE,
    purpose             VARCHAR(32) NOT NULL,
    token_hash          CHAR(64)    NOT NULL UNIQUE,            -- single use, 30-min TTL enforced in code
    expires_at          TIMESTAMPTZ NOT NULL,
    used_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_token_purpose CHECK (purpose IN ('EMAIL_VERIFY','PASSWORD_RESET'))
);
CREATE INDEX idx_verification_tokens_account ON verification_tokens(account_id);

-- ===== Lockout =====

CREATE TABLE lockout_state (
    account_id          BIGINT      PRIMARY KEY REFERENCES accounts(id) ON DELETE CASCADE,
    failed_attempts     INT         NOT NULL DEFAULT 0,
    last_failed_at      TIMESTAMPTZ,
    locked_until        TIMESTAMPTZ,
    lock_count          INT         NOT NULL DEFAULT 0          -- drives exponential backoff
);

-- ===== Audit (append-only; see grants note at bottom) =====

CREATE TABLE auth_audit (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    occurred_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    event_type          VARCHAR(64) NOT NULL,
    outcome             VARCHAR(16) NOT NULL,
    account_id          BIGINT      REFERENCES accounts(id),
    actor_uuid          UUID,                                   -- who acted (may differ from account for admin ops)
    ip                  INET,
    user_agent_hash     CHAR(64),
    trace_id            VARCHAR(64),
    details             JSONB
);
CREATE INDEX idx_auth_audit_account_time ON auth_audit(account_id, occurred_at);
CREATE INDEX idx_auth_audit_type_time ON auth_audit(event_type, occurred_at);

-- ===== Spring Authorization Server persistence =====
-- Standard SAS tables plus our columns: token values stored HASHED by the customized
-- persistence layer (D-003); family_id/device_label support refresh-token families.

CREATE TABLE oauth2_registered_client (
    id                              VARCHAR(100)  PRIMARY KEY,
    client_id                       VARCHAR(100)  NOT NULL UNIQUE,
    client_id_issued_at             TIMESTAMPTZ   NOT NULL DEFAULT now(),
    client_secret                   VARCHAR(200),
    client_secret_expires_at        TIMESTAMPTZ,
    client_name                     VARCHAR(200)  NOT NULL,
    client_authentication_methods   VARCHAR(1000) NOT NULL,
    authorization_grant_types       VARCHAR(1000) NOT NULL,
    redirect_uris                   VARCHAR(1000),
    post_logout_redirect_uris       VARCHAR(1000),
    scopes                          VARCHAR(1000) NOT NULL,
    client_settings                 VARCHAR(2000) NOT NULL,
    token_settings                  VARCHAR(2000) NOT NULL
);

CREATE TABLE oauth2_authorization (
    id                              VARCHAR(100)  PRIMARY KEY,
    registered_client_id            VARCHAR(100)  NOT NULL,
    principal_name                  VARCHAR(200)  NOT NULL,
    authorization_grant_type        VARCHAR(100)  NOT NULL,
    authorized_scopes               VARCHAR(1000),
    attributes                      TEXT,
    state                           VARCHAR(500),
    authorization_code_value        TEXT,
    authorization_code_issued_at    TIMESTAMPTZ,
    authorization_code_expires_at   TIMESTAMPTZ,
    authorization_code_metadata     TEXT,
    access_token_value              TEXT,                       -- hashed
    access_token_issued_at          TIMESTAMPTZ,
    access_token_expires_at         TIMESTAMPTZ,
    access_token_metadata           TEXT,
    access_token_type               VARCHAR(100),
    access_token_scopes             VARCHAR(1000),
    refresh_token_value             TEXT,                       -- hashed
    refresh_token_issued_at         TIMESTAMPTZ,
    refresh_token_expires_at        TIMESTAMPTZ,
    refresh_token_metadata          TEXT,
    oidc_id_token_value             TEXT,
    oidc_id_token_issued_at         TIMESTAMPTZ,
    oidc_id_token_expires_at        TIMESTAMPTZ,
    oidc_id_token_metadata          TEXT,
    oidc_id_token_claims            TEXT,
    family_id                       UUID,                       -- refresh-token family (D-003)
    device_label                    VARCHAR(100)                -- user-facing session name
);
CREATE INDEX idx_oauth2_authorization_principal ON oauth2_authorization(principal_name);
CREATE INDEX idx_oauth2_authorization_family ON oauth2_authorization(family_id);

CREATE TABLE oauth2_authorization_consent (
    registered_client_id            VARCHAR(100) NOT NULL,
    principal_name                  VARCHAR(200) NOT NULL,
    authorities                     VARCHAR(1000) NOT NULL,
    PRIMARY KEY (registered_client_id, principal_name)
);

-- ===== Outbox (contract shared with libs/java/outbox) =====

CREATE TABLE outbox (
    id                  UUID          PRIMARY KEY,
    aggregate_type      VARCHAR(64)   NOT NULL,
    aggregate_id        VARCHAR(64)   NOT NULL,                 -- Kafka partition key
    event_type          VARCHAR(100)  NOT NULL,
    schema_version      INT           NOT NULL DEFAULT 1,
    payload             JSONB         NOT NULL,
    headers             JSONB,
    created_at          TIMESTAMPTZ   NOT NULL DEFAULT now(),
    published_at        TIMESTAMPTZ
);
CREATE INDEX idx_outbox_unpublished ON outbox(created_at) WHERE published_at IS NULL;

-- ===== Reference data =====

INSERT INTO roles (name, description) VALUES
    ('USER',       'Baseline authenticated user'),
    ('MERCHANT',   'Can create invoices and API keys; MFA mandatory'),
    ('ADMIN',      'Platform administration; MFA mandatory'),
    ('COMPLIANCE', 'Compliance review queue access');

-- NOTE (enforced by infra, not this migration, since app roles differ per environment):
-- the application's DB role receives INSERT + SELECT only on auth_audit — no UPDATE/DELETE —
-- per target-design §15. See infra/stacks data-stack grants.
