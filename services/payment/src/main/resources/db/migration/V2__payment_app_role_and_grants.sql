-- T02 AC2-AC8: the runtime application role, least-privilege by construction. Deliberately kept
-- out of V1__payments_baseline.sql, which is a verbatim transcription of design.md §4c and carries
-- no grant/role statements of its own (mirrors crypto-service's/notification-service's own
-- identical V1/V2 split).
--
-- checky (the role V1 was created by, via the Flyway Maven plugin) owns every table in this
-- migration and is never used by the running application. payment_app owns nothing and connects
-- only as a grantee — in PostgreSQL, table owners bypass GRANT/REVOKE entirely, so the restriction
-- below only means anything because of that owner/grantee split (AC7).
--
-- No password is set here: this migration must be safe to run unmodified in any environment, and
-- a committed password would only ever be right for local dev. Real environments set
-- payment_app's password out-of-band (External Secrets Operator / IAM, L13), the same way every
-- other real credential in this service is handled. For local dev, run once after this migration:
-- `ALTER ROLE payment_app PASSWORD 'payment-app-local-only';` (see services/payment/README.md).
-- The database name is likewise never hardcoded — GRANT CONNECT targets current_database() via
-- dynamic SQL, so this migration is not tied to "checky".

DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'payment_app') THEN
        CREATE ROLE payment_app LOGIN;
    END IF;
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO payment_app', current_database());
END
$$;

GRANT USAGE ON SCHEMA payments TO payment_app;

-- Every table in payments uses GENERATED ALWAYS AS IDENTITY; nextval() on the underlying sequence
-- requires USAGE, or an otherwise-permitted INSERT still fails (AC4).
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA payments TO payment_app;

-- AC3's own two named tables: INSERT + SELECT only, no UPDATE, no DELETE, ever (L7). No grant is
-- made here on invoices, payments, receipts, ledger_anchors, processed_events, outbox, or
-- shedlock (AC5) - each of those gets its own grant in whichever later task first needs it
-- (least-privilege-by-construction, mirroring crypto-service's own V2-V9 incremental pattern).
-- payment_app is granted no schema-level CREATE privilege anywhere above, so it cannot create or
-- drop tables in payments regardless (AC8).
GRANT INSERT, SELECT ON payments.ledger_entries, payments.verification_records TO payment_app;
