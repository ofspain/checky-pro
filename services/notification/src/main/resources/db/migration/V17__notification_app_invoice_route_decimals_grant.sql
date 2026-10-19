-- Open-issues sweep (2026-10-19, S2). An invoice route can be learned first from an event without
-- token decimals (receipt.issued), and the insert-once write then dropped the decimals a later
-- invoice.created carried. The write now fills token_decimals when, and only when, it is still
-- null (ON CONFLICT DO UPDATE ... WHERE token_decimals IS NULL). That needs UPDATE on that one
-- column. merchant_account_uuid and created_at stay non-updatable; DELETE stays denied.

GRANT UPDATE (token_decimals) ON notifications.invoice_routes TO notification_app;
