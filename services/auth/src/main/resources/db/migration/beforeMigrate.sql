-- Flyway callback: runs before every migrate, ahead of V1, including at each application start.
-- It is not a versioned migration, so it never enters flyway_schema_history, and edits to it are
-- not checksum-validated; AuthCitextCallbackIntegrationTest pins what it does.
--
-- V1 runs CREATE EXTENSION IF NOT EXISTS citext and then uses CITEXT under the auth schema.
-- Postgres allows one copy of an extension per database. In a database shared with
-- notification-service, notification may already have put citext in its own schema; V1's install
-- is then skipped and fails later with "type citext does not exist". Each service runs in its
-- own database (open-issues sweep, 2026-10-20); this stops a shared one with a clear reason.
--
-- The check applies only before V1 has created auth.accounts. Once V1 has run, its columns hold
-- the type, wherever the extension lives: the local shared database, where citext was moved into
-- notifications by hand during notification T02, keeps starting.

DO $$
DECLARE
    installed_in name;
BEGIN
    SELECT n.nspname INTO installed_in
    FROM pg_extension e JOIN pg_namespace n ON n.oid = e.extnamespace
    WHERE e.extname = 'citext';
    IF installed_in IS NOT NULL AND installed_in <> 'auth' AND to_regclass('auth.accounts') IS NULL THEN
        RAISE EXCEPTION 'citext is installed in schema %, where auth-service migrations cannot see it. Run auth-service in its own database.', installed_in
            USING HINT = 'Flyway has already created an empty auth schema and history table; they can be dropped.';
    END IF;
END
$$;
