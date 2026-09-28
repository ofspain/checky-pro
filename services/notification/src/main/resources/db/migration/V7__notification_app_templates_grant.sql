-- T09: TemplateRenderer only ever reads templates - "seeded/versioned, not runtime-edited" (L9),
-- no write API exists anywhere in this spec (no TemplateController is named in tasks.md through
-- T20), so SELECT is the entire grant. Mirrors T08's own V6__notification_app_channel_preferences_grant.sql
-- exactly - the second table in this module with a SELECT-only grant.

GRANT SELECT ON notifications.templates TO notification_app;
