-- T02 AC3: the runtime application role, least-privilege by construction. Deliberately kept out
-- of V1__chain_baseline.sql, which is a verbatim transcription of design.md §4c and carries no
-- grant/role statements of its own.
--
-- checky (the role V1 was created by, via the Flyway Maven plugin) owns every table in this
-- migration and is never used by the running application. crypto_app owns nothing and connects
-- only as a grantee — in PostgreSQL, table owners bypass GRANT/REVOKE entirely, so the restriction
-- below only means anything because of that owner/grantee split.
--
-- No password is set here (T02 Phase 9 — Kimi Findings 1/2): this migration must be safe to run
-- unmodified in any environment, and a committed password would only ever be right for local dev.
-- Real environments set crypto_app's password out-of-band (External Secrets Operator / IAM, L13),
-- the same way every other real credential in this service is handled. For local dev, run once
-- after this migration: `ALTER ROLE crypto_app PASSWORD 'crypto-app-local-only';` (see
-- services/crypto/README.md). The database name is likewise never hardcoded — GRANT CONNECT
-- targets current_database() via dynamic SQL, so this migration is not tied to "checky".

DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'crypto_app') THEN
        CREATE ROLE crypto_app LOGIN;
    END IF;
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO crypto_app', current_database());
END
$$;

GRANT USAGE ON SCHEMA chain TO crypto_app;

-- Every table in chain uses GENERATED ALWAYS AS IDENTITY; nextval() on the underlying sequence
-- requires USAGE, or an otherwise-permitted INSERT still fails.
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA chain TO crypto_app;

-- AC3's own three named tables: INSERT + SELECT only, no UPDATE, no DELETE, ever.
GRANT INSERT, SELECT ON chain.observations, chain.attestations, chain.quorum_decisions TO crypto_app;
