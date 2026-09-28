-- T08: PreferenceResolver only ever reads channel_preferences - no write API exists anywhere in
-- this spec (no PreferenceController is named in tasks.md through T20), so SELECT is the entire
-- grant. Unlike T05's contact_projection (a genuine upsert target), there is no legitimate writer
-- of this table in this codebase.

GRANT SELECT ON notifications.channel_preferences TO notification_app;
