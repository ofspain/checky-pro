-- Demo surface (demo/onchain-verification branch): one row per on-demand transaction verification
-- asked for from the public landing page. Append-only, like every other audit table in this schema:
-- payment_app is granted INSERT + SELECT and nothing else, so a verification that has been recorded
-- can never be edited into saying something the chain did not say.
--
-- The facts are nullable on purpose. crypto-service answers HELD when its three providers do not
-- agree, and a HELD answer deliberately carries NO facts (crypto L2) so no caller can act on a
-- disputed value. A row with outcome = 'HELD' and null facts is therefore a complete, correct
-- record of what was agreed: nothing.
SET search_path TO payments;

CREATE TABLE chain_verifications (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    verification_uuid UUID NOT NULL UNIQUE,
    chain VARCHAR(32) NOT NULL,
    tx_hash VARCHAR(128) NOT NULL,
    outcome VARCHAR(32) NOT NULL,
    existence VARCHAR(32),
    confirmations INT,
    token_contract_address VARCHAR(128),
    from_address VARCHAR(128),
    to_address VARCHAR(128),
    amount NUMERIC(78, 0),                   -- token base units, never a float
    requested_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_chain_verification_outcome CHECK (outcome IN
        ('AGREED','HELD','UNKNOWN_TOKEN','NOT_FOUND'))
);

-- The landing page shows the most recent verifications, newest first.
CREATE INDEX idx_chain_verifications_requested_at ON chain_verifications(requested_at DESC);
-- The same hash asked twice is two rows (each answer is a fact about a moment), so the lookup
-- by hash is an index, never a unique constraint.
CREATE INDEX idx_chain_verifications_tx_hash ON chain_verifications(chain, tx_hash);

GRANT INSERT, SELECT ON payments.chain_verifications TO payment_app;
