package com.themistra.notification.routing;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.themistra.notification.common.config.ParkedEventProperties;
import com.themistra.notification.delivery.UndeliveredEventLog;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Parked-event expiry (open-issues sweep, 2026-10-20): the cutoff, the durable EXPIRED record per
 * event in the deleting transaction (Kimi sweep review, Finding 7), then the WARN per key and the
 * metric. */
class ParkedEventExpiryJobTest {

    private static final Instant NOW = Instant.parse("2026-10-20T12:00:00Z");

    private static final Instant PARKED_A = Instant.parse("2026-10-12T09:00:00Z");
    private static final Instant PARKED_B = Instant.parse("2026-10-12T10:00:00Z");

    private final PaymentRouting paymentRouting = mock(PaymentRouting.class);
    private final UndeliveredEventLog undeliveredEventLog = mock(UndeliveredEventLog.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final ParkedEventExpiryJob job;

    ParkedEventExpiryJobTest() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        job = new ParkedEventExpiryJob(paymentRouting, undeliveredEventLog, new TransactionTemplate(transactionManager),
                new ParkedEventProperties(168, 3600), Clock.fixed(NOW, ZoneOffset.UTC), meters);
    }

    private static List<ExpiredParkedEvent> twoExpired() {
        return List.of(new ExpiredParkedEvent("payment.seen:a", "payment.seen", PARKED_A),
                new ExpiredParkedEvent("payment.finalized:b", "payment.finalized", PARKED_B));
    }

    @Test
    void expiresEventsOlderThanTheMaxAgeAndLogsAndCountsEachOne() {
        when(paymentRouting.expireParkedBefore(any())).thenReturn(twoExpired());
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
        verify(undeliveredEventLog).recordExpired("payment.seen:a", "payment.seen", PARKED_A, 168);
        verify(undeliveredEventLog).recordExpired("payment.finalized:b", "payment.finalized", PARKED_B, 168);
        verify(transactionManager).commit(any());
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

    /** The records and the delete are one transaction: if a record can't be written, the run rolls
     * back, so the events stay parked for the next run, and nothing is logged or counted. */
    @Test
    void aRecordThatCannotBeWrittenRollsTheWholeRunBack() {
        when(paymentRouting.expireParkedBefore(any())).thenReturn(twoExpired());
        doThrow(new IllegalStateException("insert refused"))
                .when(undeliveredEventLog).recordExpired(anyString(), anyString(), any(), anyLong());

        assertThatThrownBy(job::expire).isInstanceOf(IllegalStateException.class);

        verify(transactionManager).rollback(any());
        verify(transactionManager, never()).commit(any());
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
