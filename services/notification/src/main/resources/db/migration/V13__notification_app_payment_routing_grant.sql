-- Notification T07: notification_app records and reads payment routes. Insert-once
-- (INSERT ... ON CONFLICT DO NOTHING needs only INSERT), so no UPDATE; nothing removes a route,
-- so no DELETE.

GRANT INSERT, SELECT ON notifications.invoice_routes TO notification_app;
GRANT INSERT, SELECT ON notifications.payment_routes TO notification_app;
