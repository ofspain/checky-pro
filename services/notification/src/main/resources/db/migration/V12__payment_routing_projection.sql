-- Notification T07 (frozen brief D2). Payment-routing projection: which merchant owns an invoice,
-- and which invoice a payment belongs to. payment.seen carries only invoiceUuid and
-- payment.finalized only paymentUuid, so without this the merchant can't be found for either.
-- Fed only by consumed payments.* events, never by a live call to payment-service (L2's purpose,
-- as the human interpreted it at T07 Phase 4). Insert-once: routing facts never change.

CREATE TABLE notifications.invoice_routes (
    invoice_uuid UUID PRIMARY KEY,
    merchant_account_uuid UUID NOT NULL,
    token_decimals SMALLINT,                 -- null when learned from an event without decimals
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE notifications.payment_routes (
    payment_uuid UUID PRIMARY KEY,
    invoice_uuid UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
