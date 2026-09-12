-- T19: crypto_app needs INSERT/SELECT on screening_results so FailClosedScreeningClient (and, later,
-- the real vendor adapter behind ScreeningClient) can persist every screening attempt. V2 granted
-- nothing on this table - only observations, attestations, and quorum_decisions got INSERT, SELECT
-- there (confirmed by rereading V2 in full).
--
-- No UPDATE, no DELETE: a screening attempt is inserted once and never revised - a re-screen is a new
-- row, matching the append-only shape already established for token_allowlist (V5) and
-- observations/attestations/quorum_decisions (V2).
--
-- No IF NOT EXISTS guard needed: plain GRANT is already idempotent in PostgreSQL (V2/V4/V5 precedent).
-- screening_results.id's identity sequence is already covered by V2's schema-wide
-- "GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA chain TO crypto_app" - no new sequence grant needed.

GRANT INSERT, SELECT ON chain.screening_results TO crypto_app;
