-- T22's tax export filters on p.state = 'ATTESTED' AND p.finalized_at BETWEEN ? AND ? - neither
-- existing index (idx_payments_invoice on invoice_id, idx_payments_state on state alone) covers
-- this combination (Phase 3 Kimi Finding 9, accepted at Phase 4).
CREATE INDEX idx_payments_state_finalized ON payments.payments(state, finalized_at);
