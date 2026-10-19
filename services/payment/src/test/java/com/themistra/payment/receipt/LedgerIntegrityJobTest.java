package com.themistra.payment.receipt;

import com.themistra.payment.receipt.ReceiptReconciliationResult.Discrepancy;
import com.themistra.payment.receipt.ledger.LedgerVerificationResult;
import com.themistra.payment.receipt.ledger.LedgerVerifier;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** Threat #5 follow-up FU-T28-2: the scheduled trigger and the metrics alerting watches. */
@ExtendWith(MockitoExtension.class)
class LedgerIntegrityJobTest {

    private static final Instant NOW = Instant.parse("2026-10-19T03:30:00Z");
    private static final String PREFIX = LedgerIntegrityJob.METRIC_PREFIX;

    @Mock
    private LedgerVerifier ledgerVerifier;
    @Mock
    private ReceiptReconciler receiptReconciler;

    private SimpleMeterRegistry registry;
    private LedgerIntegrityJob job;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        job = new LedgerIntegrityJob(ledgerVerifier, receiptReconciler, registry, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private double gauge(String name) {
        return registry.get(PREFIX + "." + name).gauge().value();
    }

    private double runs(String outcome) {
        return registry.get(PREFIX + ".runs").tag("outcome", outcome).counter().count();
    }

    @Test
    void shouldPublishNotYetRunBeforeTheFirstRun() {
        assertThat(gauge("chain.intact")).isEqualTo(-1);
        assertThat(gauge("receipt.discrepancies")).isEqualTo(-1);
        assertThat(gauge("last.completed.epoch.seconds")).isEqualTo(-1);
    }

    @Test
    void shouldPublishAnIntactRun() {
        when(ledgerVerifier.verify()).thenReturn(new LedgerVerificationResult.Intact());
        when(receiptReconciler.reconcile()).thenReturn(new ReceiptReconciliationResult(3, List.of()));

        job.run();

        assertThat(gauge("chain.intact")).isEqualTo(1);
        assertThat(gauge("receipt.discrepancies")).isZero();
        assertThat(gauge("last.completed.epoch.seconds")).isEqualTo(NOW.getEpochSecond());
        assertThat(runs("intact")).isEqualTo(1);
        assertThat(runs("tampered")).isZero();
    }

    @Test
    void shouldPublishABrokenChain() {
        when(ledgerVerifier.verify()).thenReturn(new LedgerVerificationResult.Broken(4L, "entry_hash mismatch"));
        when(receiptReconciler.reconcile()).thenReturn(new ReceiptReconciliationResult(3, List.of()));

        job.run();

        assertThat(gauge("chain.intact")).isZero();
        assertThat(runs("tampered")).isEqualTo(1);
        assertThat(runs("intact")).isZero();
    }

    /** An intact chain with receipt discrepancies is still tampering: the owner-rewrite case. */
    @Test
    void shouldPublishReceiptDiscrepanciesEvenWhenTheChainIsIntact() {
        when(ledgerVerifier.verify()).thenReturn(new LedgerVerificationResult.Intact());
        when(receiptReconciler.reconcile()).thenReturn(new ReceiptReconciliationResult(3, List.of(
                new Discrepancy(UUID.randomUUID(), "receipts/a", ReceiptReconciler.REASON_DIGEST_MISMATCH),
                new Discrepancy(null, "receipts/b", ReceiptReconciler.REASON_ORPHAN_OBJECT))));

        job.run();

        assertThat(gauge("chain.intact")).isEqualTo(1);
        assertThat(gauge("receipt.discrepancies")).isEqualTo(2);
        assertThat(runs("tampered")).isEqualTo(1);
    }

    /** A failed run must not look like a passing one: the gauges keep their last values and
     * last.completed stops advancing, so the staleness alert fires. */
    @Test
    void shouldCountAFailedRunAndKeepTheLastCompletedValues() {
        when(ledgerVerifier.verify()).thenReturn(new LedgerVerificationResult.Intact());
        when(receiptReconciler.reconcile())
                .thenReturn(new ReceiptReconciliationResult(1, List.of()))
                .thenThrow(new ReceiptStorageException("S3 down", new RuntimeException()));
        job.run();

        job.run();

        assertThat(runs("error")).isEqualTo(1);
        assertThat(runs("intact")).isEqualTo(1);
        assertThat(gauge("chain.intact")).isEqualTo(1);
        assertThat(gauge("last.completed.epoch.seconds")).isEqualTo(NOW.getEpochSecond());
    }

    @Test
    void shouldRunOnTheConfiguredCronUnderAShedLock() throws NoSuchMethodException {
        var run = LedgerIntegrityJob.class.getMethod("run");

        assertThat(run.getAnnotation(Scheduled.class).cron())
                .isEqualTo("${themistra.payment.ledger.integrity-check.cron}");
        assertThat(run.getAnnotation(SchedulerLock.class).name()).isEqualTo("ledger-integrity-check");
    }
}
