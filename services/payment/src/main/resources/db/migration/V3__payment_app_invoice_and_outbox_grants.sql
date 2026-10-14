-- T06 (Phase 9, Kimi Phase 8 finding 1): payment_app's own first grant on invoices/outbox.
-- InvoiceService.create (T05/T06) is the first real caller that inserts into invoices;
-- OutboxPublisher (T04) and OutboxRelay (T04) have been able to compile and pass their own
-- mocked-repository unit tests since T04, but neither ever had a real grant - this is the
-- first task with a real end-to-end caller to surface the gap. Mirrors V2's own incremental-
-- grant convention: no UPDATE on invoices yet (no task in this service's own scope updates an
-- invoice row until task 8's expiry sweep / a later state-transition task).
--
-- outbox needs all three: OutboxPublisher INSERTs; OutboxRelay SELECTs unpublished rows and
-- UPDATEs published_at on successful delivery (T04's own OutboxRelay.relayOne).

GRANT INSERT, SELECT ON payments.invoices TO payment_app;
GRANT INSERT, SELECT, UPDATE ON payments.outbox TO payment_app;
