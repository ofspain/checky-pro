-- T04: notification_app needs to record and read processed event keys for idempotency (L1).
-- processed_events is append-only: a key is never revised or deleted once recorded, so only
-- INSERT + SELECT are granted - mirrors delivery_log's own T02 grant philosophy exactly.

GRANT INSERT, SELECT ON notifications.processed_events TO notification_app;
