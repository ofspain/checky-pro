-- T13: notification_app needs to write every in-app notification (InAppChannel.send) and read them
-- back for the unread endpoint (InappReadController) and the SSE stream's own registry. No task
-- before this one ever touched inapp_notifications, so - like processed_events (V4),
-- contact_projection (V5), channel_preferences (V6), and templates (V7) before it - it was never
-- granted. Only INSERT + SELECT: marking a notification read (an UPDATE of read_at) is out of this
-- task's own scope.

GRANT INSERT, SELECT ON notifications.inapp_notifications TO notification_app;
