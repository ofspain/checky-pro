-- Refresh-token family tracking and reuse-detection archive (D-003).
--
-- V1's oauth2_authorization.family_id / device_label columns are superseded by these
-- dedicated tables (D-016): tracking lives outside SAS's own row format so this feature
-- never depends on undocumented internals of JdbcOAuth2AuthorizationService's storage
-- shape. Migrations are immutable once applied, so this is a follow-up migration rather
-- than an edit to V1.

ALTER TABLE oauth2_authorization DROP COLUMN family_id;
ALTER TABLE oauth2_authorization DROP COLUMN device_label;

CREATE TABLE refresh_token_family (
    family_id           UUID        PRIMARY KEY,
    authorization_id    VARCHAR(100) NOT NULL UNIQUE,  -- oauth2_authorization.id currently holding this family's live token
    principal_name      VARCHAR(200) NOT NULL,          -- account UUID as string
    device_label        VARCHAR(100),
    current_token_hash  CHAR(64)    NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    rotated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    revoked_at          TIMESTAMPTZ,
    revoked_reason      VARCHAR(64)
);
CREATE INDEX idx_refresh_token_family_principal ON refresh_token_family(principal_name);
CREATE INDEX idx_refresh_token_family_current_hash ON refresh_token_family(current_token_hash)
    WHERE revoked_at IS NULL;

-- Every hash a family has ever presented as "current" — once superseded by rotation, the old
-- hash moves here. A presented refresh token whose hash matches an archive row (rather than
-- the family's current_token_hash) is a replay of an already-rotated token.
CREATE TABLE refresh_token_archive (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    family_id           UUID        NOT NULL REFERENCES refresh_token_family(family_id) ON DELETE CASCADE,
    token_hash          CHAR(64)    NOT NULL,
    superseded_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_token_archive_hash ON refresh_token_archive(token_hash);
