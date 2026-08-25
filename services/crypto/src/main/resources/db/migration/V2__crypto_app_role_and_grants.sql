-- T02 AC3: the runtime application role, least-privilege by construction. Deliberately kept out
-- of V1__chain_baseline.sql, which is a verbatim transcription of design.md §4c and carries no
-- grant/role statements of its own.
--
-- checky (the role V1 was created by, via the Flyway Maven plugin) owns every table in this
-- migration and is never used by the running application. crypto_app owns nothing and connects
-- only as a grantee — in PostgreSQL, table owners bypass GRANT/REVOKE entirely, so the restriction
-- below only means anything because of that owner/grantee split.

DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'crypto_app') THEN
        CREATE ROLE crypto_app LOGIN PASSWORD 'crypto-app-local-only';
    END IF;
END
$$;

GRANT CONNECT ON DATABASE checky TO crypto_app;
GRANT USAGE ON SCHEMA chain TO crypto_app;

-- Every table in chain uses GENERATED ALWAYS AS IDENTITY; nextval() on the underlying sequence
-- requires USAGE, or an otherwise-permitted INSERT still fails.
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA chain TO crypto_app;

-- AC3's own three named tables: INSERT + SELECT only, no UPDATE, no DELETE, ever.
GRANT INSERT, SELECT ON chain.observations, chain.attestations, chain.quorum_decisions TO crypto_app;
