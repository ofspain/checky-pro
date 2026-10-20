package com.themistra.notification.routing;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.themistra.notification.common.config.ParkedEventProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Parked-event expiry (open-issues sweep, 2026-10-20): the cutoff, the WARN per key, the metric. */
class ParkedEventExpiryJobTest {

    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");

    private final PaymentRouting paymentRouting = mock(PaymentRouting.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final ParkedEventExpiryJob job = new ParkedEventExpiryJob(paymentRouting,
            new ParkedEventProperties(168, 3600), Clock.fixed(NOW, ZoneOffset.UTC), meters);

    @Test
    void expiresEventsOlderThanTheMaxAgeAndLogsAndCountsEachOne() {
        when(paymentRouting.expireParkedBefore(any())).thenReturn(List.of("payment.seen:a", "payment.finalized:b"));
        Logger logger = (Logger) LoggerFactory.getLogger(ParkedEventExpiryJob.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            job.expire();
        } finally {
            logger.detachAppender(appender);
        }

        verify(paymentRouting).expireParkedBefore(Instant.parse("2026-10-13T12:00:00Z"));
        assertThat(appender.list).extracting(ILoggingEvent::getLevel).containsOnly(Level.WARN);
        assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage).containsExactly(
                "Parked payment event with key payment.seen:a expired after 168h without its route; it was never notified",
                "Parked payment event with key payment.finalized:b expired after 168h without its route; it was never notified");
        assertThat(meters.counter(ParkedEventExpiryJob.EXPIRED_METRIC).count()).isEqualTo(2.0);
    }

    @Test
    void nothingToExpireLogsNothing() {
        when(paymentRouting.expireParkedBefore(any())).thenReturn(List.of());

        job.expire();

        assertThat(meters.counter(ParkedEventExpiryJob.EXPIRED_METRIC).count()).isZero();
    }

    /** One replica at a time, on the configured interval. */
    @Test
    void runsOnTheConfiguredIntervalUnderAShedLock() throws NoSuchMethodException {
        var method = ParkedEventExpiryJob.class.getMethod("expire");
        assertThat(method.getAnnotation(Scheduled.class).fixedDelayString())
                .isEqualTo("${themistra.notification.parked-events.expiry-interval-seconds}");
        assertThat(method.getAnnotation(SchedulerLock.class).name()).isEqualTo("parked-event-expiry");
    }
}
