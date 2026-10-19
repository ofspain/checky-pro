-- Open-issues sweep (2026-10-19, FU-T20-1). Version 2 of the in-app user.verify template, without
-- {{verificationLink}}. Version 1 put the one-time verification token into the stored in-app body;
-- in-app rows are durable and readable over the API, so the token outlived the email that needed
-- it. The in-app password-reset message already avoids its link for the same reason. The email
-- (email.verify) keeps the link. Templates are immutable (L9), so this ships as a new row.

INSERT INTO notifications.templates (name, channel, version, subject, body) VALUES
    ('user.verify', 'IN_APP', 2,
        NULL,
        'Verify your email address to finish setting up your account. Use the link in the email we sent you.');
