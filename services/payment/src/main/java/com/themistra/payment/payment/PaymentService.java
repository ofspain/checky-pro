package com.themistra.payment.payment;

import com.themistra.payment.consumer.dto.ChainTxConfirmed;
import com.themistra.payment.consumer.dto.ChainTxFinalized;
import com.themistra.payment.consumer.dto.ChainTxReorged;
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
    /** T11 (Phase 8, Kimi Finding 1), narrowed at T12 (Phase 3 Kimi Finding 1): the states that
     * ALWAYS block a new payment for the same invoice, regardless of {@code reorgedAt}. {@code
     * WATCHING} is deliberately excluded here - a fresh (never-reorged) {@code WATCHING} row
     * would still block (checked separately below via {@code
     * existsByInvoiceIdAndStateInAndReorgedAtIsNull}), but a reorged-back one must not, or the
     * very replacement transaction R12's own watch re-registration exists to detect would be
     * dropped by this guard - defeating the reorg-recovery path entirely. */
    private static final List<PaymentState> ACTIVE_NON_TERMINAL_STATES =
            List.of(PaymentState.CREATED, PaymentState.SEEN, PaymentState.CONFIRMING);
    private static final List<PaymentState> WATCHING_ONLY = List.of(PaymentState.WATCHING);

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
     * {@code WARN} and dropped. Phase 8 Kimi Finding 1 (a real, confirmed omission - Decision 3
     * named this exact race but it was never actually implemented until now): an invoice that
     * already has a non-terminal {@code Payment} row (the disclosed T08 Phase 9 residual
     * unregister-timing race letting a second real transaction slip through) must not get a
     * second, competing row either - also logged at {@code WARN} and dropped. Neither case can
     * produce a {@link com.themistra.payment.verification.VerificationRecord} (its own
     * {@code payment_id} column is {@code NOT NULL}, and there is no {@code Payment} row to
     * attach one to for the first case; the second case has a different, unrelated existing row,
     * not one this event belongs to) - a disclosed, schema-driven limitation, not an oversight.
     *
     * <p>Returns {@code false} in both cases (Phase 8 Kimi Finding 3: no longer returns
     * {@code Optional<Payment>} - the entity itself must never cross into {@code consumer}, even
     * as a generic type argument that happens not to trip today's ArchUnit bytecode scan; a
     * future refactor storing the result in a field could). {@code ChainEventConsumer} only needs
     * success/failure, to decide whether to call {@code unregisterWatch}. */
    @Transactional
    public boolean applySeen(InvoiceWatchView invoice, ChainTxSeen event) {
        Objects.requireNonNull(invoice, "invoice must not be null");
        Objects.requireNonNull(event, "event must not be null");

        if (invoice.state() != InvoiceState.OPEN) {
            log.warn("Ignoring chain.tx.seen for invoiceUuid={}: invoice is no longer OPEN (state={})",
                    invoice.invoiceUuid(), invoice.state());
            return false;
        }

        if (repository.existsByInvoiceIdAndStateIn(invoice.id(), ACTIVE_NON_TERMINAL_STATES)
                || repository.existsByInvoiceIdAndStateInAndReorgedAtIsNull(invoice.id(), WATCHING_ONLY)) {
            log.warn("Ignoring chain.tx.seen for invoiceUuid={}: an active non-terminal payment already exists "
                    + "(the disclosed T08 residual unregister-timing race, or a fresh WATCHING row)",
                    invoice.invoiceUuid());
            return false;
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

        return true;
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

        // Phase 8 Kimi Finding 6: a defensive sanity check, not an enforcement mechanism - the
        // atomic UPDATE's own WHERE clause is what actually enforces legality. This only guards
        // against PaymentStateMachine's own edge set (T09) ever drifting out of sync with this
        // repository's own hardcoded queries without anyone noticing.
        if (!stateMachine.isPermitted(PaymentState.SEEN, PaymentState.CONFIRMING)) {
            throw new IllegalStateException("SEEN -> CONFIRMING must always be permitted - PaymentStateMachine's own edge set changed unexpectedly");
        }
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

        // Phase 8 Kimi Finding 4: findCurrentState is a scalar projection, bypassing the
        // persistence context's own first-level cache - repository.findById(...) here would
        // return the same already-managed, now-stale `payment` instance loaded above.
        PaymentState currentState = repository.findCurrentState(payment.getId()).orElse(payment.getState());
        verificationRecordService.recordIgnored(payment.getId(), currentState, event.idempotencyKey(),
                Map.of("reason", "stale-or-non-increasing-confirmed-event", "reportedConfirmations", event.confirmations()));
    }

    /** R10. {@code SEEN}/{@code CONFIRMING → FINALIZED} is the real transition; any other current
     * state is stale (R14, {@code recordIgnored}). Persists the event's own real
     * {@code amount}/{@code fromAddress}/{@code toAddress}/{@code addressPoisoningFlag},
     * overwriting {@link Payment#createSeen}'s own placeholder {@code amount = 0}.
     *
     * <p>Tries {@code CONFIRMING} then {@code SEEN} as the expected prior state, one atomic
     * attempt at a time (Phase 7 self-review — see {@link PaymentRepository
     * #markFinalizedIfCurrentState}'s own Javadoc for why this must not be collapsed back into a
     * single {@code IN (SEEN, CONFIRMING)} query): whichever one succeeds is the real,
     * exactly-known {@code fromState} for the {@code VerificationRecord}, never inferred from a
     * read that could already be stale by the time the update actually runs. */
    @Transactional
    public void applyFinalized(ChainTxFinalized event) {
        Objects.requireNonNull(event, "event must not be null");
        Optional<Payment> maybePayment = repository.findByChainAndTxHash(event.chain(), event.txHash());
        if (maybePayment.isEmpty()) {
            log.warn("Ignoring chain.tx.finalized for chain={} txHash={}: no known payment", event.chain(), event.txHash());
            return;
        }
        Payment payment = maybePayment.get();
        Instant now = clock.instant();

        PaymentState fromState;
        if (tryFinalizeFrom(payment.getId(), PaymentState.CONFIRMING, event, now)) {
            fromState = PaymentState.CONFIRMING;
        } else if (tryFinalizeFrom(payment.getId(), PaymentState.SEEN, event, now)) {
            fromState = PaymentState.SEEN;
        } else {
            // Phase 8 Kimi Finding 4: see applyConfirmed's own identical comment.
            PaymentState currentState = repository.findCurrentState(payment.getId()).orElse(payment.getState());
            verificationRecordService.recordIgnored(payment.getId(), currentState, event.idempotencyKey(),
                    Map.of("reason", "stale-finalized-event"));
            return;
        }

        verificationRecordService.record(payment.getId(), fromState, PaymentState.FINALIZED,
                event.idempotencyKey(), event.confirmations(), null);

        outboxPublisher.publish("payment-finalized", payment.getPaymentUuid().toString(), "payment.finalized",
                new PaymentFinalizedPayload(payment.getPaymentUuid(), event.chain(), event.txHash(),
                        event.fromAddress(), event.toAddress(), event.tokenContractAddress(),
                        event.amount().toPlainString(), event.confirmations(), event.addressPoisoningFlag(), now));
    }

    private boolean tryFinalizeFrom(Long paymentId, PaymentState expectedCurrentState, ChainTxFinalized event, Instant now) {
        // Phase 8 Kimi Finding 6: same defensive sanity check as applyConfirmed's own identical
        // comment - the atomic UPDATE's own WHERE clause is the real enforcement.
        if (!stateMachine.isPermitted(expectedCurrentState, PaymentState.FINALIZED)) {
            throw new IllegalStateException(expectedCurrentState
                    + " -> FINALIZED must always be permitted - PaymentStateMachine's own edge set changed unexpectedly");
        }
        return repository.markFinalizedIfCurrentState(paymentId, expectedCurrentState, PaymentState.FINALIZED,
                event.amount(), event.fromAddress(), event.toAddress(), event.confirmations(),
                event.addressPoisoningFlag(), now) > 0;
    }

    /** R11/R12 (T12). A {@code chain.tx.reorged} event walks {@code CONFIRMING} back to {@code
     * SEEN} (R11, no watch action) or {@code SEEN} back to {@code WATCHING} (R12); any other
     * current state is stale (R14, {@code recordIgnored}). Phase 3 Finding 6/Phase 9 Finding 4:
     * the event's own {@code tokenContractAddress} must match the already-loaded {@code
     * Payment}'s own stored value, or the event is treated as stale regardless of state - a
     * mismatch is logged at {@code ERROR}, not {@code WARN}, since it indicates either a
     * producer bug or a malicious message, not a routine race. (There is no equivalent
     * {@code chain} check: {@code payment} was found via {@code findByChainAndTxHash(event.chain(),
     * ...)}, whose own {@code WHERE} clause already guarantees {@code payment.getChain().equals(
     * event.chain())} - an explicit re-check here would be dead code, not defense-in-depth.)
     *
     * <p>Returns a {@link ReorgOutcome} ({@link Optional#empty()} means stale/ignored - no
     * transition occurred) so {@code ChainEventConsumer} - never this method (Phase 3 Finding 4:
     * no outbound {@code CryptoClient}/{@code InvoiceService} call may originate here) - can
     * decide whether a replacement watch needs registering. Phase 8 Kimi Finding 1: carries
     * {@code invoiceId}, not just the landed-on state, specifically so the consumer can look up
     * the invoice by this stable foreign key rather than the event's own {@code watchId} (see
     * {@link ReorgOutcome}'s own Javadoc for why that would be unreliable).</p> */
    @Transactional
    public Optional<ReorgOutcome> applyReorged(ChainTxReorged event) {
        Objects.requireNonNull(event, "event must not be null");
        Optional<Payment> maybePayment = repository.findByChainAndTxHash(event.chain(), event.txHash());
        if (maybePayment.isEmpty()) {
            log.warn("Ignoring chain.tx.reorged for chain={} txHash={}: no known payment", event.chain(), event.txHash());
            return Optional.empty();
        }
        Payment payment = maybePayment.get();

        if (!payment.getTokenContractAddress().equals(event.tokenContractAddress())) {
            log.error("Ignoring chain.tx.reorged for paymentId={}: tokenContractAddress mismatch "
                    + "against the loaded payment's own stored value", payment.getId());
            return Optional.empty();
        }

        Instant now = clock.instant();

        // Phase 8 Kimi Finding 6 precedent: a defensive sanity check, not the real enforcement -
        // the atomic UPDATE's own WHERE clause enforces legality.
        if (!stateMachine.isPermitted(PaymentState.CONFIRMING, PaymentState.SEEN)
                || !stateMachine.isPermitted(PaymentState.SEEN, PaymentState.WATCHING)) {
            throw new IllegalStateException("A reorg reversal edge is not permitted - PaymentStateMachine's own edge set changed unexpectedly");
        }

        int toSeen = repository.markSeenIfConfirming(payment.getId(), PaymentState.CONFIRMING, PaymentState.SEEN, now);
        if (toSeen > 0) {
            verificationRecordService.record(payment.getId(), PaymentState.CONFIRMING, PaymentState.SEEN,
                    event.idempotencyKey(), null, null);
            return Optional.of(new ReorgOutcome(payment.getId(), payment.getInvoiceId(), PaymentState.SEEN));
        }

        int toWatching = repository.markWatchingIfSeen(payment.getId(), PaymentState.SEEN, PaymentState.WATCHING, now);
        if (toWatching > 0) {
            verificationRecordService.record(payment.getId(), PaymentState.SEEN, PaymentState.WATCHING,
                    event.idempotencyKey(), null, null);
            return Optional.of(new ReorgOutcome(payment.getId(), payment.getInvoiceId(), PaymentState.WATCHING));
        }

        // Phase 8 Kimi Finding 4 precedent: see applyConfirmed's own identical comment.
        PaymentState currentState = repository.findCurrentState(payment.getId()).orElse(payment.getState());
        verificationRecordService.recordIgnored(payment.getId(), currentState, event.idempotencyKey(),
                Map.of("reason", "stale-reorg-event"));
        return Optional.empty();
    }
}
