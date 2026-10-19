-- Open-issues sweep (2026-10-19, FU-T07-4). Payment events that arrive before the route that names
-- their merchant (for example payment.seen before invoice.created, which is on another topic) were
-- deduplicated and dropped for good. They are now parked here and replayed, in lifecycle order, by the
-- event that records the missing route; a replayed row is deleted in the same transaction as its
-- dispatch. The payload is the event's own JSON, needed to render it later; it is never logged.
-- clock_timestamp(), not now(): two events parked in one transaction still get distinct times, which
-- orders events of the same lifecycle step.

CREATE TABLE notifications.parked_payment_events (
    event_key    TEXT PRIMARY KEY,           -- the processed_events key, e.g. payment.seen:<uuid>
    kind         TEXT NOT NULL,              -- payment.seen, payment.finalized or receipt.issued
    payload      TEXT NOT NULL,
    invoice_uuid UUID,                       -- null when the event names only its payment
    payment_uuid UUID,
    parked_at    TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX parked_payment_events_invoice_uuid_idx ON notifications.parked_payment_events (invoice_uuid);
CREATE INDEX parked_payment_events_payment_uuid_idx ON notifications.parked_payment_events (payment_uuid);

-- No UPDATE: a parked row is never changed, only replayed and removed.
GRANT INSERT, SELECT, DELETE ON notifications.parked_payment_events TO notification_app;
