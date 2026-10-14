-- T08 (Phase 11 finding, self-identified while resolving Kimi's Gap 1/2 suggestions): V3's own
-- comment said "no UPDATE on invoices yet ... until task 8's expiry sweep" - but V3 itself never
-- granted it, and no later migration did either. InvoiceRepository.updateStateIfCurrentState
-- (Phase 9's own atomic conditional UPDATE, the fix for the lost-update race) issues a real
-- `UPDATE payments.invoices SET state = ..., updated_at = ... WHERE ...` as payment_app - without
-- this grant it would fail with a real Postgres permission-denied error the first time it actually
-- ran against a live database. No test caught this until now because every existing InvoiceService
-- test mocks InvoiceRepository; SchemaBaselineMigrationIntegrationTest's own
-- grantedTablesHaveExactlyInsertAndSelectPrivilegesNoMore test was itself asserting invoices had
-- NO UPDATE grant, which was true and is now deliberately changed by this migration.

GRANT UPDATE ON payments.invoices TO payment_app;
