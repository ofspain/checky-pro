-- Kimi sweep review, Finding 7 (2026-10-20, author decision). A parked payment event whose route
-- never arrives is removed after the configured maximum age (ParkedEventExpiryJob). Until now that
-- left only a WARN log line and a metric. It now also leaves a durable delivery_log row with outcome
-- EXPIRED, written in the same transaction as the delete, so the dispute-grade log shows the
-- merchant was never notified, and why.
--
-- An EXPIRED row reached no channel and no recipient, so channel becomes nullable, but only for
-- EXPIRED: chk_delivery_channel_matches_outcome keeps every real attempt (SENT, FAILED, SUPPRESSED,
-- DEAD_LETTERED) required to name its channel, and forbids a channel on an EXPIRED row.
-- The grant is unchanged: notification_app still holds only INSERT and SELECT (L3, append-only).

ALTER TABLE notifications.delivery_log ALTER COLUMN channel DROP NOT NULL;

ALTER TABLE notifications.delivery_log DROP CONSTRAINT chk_delivery_outcome;
ALTER TABLE notifications.delivery_log ADD CONSTRAINT chk_delivery_outcome CHECK (outcome IN
    ('SENT','FAILED','SUPPRESSED','DEAD_LETTERED','EXPIRED'));

ALTER TABLE notifications.delivery_log ADD CONSTRAINT chk_delivery_channel_matches_outcome CHECK
    ((channel IS NULL) = (outcome = 'EXPIRED'));
