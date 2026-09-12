-- T17: durable, restart-safe storage for the transaction a watch has seen, needed to build the
-- chain.tx.finalized event payload (amount/fromAddress/toAddress) at finality time, potentially long
-- after the observations that first supplied them were correlated and pruned in memory (Watcher, T16).
-- tokenContractAddress is deliberately not duplicated here - it is already durable on watches.token_
-- contract_address. amount uses the same NUMERIC(78, 0) precision as watches.expected_amount (V1) -
-- token base units, exact, never floating point (agents.md).
--
-- No new grant: crypto_app's existing UPDATE on chain.chain_cursors (V7) already covers new columns
-- on the same table.

ALTER TABLE chain.chain_cursors
    ADD COLUMN tx_hash VARCHAR(128),
    ADD COLUMN amount NUMERIC(78, 0),
    ADD COLUMN from_address VARCHAR(128),
    ADD COLUMN to_address VARCHAR(128);
