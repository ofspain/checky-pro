-- Notification T07 (frozen brief D1, D3, F7). Version 2 of the four payment templates. V3 is
-- immutable (L9), so the new wording ships as new rows; TemplateRenderer always picks the highest
-- version per (name, channel).
-- What changed from version 1:
--   * merchant-facing wording: only the merchant is notified at launch (Q7, D1)
--   * no {{currency}}: no payment event carries a currency or symbol
--   * no {{displayName}}: it is always null platform-wide, which rendered "Hi ,"
--   * no {{receiptLink}}: nothing serves /receipts/<id> yet (payment FU-T29-1, D3)
--   * amounts are human units, formatted by the consumer from base units and tokenDecimals

INSERT INTO notifications.templates (name, channel, version, subject, body) VALUES
    ('invoice.created', 'EMAIL', 2,
        'New invoice created',
        'An invoice for {{amount}} on {{chain}} has been created (invoice {{invoiceId}}). {{invoiceLink}}'),
    ('invoice.created', 'IN_APP', 2,
        NULL,
        'New invoice for {{amount}} on {{chain}} created.'),
    ('payment.seen', 'EMAIL', 2,
        'Payment detected on-chain',
        'A payment for invoice {{invoiceId}} has been seen on {{chain}} (transaction {{txHash}}). It is awaiting confirmations.'),
    ('payment.seen', 'IN_APP', 2,
        NULL,
        'Payment for invoice {{invoiceId}} seen on-chain, awaiting confirmation.'),
    ('payment.finalized', 'EMAIL', 2,
        'Payment finalized',
        'A payment of {{amount}} for invoice {{invoiceId}} has been finalized on {{chain}} (transaction {{txHash}}).'),
    ('payment.finalized', 'IN_APP', 2,
        NULL,
        'Payment of {{amount}} for invoice {{invoiceId}} finalized.'),
    ('receipt.issued', 'EMAIL', 2,
        'Your receipt is ready',
        'The signed receipt {{receiptId}} for invoice {{invoiceId}} has been issued.'),
    ('receipt.issued', 'IN_APP', 2,
        NULL,
        'Receipt {{receiptId}} issued for invoice {{invoiceId}}.');
