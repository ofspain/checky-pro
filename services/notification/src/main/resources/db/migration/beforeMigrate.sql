-- Flyway callback: runs before every migrate, ahead of V1. It is not a versioned migration, so
-- it never enters flyway_schema_history and V1 stays as it is.
--
-- V1 sets search_path TO notifications and then uses CITEXT, so the citext extension must live in
-- the notifications schema. Postgres allows one copy of an extension per database. In a database
-- shared with auth-service, auth's V1 has already installed citext into the auth schema, and
-- notification cannot install its own. notification-service therefore runs in its own database
-- (open-issues sweep, 2026-10-20), where this installs citext the first time.
--
-- citext is a trusted extension (Postgres 13+): the migrating role needs CREATE on the database,
-- not superuser.

CREATE SCHEMA IF NOT EXISTS notifications;
CREATE EXTENSION IF NOT EXISTS citext SCHEMA notifications;

-- IF NOT EXISTS skips the install when another service already put citext elsewhere. Stop here
-- with a clear reason, not later with "type citext does not exist".
DO $$
DECLARE
    installed_in name;
BEGIN
    SELECT n.nspname INTO installed_in
    FROM pg_extension e JOIN pg_namespace n ON n.oid = e.extnamespace
    WHERE e.extname = 'citext';
    IF installed_in IS DISTINCT FROM 'notifications' THEN
        RAISE EXCEPTION 'citext is installed in schema %, where notification-service migrations cannot see it. Run notification-service in its own database.', installed_in;
    END IF;
END
$$;
