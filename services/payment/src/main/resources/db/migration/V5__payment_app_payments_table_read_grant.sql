-- T08: read-only access to payments.payments for InvoiceExpirySweepJob's own in-flight-payment
-- check (a native query, since no Payment JPA entity exists yet - task 11's own scope). No
-- INSERT/UPDATE/DELETE - this task never writes to payments.payments.

GRANT SELECT ON payments.payments TO payment_app;
