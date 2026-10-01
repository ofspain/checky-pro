-- T14: notification_app needs the full read/write lifecycle on delivery_retry - DeliveryOrchestrator
-- inserts the first retry row on a transient channel failure; RetryScheduler selects due rows,
-- updates one in place on a reschedule, and deletes one once resolved (sent, suppressed, a
-- permanent failure, or exhaustion). This is the first table in this module where notification_app
-- needs UPDATE/DELETE - every prior grant (V4-V8) was INSERT+SELECT or SELECT-only, since every
-- prior table was append-only or read-only from the app's own perspective. delivery_retry is a
-- genuine mutable scheduling queue, not a log.

GRANT SELECT, INSERT, UPDATE, DELETE ON notifications.delivery_retry TO notification_app;
