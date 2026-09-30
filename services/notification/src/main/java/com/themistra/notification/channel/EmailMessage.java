package com.themistra.notification.channel;

/**
 * The transport-boundary message shape {@link EmailTransport#send} accepts - deliberately separate
 * from {@link com.themistra.notification.template.TemplateRenderer.RenderedMessage} since this one
 * also carries the resolved {@code to}/{@code from} addresses, not just rendered content.
 *
 * <p>{@code subject}/{@code body} can contain the recipient's own display name and, for
 * {@code email.verify}/{@code email.password_reset}, the raw verification/reset token embedded in a
 * computed link (T09) - {@code toString()} is overridden to exclude both, mirroring
 * {@code RenderedMessage}'s own established safe-{@code toString()} precedent exactly, so no future
 * log statement that happens to print an {@code EmailMessage} whole can leak either.</p>
 */
public record EmailMessage(String to, String from, String subject, String body) {

    @Override
    public String toString() {
        return "EmailMessage[to=" + to + ", from=" + from
                + ", subjectLength=" + (subject == null ? 0 : subject.length())
                + ", bodyLength=" + (body == null ? 0 : body.length()) + "]";
    }
}
