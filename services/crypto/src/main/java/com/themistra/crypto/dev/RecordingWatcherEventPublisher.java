package com.themistra.crypto.dev;

import com.themistra.crypto.watch.PaymentVerifiedEvent;
import com.themistra.crypto.watch.WatcherEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Keeps the last few published events so the explorer can show them.
 *
 * <p>Local development only, and not a substitute for the observation log §6.1 calls for: this is
 * a bounded in-memory ring that a restart empties. It exists so a person can watch the pipeline
 * work, not so an attestation can be defended later.
 */
@Component
@Primary
@Profile("local")
public class RecordingWatcherEventPublisher implements WatcherEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RecordingWatcherEventPublisher.class);
    private static final int CAPACITY = 20;

    private final Deque<PaymentVerifiedEvent> recent = new ArrayDeque<>();

    @Override
    public synchronized void publish(PaymentVerifiedEvent event) {
        log.info("PAYMENT VERIFIED: watch={} tx={} amount={} block={}",
                event.watchId(), event.txHash(), event.observedAmount(), event.blockNumber());
        recent.addFirst(event);
        while (recent.size() > CAPACITY) {
            recent.removeLast();
        }
    }

    public synchronized List<PaymentVerifiedEvent> recent() {
        return List.copyOf(recent);
    }
}
