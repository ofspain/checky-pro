-- T11: crypto_app needs INSERT/SELECT on token_allowlist so TokenAllowlistSeeder can idempotently
-- seed configured entries at startup, and TokenValidator can read them. V2 granted nothing on this
-- table (confirmed by rereading V2 in full - the same gap pattern V4 already closed for
-- provider_health). No UPDATE, no DELETE: an entry is inserted once per (chain, contract_address,
-- version) and never revised - a new version is a new row, matching the append-only shape already
-- established for observations/attestations/quorum_decisions (V2's own INSERT, SELECT-only grant).
--
-- No IF NOT EXISTS guard needed: plain GRANT is already idempotent in PostgreSQL (V2/V3/V4 precedent).
-- token_allowlist.id's identity sequence is already covered by V2's schema-wide
-- "GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA chain TO crypto_app" - no new sequence grant needed.

GRANT INSERT, SELECT ON chain.token_allowlist TO crypto_app;
