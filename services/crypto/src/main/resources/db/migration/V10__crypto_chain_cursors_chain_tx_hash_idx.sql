-- T21: supports AttestationService's new (chain, tx_hash) lookup (ChainCursorRepository
-- .findByChainAndTxHash) - chain_cursors has no index beyond its identity PK today (V1), so this
-- query would otherwise sequential-scan a table that grows with every registered watch.
CREATE INDEX idx_chain_cursors_chain_tx_hash ON chain.chain_cursors(chain, tx_hash);
