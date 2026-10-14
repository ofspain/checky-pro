package com.themistra.payment.invoice;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.themistra.payment.common.CryptoClient;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T08 (R5). Mocked {@link InvoiceService}/{@link CryptoClient}, no Spring context, no Docker -
 * mirrors {@code notification-service}'s own {@code RetrySchedulerTest} shape exactly, down to
 * calling {@code processOne} directly (package-private, same reason: a plain-JUnit suite never
 * boots a Spring context, so the real {@code @Scheduled} firing is never exercised here by
 * design).
 */
class InvoiceExpirySweepJobTest {

    private static final Instant NOW = Instant.parse("2026-07-13T00:00:00Z");
    private static final UUID MERCHANT_ACCOUNT_UUID = UUID.randomUUID();

    private final InvoiceService invoiceService = mock(InvoiceService.class);
    private final CryptoClient cryptoClient = mock(CryptoClient.class);
    private final InvoiceExpirySweepJob job = new InvoiceExpirySweepJob(invoiceService, cryptoClient);

    private static Invoice invoiceWithWatchId(UUID watchId) {
        return invoiceWithWatchId(watchId, UUID.randomUUID());
    }

    private static Invoice invoiceWithWatchId(UUID watchId, UUID invoiceUuid) {
        return Invoice.create(invoiceUuid, MERCHANT_ACCOUNT_UUID, "ETHEREUM", "0xabc", (short) 18,
                new BigDecimal("1000"), "0xdef", null, NOW.plusSeconds(3600), watchId, NOW);
    }

    /** R5, named. AC5: {@code unregisterWatch} must run before {@code expireInvoice}, not inside
     * it - proven here by asserting both were called, mirroring {@code CryptoClient}'s own
     * Javadoc-documented outside-the-transaction discipline (the same one T06 established for
     * {@code registerWatch}). */
    @Test
    void shouldExpireInvoiceAndUnregisterWatchAfterTtl() {
        UUID watchId = UUID.randomUUID();
        Invoice invoice = invoiceWithWatchId(watchId);
        when(invoiceService.expireInvoice(invoice)).thenReturn(true);

        job.processOne(invoice);

        // Phase 12 (self-identified while building the traceability matrix): the two bare
        // verify()s below only proved both calls happened, never AC5's own actual claim - that
        // unregisterWatch runs strictly before expireInvoice (its own @Transactional method),
        // never inside or after it.
        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(cryptoClient, invoiceService);
        inOrder.verify(cryptoClient).unregisterWatch(watchId);
        inOrder.verify(invoiceService).expireInvoice(invoice);
    }

    /** AC8 (Phase 3 finding 7): a candidate with no {@code watchId} is skipped entirely - neither
     * {@code unregisterWatch} nor {@code expireInvoice} is ever called for it. Defensive-only:
     * every invoice {@code InvoiceController} (T06) creates always has a {@code watchId}, so this
     * path is not currently reachable in production. */
    @Test
    void shouldSkipCandidateWithNullWatchId() {
        Invoice invoice = invoiceWithWatchId(null);

        job.processOne(invoice);

        verify(cryptoClient, never()).unregisterWatch(any());
        verify(invoiceService, never()).expireInvoice(any());
    }

