-- Adds the device-authorization-grant columns Spring Authorization Server's
-- JdbcOAuth2AuthorizationService references unconditionally in every generated SQL statement
-- (SELECT/INSERT/UPDATE), regardless of which grant types are actually configured. V1's
-- oauth2_authorization table deliberately omitted them (this service doesn't support the Device
-- Authorization Grant) but that omission breaks every query against the table, not just the ones
-- device-grant flows would use — confirmed only now (T22), the first time anything has actually
-- driven /oauth2/authorize to completion against a real database. Additive only; no existing
-- column changed. Types/nullability match the library's bundled reference schema
-- (oauth2-authorization-schema.sql), translated blob->TEXT and timestamp->TIMESTAMPTZ exactly the
-- way V1 already did for every other column of the same two kinds.

ALTER TABLE oauth2_authorization
    ADD COLUMN IF NOT EXISTS user_code_value          TEXT,
    ADD COLUMN IF NOT EXISTS user_code_issued_at      TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS user_code_expires_at     TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS user_code_metadata       TEXT,
    ADD COLUMN IF NOT EXISTS device_code_value        TEXT,
    ADD COLUMN IF NOT EXISTS device_code_issued_at    TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS device_code_expires_at   TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS device_code_metadata     TEXT;
