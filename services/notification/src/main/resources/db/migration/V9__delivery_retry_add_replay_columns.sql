-- T14: delivery_retry (V1) has no column carrying what a retry needs to actually re-render and
-- re-send - notification_kind (to re-resolve the template mapping) and event_data_json (the
-- original event payload, Jackson-serialized). No DEFAULT/backfill needed: zero rows have ever been
-- written to this table before this task.

ALTER TABLE notifications.delivery_retry
    ADD COLUMN notification_kind VARCHAR(64) NOT NULL,
    ADD COLUMN event_data_json TEXT NOT NULL;
