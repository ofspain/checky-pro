-- T05: notification_app needs to record/refresh the recipient-contact projection (O1, blocker for
-- R1/R2/R6). contact_projection is a genuine upsert target - email/display_name/updated_at are
-- revised over an account's lifetime, unlike processed_events' insert-once ledger - so UPDATE is
-- legitimately granted here, mirroring crypto-service's own V3__crypto_app_outbox_grant.sql (the
-- only other table in this codebase that needs UPDATE for a real reason). No DELETE - nothing in
-- this task's own scope removes a projection row.

GRANT INSERT, SELECT, UPDATE ON notifications.contact_projection TO notification_app;
