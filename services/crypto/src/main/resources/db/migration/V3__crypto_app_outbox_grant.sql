-- T04: crypto_app needs write access to outbox to publish events. V2 granted INSERT+SELECT-only on
-- the three append-only observation-log tables (observations, attestations, quorum_decisions);
-- outbox is a materially different access pattern - OutboxRelay marks a row published by setting
-- published_at on an already-persisted row, which JPA executes as an UPDATE, not a new INSERT.
--
-- No IF NOT EXISTS guard is needed here, unlike V2's CREATE ROLE block: plain GRANT is already
-- idempotent in PostgreSQL (re-granting an already-held privilege is a silent no-op, never an
-- error) - V2's own bare GRANT lines carry no such guard either, for the same reason.
--
-- outbox.id's underlying identity sequence is already covered by V2's schema-wide
-- "GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA chain TO crypto_app" - no new sequence grant
-- needed here.

GRANT INSERT, SELECT, UPDATE ON chain.outbox TO crypto_app;
