-- T11: payment_app needs to record and read processed event keys for idempotency (L5/R13).
-- processed_events is append-only: a key is never revised or deleted once recorded, so only
-- INSERT + SELECT are granted - mirrors notification-service's own identical, already-proven
-- V4__notification_app_processed_events_grant.sql precedent exactly.

GRANT INSERT, SELECT ON payments.processed_events TO payment_app;
