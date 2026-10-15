-- payment_app needs INSERT+SELECT on receipts for the first time this task (T19) - the first
-- real caller of the Receipt entity. ledger_entries already has this same grant (V2) since it
-- was scoped correctly from the start, even though nothing used it until now.
GRANT INSERT, SELECT ON payments.receipts TO payment_app;
