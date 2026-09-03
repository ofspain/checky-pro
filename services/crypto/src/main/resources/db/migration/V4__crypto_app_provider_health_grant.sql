-- T10: crypto_app needs read/write access to provider_health to track per-provider degradation.
-- V2 granted INSERT+SELECT-only on the three append-only observation-log tables and omitted
-- provider_health entirely (confirmed by rereading V2 in full - a gap in T02's own grant set, not a
-- deliberate exclusion this migration works around). provider_health is genuinely update-in-place
-- (one row per (chain, provider), UNIQUE (chain, provider), healthy/last_ok_at/last_disagreement_at
-- revised over time) - the same access pattern V3 already established for outbox, hence UPDATE here
-- too. No DELETE: a health row is never removed, only transitioned.
--
-- No IF NOT EXISTS guard needed: plain GRANT is already idempotent in PostgreSQL (V2/V3 precedent).
-- provider_health.id's identity sequence is already covered by V2's schema-wide
-- "GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA chain TO crypto_app" - no new sequence grant needed.

GRANT INSERT, SELECT, UPDATE ON chain.provider_health TO crypto_app;
