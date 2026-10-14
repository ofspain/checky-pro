-- T11: payment_app needs to create and repeatedly update Payment rows. chain.tx.seen creates one;
-- chain.tx.confirmed/chain.tx.finalized update it via PaymentRepository's own atomic, conditional
-- UPDATE queries (never a load-mutate-save - see Payment's own Javadoc for why). T08's own V5
-- granted SELECT-only, for the expiry sweep's own read-only in-flight-payment check; this widens
-- it to the full read-write set this task's own ChainEventConsumer needs. No DELETE - a Payment
-- row is never deleted once created.

GRANT INSERT, UPDATE ON payments.payments TO payment_app;
