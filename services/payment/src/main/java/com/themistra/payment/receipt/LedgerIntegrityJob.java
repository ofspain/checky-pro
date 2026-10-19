package com.themistra.payment.receipt;

import com.themistra.payment.receipt.ReceiptReconciliationResult.Discrepancy;
import com.themistra.payment.receipt.ledger.LedgerVerificationResult;
import com.themistra.payment.receipt.ledger.LedgerVerifier;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Threat #5 follow-ups FU-T28-2 and FU-T28-3: the production trigger for tamper detection. On its
 * cron it runs {@link LedgerVerifier} (is the hash chain intact?) and {@link ReceiptReconciler}
 * (does the database still agree with the locked S3 originals?), logs every break at ERROR, and
 * publishes the outcome as metrics that alerting rules can watch.
 *
 * <p><b>Metrics.</b></p>
 * <ul>
 *   <li>{@code themistra.payment.ledger.integrity.chain.intact}: 1 when the last run found the
 *       chain intact, 0 when broken, -1 before the first completed run.</li>
 *   <li>{@code themistra.payment.ledger.integrity.receipt.discrepancies}: discrepancies the last
 *       run found, -1 before the first completed run.</li>
 *   <li>{@code themistra.payment.ledger.integrity.last.completed.epoch.seconds}: when a run last
 *       completed, so an alert can also fire if the check stops running.</li>
 *   <li>{@code themistra.payment.ledger.integrity.runs}, tagged {@code outcome} = {@code intact},
 *       {@code tampered} or {@code error}.</li>
 * </ul>
 * <p>An alert should fire on {@code chain.intact == 0}, on {@code receipt.discrepancies > 0}, and
 * when {@code last.completed.epoch.seconds} is older than two schedule intervals. Those rules
 * belong to the monitoring configuration, not this service.</p>
 *
 * <p>ShedLock-guarded, so it runs once across replicas (agents.md). Not {@code @Transactional}:
 * both checks are read-only and each manages its own reads.</p>
 */
@Component
public class LedgerIntegrityJob {

    private static final Logger log = LoggerFactory.getLogger(LedgerIntegrityJob.class);

    static final String METRIC_PREFIX = "themistra.payment.ledger.integrity";
    private static final long NOT_YET_RUN = -1;

    private final LedgerVerifier ledgerVerifier;
    private final ReceiptReconciler receiptReconciler;
    private final Clock clock;
    private final AtomicLong chainIntact = new AtomicLong(NOT_YET_RUN);
    private final AtomicLong receiptDiscrepancies = new AtomicLong(NOT_YET_RUN);
    private final AtomicLong lastCompletedEpochSeconds = new AtomicLong(NOT_YET_RUN);
    private final Counter intactRuns;
    private final Counter tamperedRuns;
    private final Counter erroredRuns;

    public LedgerIntegrityJob(LedgerVerifier ledgerVerifier, ReceiptReconciler receiptReconciler,
            MeterRegistry meterRegistry, Clock clock) {
        this.ledgerVerifier = Objects.requireNonNull(ledgerVerifier, "ledgerVerifier must not be null");
        this.receiptReconciler = Objects.requireNonNull(receiptReconciler, "receiptReconciler must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
        Gauge.builder(METRIC_PREFIX + ".chain.intact", chainIntact, AtomicLong::get)
                .description("1 if the last integrity check found the ledger hash chain intact, 0 if broken, -1 before the first run")
                .register(meterRegistry);
        Gauge.builder(METRIC_PREFIX + ".receipt.discrepancies", receiptDiscrepancies, AtomicLong::get)
                .description("Receipts that disagreed with their locked S3 original in the last integrity check, -1 before the first run")
                .register(meterRegistry);
        Gauge.builder(METRIC_PREFIX + ".last.completed.epoch.seconds", lastCompletedEpochSeconds, AtomicLong::get)
                .description("When the ledger integrity check last completed, -1 before the first run")
                .register(meterRegistry);
        this.intactRuns = runsCounter(meterRegistry, "intact");
        this.tamperedRuns = runsCounter(meterRegistry, "tampered");
        this.erroredRuns = runsCounter(meterRegistry, "error");
    }

    private static Counter runsCounter(MeterRegistry meterRegistry, String outcome) {
        return Counter.builder(METRIC_PREFIX + ".runs")
                .description("Ledger integrity check runs, by outcome")
                .tag("outcome", outcome)
                .register(meterRegistry);
    }

    @Scheduled(cron = "${themistra.payment.ledger.integrity-check.cron}")
    @SchedulerLock(name = "ledger-integrity-check", lockAtMostFor = "30m", lockAtLeastFor = "1m")
    public void run() {
        try {
            LedgerVerificationResult chain = ledgerVerifier.verify();
            ReceiptReconciliationResult receipts = receiptReconciler.reconcile();

            boolean chainOk = chain instanceof LedgerVerificationResult.Intact;
            if (chain instanceof LedgerVerificationResult.Broken broken) {
                log.error("LEDGER INTEGRITY ALERT: hash chain broken at seq={}: {}", broken.seq(), broken.reason());
            }
            for (Discrepancy discrepancy : receipts.discrepancies()) {
                log.error("LEDGER INTEGRITY ALERT: receiptUuid={} s3KeyPrefix={}: {}",
                        discrepancy.receiptUuid(), discrepancy.s3KeyPrefix(), discrepancy.reason());
            }

            chainIntact.set(chainOk ? 1 : 0);
            receiptDiscrepancies.set(receipts.discrepancies().size());
            lastCompletedEpochSeconds.set(clock.instant().getEpochSecond());
            if (chainOk && receipts.consistent()) {
                intactRuns.increment();
                log.info("Ledger integrity check passed: chain intact, {} receipts consistent with S3",
                        receipts.receiptsChecked());
            } else {
                tamperedRuns.increment();
            }
        } catch (RuntimeException e) {
            // The gauges keep the last completed run's values, and last.completed stops advancing,
            // so a staleness alert still fires if the check keeps failing.
            erroredRuns.increment();
            log.error("Ledger integrity check could not complete", e);
        }
    }
}
