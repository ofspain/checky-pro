-- Widens api_keys.prefix from VARCHAR(16) to VARCHAR(32) to actually fit L7's public prefix
-- format (ck_live_ + a random 24-character alphanumeric suffix = 32 characters total). V1 sized
-- the column too narrowly for L7 as written; this widens it rather than shrinking L7's suffix
-- (human decision, task 24 Phase 2). Widening a VARCHAR is metadata-only in Postgres - no table
-- rewrite, no data loss, existing idx_api_keys_prefix index remains valid unchanged.

ALTER TABLE api_keys ALTER COLUMN prefix TYPE VARCHAR(32);
