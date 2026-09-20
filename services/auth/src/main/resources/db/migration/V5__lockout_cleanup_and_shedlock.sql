-- Cleanup support for lockout rows and scheduled job coordination.
-- No changes to existing tables; this migration is additive only.

CREATE INDEX IF NOT EXISTS idx_lockout_state_locked_until
    ON lockout_state(locked_until)
    WHERE locked_until IS NOT NULL;

-- ShedLock for multi-replica scheduled cleanup (refresh-token family cleanup
-- referenced in target-design.md §7 must not run concurrently across pods).
CREATE TABLE IF NOT EXISTS shedlock (
    name VARCHAR(64) PRIMARY KEY,
    lock_until TIMESTAMPTZ NOT NULL,
    locked_at TIMESTAMPTZ NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);
