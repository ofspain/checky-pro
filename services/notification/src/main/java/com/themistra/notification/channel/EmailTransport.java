package com.themistra.notification.channel;

/**
 * The real-vs-fake seam (Kimi Phase 3 Findings #1/#3/#9): {@link EmailChannel} depends on this
 * interface only, never on a concrete transport, so exactly one implementation is ever active per
 * {@code themistra.notification.email.transport} value ({@code ses} or {@code fake}) - selected via
 * {@code @ConditionalOnProperty} on each implementation, never by {@code EmailChannel} itself.
 */
public interface EmailTransport {

    /**
     * @return the provider's own opaque success identifier (a real SES {@code messageId}, or a
     * synthetic id from the fake).
     * @throws EmailDeliveryException if the send attempt fails. Never swallowed here - propagates to
     * {@link EmailChannel#send}'s own caller unmodified.
     */
    String send(EmailMessage message);
}
