-- T02 AC2: the runtime application role, least-privilege by construction. Deliberately kept out of
-- V1__notifications_baseline.sql, which is a verbatim transcription of design.md §4c and carries no
-- grant/role statements of its own.
--
-- checky (the role V1 was created by, via the Flyway Maven plugin) owns every table in this
-- migration and is never used by the running application. notification_app owns nothing and
-- connects only as a grantee - in PostgreSQL, table owners bypass GRANT/REVOKE entirely, so the
-- restriction below only means anything because of that owner/grantee split (mirrors
-- crypto-service's own V2__crypto_app_role_and_grants.sql exactly).
--
-- No password is set here: this migration must be safe to run unmodified in any environment, and a
-- committed password would only ever be right for local dev. Real environments set
-- notification_app's password out-of-band (External Secrets Operator / IAM, L10). For local dev, run
-- once after this migration: `ALTER ROLE notification_app PASSWORD 'notification-app-local-only';`.
-- The database name is likewise never hardcoded - GRANT CONNECT targets current_database() via
-- dynamic SQL.
--
-- This task's own literal scope grants only delivery_log. The other six baseline tables
-- (contact_projection, channel_preferences, templates, processed_events, inapp_notifications,
-- delivery_retry) and shedlock each get their own grant migration in the task that first needs
-- runtime access to them, mirroring crypto-service's own incremental-grant pattern (one grant
-- migration per newly-consuming task) - not granted upfront here.

DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'notification_app') THEN
        CREATE ROLE notification_app LOGIN;
    END IF;
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO notification_app', current_database());
END
$$;

GRANT USAGE ON SCHEMA notifications TO notification_app;

-- Every table in notifications uses GENERATED ALWAYS AS IDENTITY; nextval() on the underlying
-- sequence requires USAGE, or an otherwise-permitted INSERT still fails.
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA notifications TO notification_app;

-- T02's own literal scope: INSERT + SELECT only, no UPDATE, no DELETE, ever.
GRANT INSERT, SELECT ON notifications.delivery_log TO notification_app;
