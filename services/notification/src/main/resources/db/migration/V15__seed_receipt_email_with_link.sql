-- Open-issues sweep (2026-10-19, FU-T07-2). Version 3 of the receipt.issued EMAIL template, adding
-- the receipt link now that payment-service serves the merchant receipt read (payment FU-T29-1,
-- GET /api/v1/receipts/{receiptUuid}) and the SPA route is /app/receipts/:id. TemplateRenderer
-- computes {{receiptLink}} from the event's receiptUuid; it never comes from event data directly.
-- Templates are immutable (L9), so this ships as a new row. IN_APP stays at version 2, without a
-- link.

INSERT INTO notifications.templates (name, channel, version, subject, body) VALUES
    ('receipt.issued', 'EMAIL', 3,
        'Receipt issued',
        'The signed receipt {{receiptId}} for invoice {{invoiceId}} has been issued. {{receiptLink}}');
