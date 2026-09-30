package com.themistra.notification.channel;

import java.util.UUID;

/**
 * The transport-boundary message shape {@link EmailTransport#send} accepts - deliberately separate
 * from {@link com.themistra.notification.template.TemplateRenderer.RenderedMessage} since this one
 * also carries the recipient's own {@code accountUuid} and the resolved {@code to}/{@code from}
 * addresses, not just rendered content. {@code accountUuid} (Kimi Phase 8 Finding #6) lets a test
 * correlate a captured {@link FakeEmailTransport} message back to the account that triggered it
 * without needing to know its email address in advance, and lets {@link EmailChannel} log an
 * account-correlated identifier instead of the recipient's own address (Finding #2).
 *
 * <p>{@code to} is itself PII, and {@code subject}/{@code body} can contain the recipient's own
 * display name and, for {@code email.verify}/{@code email.password_reset}, the raw
 * verification/reset token embedded in a computed link (T09) - {@code toString()} is overridden to
 * exclude all three (Kimi Phase 8 Finding #2 - the same PII concern that applies to
 * {@code EmailChannel}'s own log line applies equally here, since a future log statement could print
 * an {@code EmailMessage} whole), mirroring {@code RenderedMessage}'s own established
 * safe-{@code toString()} precedent. {@code from} is always the same fixed, non-secret system
 * address ({@code EmailProperties.from()}), so it is safe to include.</p>
 */
public record EmailMessage(UUID accountUuid, String to, String from, String subject, String body) {

    @Override
    public String toString() {
        return "EmailMessage[accountUuid=" + accountUuid + ", from=" + from
                + ", subjectLength=" + (subject == null ? 0 : subject.length())
                + ", bodyLength=" + (body == null ? 0 : body.length()) + "]";
    }
}
