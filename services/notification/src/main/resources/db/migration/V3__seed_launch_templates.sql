-- T02 AC3: minimal, real seed content for the 7 launch event->template mappings, both EMAIL and
-- IN_APP channel variants (14 rows), matching the default channel-preference matrix (design.md §4c:
-- SECURITY and PAYMENT categories are ON for both channels by default). Placeholder syntax is
-- {{variable}} - a reasonable, engine-agnostic default; O6 (the actual rendering engine) is task 9's
-- own open decision and may require revisiting this syntax, not treated as final here.
--
-- auth.user.lifecycle (user.suspended) -> account.suspended is explicitly marked optional (Q7) and
-- is not seeded at launch.
--
-- IN_APP names for the two auth-originated, EMAIL-prefixed templates are distinct from their EMAIL
-- counterparts (user.verify, user.password_reset), per the Phase 4 frozen brief's own naming table;
-- the other five mappings reuse the identical name across both channels, differentiated only by the
-- channel column.

INSERT INTO notifications.templates (name, channel, version, subject, body) VALUES
    ('email.verify', 'EMAIL', 1,
        'Verify your Themistra account',
        'Hi {{displayName}}, please verify your email by visiting {{verificationLink}}. This link expires in 24 hours. If you did not request this, ignore this email.'),
    ('user.verify', 'IN_APP', 1,
        NULL,
        'Verify your email address to finish setting up your account. {{verificationLink}}'),
    ('email.password_reset', 'EMAIL', 1,
        'Reset your Themistra password',
        'Hi {{displayName}}, we received a request to reset your password. Visit {{resetLink}} to choose a new one. This link expires in 1 hour. If you did not request this, you can safely ignore this email.'),
    ('user.password_reset', 'IN_APP', 1,
        NULL,
        'A password reset was requested for your account. If this was not you, secure your account immediately.'),
    ('user.welcome', 'EMAIL', 1,
        'Welcome to Themistra',
        'Hi {{displayName}}, welcome aboard! Your account is ready. {{getStartedLink}}'),
    ('user.welcome', 'IN_APP', 1,
        NULL,
        'Welcome to Themistra, {{displayName}}! Explore your dashboard to get started.'),
    ('invoice.created', 'EMAIL', 1,
        'New invoice created',
        'Hi {{displayName}}, an invoice for {{amount}} {{currency}} has been created. {{invoiceLink}}'),
    ('invoice.created', 'IN_APP', 1,
        NULL,
        'A new invoice for {{amount}} {{currency}} was created.'),
    ('payment.seen', 'EMAIL', 1,
        'Payment detected on-chain',
        'Hi {{displayName}}, we have seen a payment of {{amount}} {{currency}} on-chain for invoice {{invoiceId}}. It is awaiting confirmations.'),
    ('payment.seen', 'IN_APP', 1,
        NULL,
        'Payment of {{amount}} {{currency}} seen on-chain, awaiting confirmation.'),
    ('payment.finalized', 'EMAIL', 1,
        'Payment finalized',
        'Hi {{displayName}}, your payment of {{amount}} {{currency}} for invoice {{invoiceId}} has been finalized on-chain.'),
    ('payment.finalized', 'IN_APP', 1,
        NULL,
        'Payment of {{amount}} {{currency}} finalized.'),
    ('receipt.issued', 'EMAIL', 1,
        'Your receipt is ready',
        'Hi {{displayName}}, your receipt for invoice {{invoiceId}} is ready. {{receiptLink}}'),
    ('receipt.issued', 'IN_APP', 1,
        NULL,
        'Your receipt is ready. {{receiptLink}}');
