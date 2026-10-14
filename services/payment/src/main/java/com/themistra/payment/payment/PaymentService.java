package com.themistra.payment.payment;

import com.themistra.payment.consumer.dto.ChainTxConfirmed;
import com.themistra.payment.consumer.dto.ChainTxFinalized;
import com.themistra.payment.consumer.dto.ChainTxSeen;
import com.themistra.payment.events.OutboxPublisher;
import com.themistra.payment.invoice.InvoiceState;
import com.themistra.payment.invoice.InvoiceWatchView;
import com.themistra.payment.payment.event.PaymentFinalizedPayload;
import com.themistra.payment.payment.event.PaymentSeenPayload;
import com.themistra.payment.verification.VerificationRecordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * T11. The first real caller of {@link PaymentStateMachine} (T09) and {@link
 * VerificationRecordService} (T10). Every state/field change on a {@link Payment} goes through
 * {@link PaymentRepository}'s own atomic, conditional {@code UPDATE} methods — never a
 * load-mutate-save (see {@link Payment}'s own Javadoc for why: three separate Kafka topics can
 * target the same row with no cross-topic ordering guarantee).
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final List<PaymentState> FINALIZABLE_STATES = List.of(PaymentState.SEEN, PaymentState.CONFIRMING);

    private final PaymentRepository repository;
    private final PaymentStateMachine stateMachine;
    private final VerificationRecordService verificationRecordService;
    private final OutboxPublisher outboxPublisher;
    private final Clock clock;

    public PaymentService(PaymentRepository repository, PaymentStateMachine stateMachine,
                           VerificationRecordService verificationRecordService, OutboxPublisher outboxPublisher,
                           Clock clock) {
        this.repository = repository;
        this.stateMachine = stateMachine;
        this.verificationRecordService = verificationRecordService;
        this.outboxPublisher = outboxPublisher;
        this.clock = clock;
    }

    /** R7/R7a/R8. Phase 3 Finding 1: an invoice that is no longer {@code OPEN} means this
     * {@code chain.tx.seen} is stale relative to an already-resolved invoice - logged at
     * {@code WARN} and dropped. No {@link com.themistra.payment.verification.VerificationRecord}
     * is possible for this case (its own {@code payment_id} column is {@code NOT NULL}, and there
     * is no {@code Payment} row to attach one to) - a disclosed, schema-driven limitation, not an
     * oversight. Returns {@link Optional#empty()} in that case so {@code ChainEventConsumer}
     * knows not to call {@code unregisterWatch} either (nothing was actually recorded as seen).
     */
    @Transactional
    public Optional<Payment> applySeen(InvoiceWatchView invoice, ChainTxSeen event) {
        Objects.requireNonNull(invoice, "invoice must not be null");
        Objects.requireNonNull(event, "event must not be null");

        if (invoice.state() != InvoiceState.OPEN) {
            log.warn("Ignoring chain.tx.seen for invoiceUuid={}: invoice is no longer OPEN (state={})",
                    invoice.invoiceUuid(), invoice.state());
            return Optional.empty();
        }

        Instant now = clock.instant();
        // Decision 1/Finding 5 (frozen brief): no real prior row exists - CREATED/WATCHING are
        // never actually persisted. stateMachine.isPermitted is asserted true by construction,
        // not re-derived from a real stored row.
        if (!stateMachine.isPermitted(PaymentState.WATCHING, PaymentState.SEEN)) {
            throw new IllegalStateException("WATCHING -> SEEN must always be permitted - PaymentStateMachine's own edge set changed unexpectedly");
        }

        Payment payment = Payment.createSeen(invoice.id(), event.chain(), event.txHash(),
                invoice.payToAddress(), event.tokenContractAddress(), event.confirmations(), now);
        Payment saved = repository.save(payment);

        verificationRecordService.record(saved.getId(), null, PaymentState.SEEN, event.idempotencyKey(),
                event.confirmations(), null);

        outboxPublisher.publish("payment-seen", saved.getPaymentUuid().toString(), "payment.seen",
                new PaymentSeenPayload(saved.getPaymentUuid(), invoice.invoiceUuid(), saved.getChain(),
                        saved.getTxHash(), saved.getTokenContractAddress(), saved.getConfirmations(), now));

        return Optional.of(saved);
    }

    /** R9. Three-way outcome per Phase 3/4 Finding 3: {@code SEEN → CONFIRMING} is a real
     * transition; an already-{@code CONFIRMING} payment with a strictly greater count is a
     * field-only refresh (no verification row); anything else (a not-greater count while
     * {@code CONFIRMING}, or any other current state) is stale (R14, {@code recordIgnored}). */
    @Transactional
    public void applyConfirmed(ChainTxConfirmed event) {
        Objects.requireNonNull(event, "event must not be null");
        Optional<Payment> maybePayment = repository.findByChainAndTxHash(event.chain(), event.txHash());
        if (maybePayment.isEmpty()) {
            log.warn("Ignoring chain.tx.confirmed for chain={} txHash={}: no known payment", event.chain(), event.txHash());
            return;
        }
        Payment payment = maybePayment.get();
        Instant now = clock.instant();

        int transitioned = repository.markConfirmingIfSeen(payment.getId(), PaymentState.SEEN, PaymentState.CONFIRMING,
                event.confirmations(), now);
        if (transitioned > 0) {
            verificationRecordService.record(payment.getId(), PaymentState.SEEN, PaymentState.CONFIRMING,
                    event.idempotencyKey(), event.confirmations(), null);
            return;
        }

        int refreshed = repository.refreshConfirmationsIfGreaterAndCurrentlyConfirming(payment.getId(),
                PaymentState.CONFIRMING, event.confirmations(), now);
        if (refreshed > 0) {
            // Phase 3/4 Finding 3: a field-only refresh, deliberately no VerificationRecord.
            return;
        }

        PaymentState currentState = repository.findById(payment.getId())
                .map(Payment::getState)
                .orElse(payment.getState());
        verificationRecordService.recordIgnored(payment.getId(), currentState, event.idempotencyKey(),
                Map.of("reason", "stale-or-non-increasing-confirmed-event", "reportedConfirmations", event.confirmations()));
    }

    /** R10. {@code SEEN}/{@code CONFIRMING → FINALIZED} is the real transition; any other current
     * state is stale (R14, {@code recordIgnored}). Persists the event's own real
     * {@code amount}/{@code fromAddress}/{@code toAddress}/{@code addressPoisoningFlag},
     * overwriting {@link Payment#createSeen}'s own placeholder {@code amount = 0}. */
    @Transactional
    public void applyFinalized(ChainTxFinalized event) {
        Objects.requireNonNull(event, "event must not be null");
        Optional<Payment> maybePayment = repository.findByChainAndTxHash(event.chain(), event.txHash());
        if (maybePayment.isEmpty()) {
            log.warn("Ignoring chain.tx.finalized for chain={} txHash={}: no known payment", event.chain(), event.txHash());
            return;
        }
        Payment payment = maybePayment.get();
        PaymentState priorState = payment.getState();
        Instant now = clock.instant();

        int finalized = repository.markFinalizedIfCurrentState(payment.getId(), PaymentState.FINALIZED,
                FINALIZABLE_STATES, event.amount(), event.fromAddress(), event.toAddress(), event.confirmations(),
                event.addressPoisoningFlag(), now);

        if (finalized == 0) {
            PaymentState currentState = repository.findById(payment.getId())
                    .map(Payment::getState)
                    .orElse(priorState);
            verificationRecordService.recordIgnored(payment.getId(), currentState, event.idempotencyKey(),
                    Map.of("reason", "stale-finalized-event"));
            return;
        }

        verificationRecordService.record(payment.getId(), priorState, PaymentState.FINALIZED,
                event.idempotencyKey(), event.confirmations(), null);

        outboxPublisher.publish("payment-finalized", payment.getPaymentUuid().toString(), "payment.finalized",
                new PaymentFinalizedPayload(payment.getPaymentUuid(), event.chain(), event.txHash(),
                        event.fromAddress(), event.toAddress(), event.tokenContractAddress(),
                        event.amount().toPlainString(), event.confirmations(), event.addressPoisoningFlag(), now));
    }
}
