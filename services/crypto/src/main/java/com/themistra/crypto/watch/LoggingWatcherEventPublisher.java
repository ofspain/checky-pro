package com.themistra.crypto.watch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * MVP stub: logs events instead of publishing to real transport.
 *
 * <p>This is enough to prove the watcher works. When infra money arrives, swap this for a
 * KafkaWatcherEventPublisher or similar.
 *
 * <p>Active when {@code watcher.publisher.type=logging} or when no other publisher is wired.
 */
@Component
@ConditionalOnProperty(prefix = "watcher.publisher", name = "type", havingValue = "logging", matchIfMissing = true)
public class LoggingWatcherEventPublisher implements WatcherEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(LoggingWatcherEventPublisher.class);

    @Override
    public void publish(PaymentVerifiedEvent event) {
        log.info("PAYMENT_VERIFIED: watch={} txHash={} amount={} at block {}",
                event.watchId(), event.txHash(), event.observedAmount(), event.blockNumber());
    }
}
