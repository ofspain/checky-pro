package com.themistra.notification.routing;

import com.themistra.notification.common.config.ParkedEventProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Expires parked payment events (open-issues sweep, 2026-10-20). A payment event whose route
 * never arrives stays in {@code parked_payment_events}; after
 * {@code themistra.notification.parked-events.max-age-hours} it is removed and logged at WARN,
 * and {@code themistra.notification.parked_events.expired} is incremented, so a lost route is
 * visible rather than silent. Only event keys are logged, never payloads. ShedLock-guarded, like
 * {@code RetryScheduler}: one replica runs it at a time.
 */
@Component
public class ParkedEventExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(ParkedEventExpiryJob.class);
    static final String EXPIRED_METRIC = "themistra.notification.parked_events.expired";

    private final PaymentRouting paymentRouting;
    private final ParkedEventProperties properties;
    private final Clock clock;
    private final Counter expired;

    public ParkedEventExpiryJob(PaymentRouting paymentRouting, ParkedEventProperties properties, Clock clock,
                                MeterRegistry meterRegistry) {
        this.paymentRouting = paymentRouting;
        this.properties = properties;
        this.clock = clock;
        this.expired = Counter.builder(EXPIRED_METRIC)
                .description("Parked payment events removed because their route never arrived")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${themistra.notification.parked-events.expiry-interval-seconds}",
            timeUnit = TimeUnit.SECONDS)
    @SchedulerLock(name = "parked-event-expiry", lockAtMostFor = "5m", lockAtLeastFor = "10s")
    public void expire() {
        Instant cutoff = clock.instant().minus(Duration.ofHours(properties.maxAgeHours()));
        List<String> keys = paymentRouting.expireParkedBefore(cutoff);
        for (String key : keys) {
            log.warn("Parked payment event with key {} expired after {}h without its route; it was never notified",
                    key, properties.maxAgeHours());
        }
        expired.increment(keys.size());
    }
}