    /** Phase 9 (Kimi Phase 8 findings 1/3/6): {@code expireInvoice} returning {@code false} means
     * the invoice was no longer {@code OPEN} by the time the atomic update ran (e.g. a payment
     * became in-flight first) - not an error. The sweep must not throw or otherwise treat this as
     * a failure; {@code unregisterWatch} has already run by this point regardless (the disclosed,
     * narrower residual race documented in {@code processOne}'s own Javadoc).
     *
     * <p>Phase 11 Gap 8 (Kimi): originally only proved "doesn't throw" - extended with {@code
     * ListAppender} (mirrors {@code HeldFactAlerterTest}'s own identical pattern, crypto-service)
     * to also prove the disclosed residual race is actually logged at {@code WARN}, not silently
     * swallowed. */
    @Test
    void shouldNotThrowWhenInvoiceWasNoLongerOpenWhenExpiryWasAttempted() {
        UUID watchId = UUID.randomUUID();
        UUID invoiceUuid = UUID.randomUUID();
        Invoice invoice = invoiceWithWatchId(watchId, invoiceUuid);
        when(invoiceService.expireInvoice(invoice)).thenReturn(false);

        Logger logger = (Logger) LoggerFactory.getLogger(InvoiceExpirySweepJob.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            job.processOne(invoice);

            verify(cryptoClient).unregisterWatch(watchId);
            verify(invoiceService).expireInvoice(invoice);
            assertThat(appender.list).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getFormattedMessage())
                        .contains(invoiceUuid.toString())
                        .contains("no longer OPEN");
            });
        } finally {
            logger.detachAppender(appender);
        }
    }

    /** AC4: one invoice's own failure - including an exception {@code processOne} itself throws -
     * must not abort the sweep for the invoices after it. Mirrors {@code RetrySchedulerTest}'s own
     * {@code sweepSkipsAFailingRowWithoutAbortingTheRest} precedent exactly. */
    @Test
    void shouldContinueSweepAfterOneInvoiceFails() {
        Invoice badInvoice = invoiceWithWatchId(UUID.randomUUID());
        Invoice goodInvoice = invoiceWithWatchId(UUID.randomUUID());
        when(invoiceService.findExpiredCandidatesWithoutInFlightPayment())
                .thenReturn(List.of(badInvoice, goodInvoice));
        org.mockito.Mockito.doThrow(new RuntimeException("crypto-service unavailable"))
                .when(cryptoClient).unregisterWatch(badInvoice.getWatchId());
        when(invoiceService.expireInvoice(goodInvoice)).thenReturn(true);

        job.sweep();

        verify(invoiceService).expireInvoice(goodInvoice);
        verify(invoiceService, never()).expireInvoice(badInvoice);
    }

    /** Phase 11 Gap 3 (Kimi): {@code sweepMethodIsAnnotatedSchedulerLock} proves the ShedLock
     * guard, but nothing proved the method is actually scheduled - a missing {@code @Scheduled}
     * would leave the job dormant despite the cron property being bound. */
    @Test
    void sweepMethodIsAnnotatedScheduled() throws NoSuchMethodException {
        Method sweep = InvoiceExpirySweepJob.class.getMethod("sweep");
        org.springframework.scheduling.annotation.Scheduled scheduled =
                sweep.getAnnotation(org.springframework.scheduling.annotation.Scheduled.class);

        assertThat(scheduled).as("sweep() must be @Scheduled").isNotNull();
        assertThat(scheduled.cron()).isEqualTo("${themistra.payment.invoice.expiry-sweep.cron}");
    }

    /** AC3: {@code sweep()} must be {@code @SchedulerLock}-guarded, mirroring
     * {@code RetrySchedulerTest.sweepLockAnnotationIsPresentWithANonEmptyName}'s own identical
     * reflection-based structural proof. */
    @Test
    void sweepMethodIsAnnotatedSchedulerLock() throws NoSuchMethodException {
        Method sweep = InvoiceExpirySweepJob.class.getMethod("sweep");
        SchedulerLock lock = sweep.getAnnotation(SchedulerLock.class);

        assertThat(lock).as("sweep() must be @SchedulerLock-guarded (AC3)").isNotNull();
        assertThat(lock.name()).isNotBlank();
        assertThat(lock.lockAtMostFor()).isNotBlank();
    }

    /** Phase 5's own plan judged a live multi-replica ShedLock contention test disproportionate
     * (same disposition as {@code RetrySchedulerTest}'s own identical precedent) - {@code sweep}
     * itself must also not be {@code @Transactional} (one transaction per invoice, never one for
     * the whole sweep), proven here via reflection on the real annotation (a source-text search
     * would false-positive on this very class's own Javadoc, which mentions {@code @Transactional}
     * in prose). */
    @Test
    void sweepMethodIsNotAnnotatedTransactional() throws NoSuchMethodException {
        Method sweep = InvoiceExpirySweepJob.class.getMethod("sweep");

        assertThat(sweep.getAnnotation(org.springframework.transaction.annotation.Transactional.class)).isNull();
    }
}
