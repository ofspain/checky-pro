-- T16: crypto_app needs UPDATE on chain_cursors (Watcher advances last_block forward - T15's own
-- V6 deliberately withheld this, anticipating exactly this task) and full DML on chain.shedlock
-- (WatcherRegistry's multi-replica assignment, via shedlock-provider-jdbc-template) - neither had
-- any grant at all until now (Phase 3 Findings 3 confirmed by direct inspection of every prior
-- migration). shedlock rows are acquire/release/expire churn owned entirely by the ShedLock
-- library itself, not application code, so DELETE is granted here unlike every other table in this
-- schema.

GRANT UPDATE ON chain.chain_cursors TO crypto_app;
GRANT INSERT, SELECT, UPDATE, DELETE ON chain.shedlock TO crypto_app;
