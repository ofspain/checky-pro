package com.themistra.crypto.watch;

/**
 * Publishes events when the watcher finds a payment.
 *
 * <p>Implementations may target Kafka, SQS, a webhook, or a test stub. The watcher knows nothing
 * about the transport — it just publishes the event and moves on.
 */
public interface WatcherEventPublisher {
    void publish(PaymentVerifiedEvent event);
}
