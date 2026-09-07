-- T15 AC7: watches/chain_cursors had no grant at all (Phase 0 finding) - the same class of gap T10
-- (provider_health, V4) and T11 (token_allowlist, V5) each closed. watches needs UPDATE (the
-- register/unregister transition, Phase 3 Finding 4's atomic conditional UPDATE); chain_cursors is
-- only ever inserted by this task (a placeholder row) and read, never updated by this task - a future
-- task (the watcher, task 16) will need its own UPDATE grant when it starts advancing the cursor for
-- real, not granted preemptively here (least-privilege-by-construction, T02).

GRANT INSERT, SELECT, UPDATE ON chain.watches TO crypto_app;
GRANT INSERT, SELECT ON chain.chain_cursors TO crypto_app;
