package com.themistra.payment.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.themistra.payment.common.CryptoClient;
import com.themistra.payment.common.crypto.RegisterWatchResponse;
import com.themistra.payment.consumer.dto.ChainTxConfirmed;
import com.themistra.payment.consumer.dto.ChainTxFinalized;
import com.themistra.payment.consumer.dto.ChainTxReorged;
import com.themistra.payment.consumer.dto.ChainTxSeen;
import com.themistra.payment.invoice.InvoiceService;
import com.themistra.payment.invoice.InvoiceState;
import com.themistra.payment.invoice.InvoiceWatchView;
import com.themistra.payment.payment.FinalizationOutcome;
import com.themistra.payment.payment.PaymentService;
import com.themistra.payment.payment.PaymentState;
import com.themistra.payment.payment.ReorgOutcome;
import com.themistra.payment.receipt.ReceiptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.Optional;

/**
 * T11. Mirrors {@code notification-service}'s own {@code AuthEventConsumer} shape: raw
 * {@code String} payloads, Spring Boot's autoconfigured {@link ObjectMapper}, one
 * {@code @Transactional} listener method per topic (Phase 3/4 Finding 4 — resolves {@code
 * design.md}'s own open O6 question: one listener per event type, same consumer group, every
 * event schema's own producer-side partition key is {@code watchId}, so per-payment ordering is
 * preserved within each topic). One deliberate divergence (Phase 8 Kimi Finding 2):
 * deserialization failures are caught and logged here, never left to propagate the way
 * {@code AuthEventConsumer}'s own listener methods still do — a poison message must never force
 * an infinite redelivery retry loop.
 *
 * <p><b>{@code unregisterWatch} runs last, inside the transaction (Decision 4/Finding 6 — a
 * deliberate departure from {@code CryptoClient.registerWatch}'s own T06 precedent, which runs
 * outside/before any transaction).</b> If it fails, the whole {@code SEEN} transition rolls back
 * — including the {@code processed_events} dedupe mark — so Kafka's own at-least-once redelivery
 * retries the complete atomic operation until it fully succeeds. Committing {@code SEEN} while
 * the watch is still live is exactly the window L14 exists to close. This holds a DB connection
 * open across an outbound HTTP call, an accepted trade-off (Finding 6) — future performance work
 * should tune connection-pool size/HTTP timeouts, never move this call outside the transaction.</p>
 *
 * <p><b>T12, {@code onChainTxReorged} is the one exception to "{@code @Transactional} on the
 * listener method itself" (Phase 3 Finding 4, Phase 6).</b> A {@code SEEN → WATCHING} reversal
 * (R12) needs a replacement {@code registerWatch} call that must run <em>after</em> the DB-side
 * reversal commits, win or lose (Decision 2/Phase 4 Finding 3) — the opposite placement from
 * {@code unregisterWatch} above, for the opposite reason: a failed register must never erase an
 * already-true reversal, where a failed unregister must. A plain {@code @Transactional} on the
 * whole method cannot express "commit, then best-effort call outside that commit" — it would
 * make everything inside the method, before and after the register call, one single connection
 * and transaction, same as the other three listeners' methods. A {@link TransactionTemplate},
 * built from the injected {@link PlatformTransactionManager} rather than from a self-invoked
 * {@code @Transactional} method (self-invocation bypasses Spring's AOP proxy entirely and the
 * annotation would silently do nothing), wraps only the dedupe-and-reverse step; the register
 * call runs afterward, in this method's own un-transacted continuation.</p>
 *
 * <p><b>Phase 9 Finding 6, an accepted trade-off, not a gap.</b> This means the Kafka offset
 * commit for {@code chain.tx.reorged} is coupled to the {@code registerWatch} HTTP round-trip
 * (R12 only) — the listener doesn't return, and Kafka doesn't ack, until that best-effort call
 * also finishes, even though its outcome is swallowed either way. Unlike {@code
 * unregisterWatch}'s own held-open DB connection above, nothing here blocks a DB connection, only
 * consumer-lag latency on this one topic. Revisit only if load testing against a slow/
 * unavailable crypto-service shows real lag impact — not a reason to move this call to an async
 * executor by default.</p>
 */
@Component
public class ChainEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ChainEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final ProcessedEventRepository processedEventRepository;
    private final InvoiceService invoiceService;
    private final PaymentService paymentService;
    private final CryptoClient cryptoClient;
    private final ReceiptService receiptService;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public ChainEventConsumer(ObjectMapper objectMapper, ProcessedEventRepository processedEventRepository,
                               InvoiceService invoiceService, PaymentService paymentService,
                               CryptoClient cryptoClient, ReceiptService receiptService, Clock clock,
                               PlatformTransactionManager transactionManager) {
        this.objectMapper = objectMapper;
        this.processedEventRepository = processedEventRepository;
        this.invoiceService = invoiceService;
        this.paymentService = paymentService;
        this.cryptoClient = cryptoClient;
        this.receiptService = receiptService;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @KafkaListener(topics = "chain.tx.seen", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onChainTxSeen(String rawJson) {
        ChainTxSeen event = deserialize(rawJson, ChainTxSeen.class, "chain.tx.seen");
        if (event == null || !dedupe(event.idempotencyKey())) {
            return;
        }

        Optional<InvoiceWatchView> maybeInvoice = invoiceService.findByWatchId(event.watchId());
        if (maybeInvoice.isEmpty()) {
            log.warn("Ignoring chain.tx.seen for watchId={}: no known invoice (unknown or already-unregistered watch)",
                    event.watchId());
            return;
        }
        InvoiceWatchView invoice = maybeInvoice.get();

        if (!paymentService.applySeen(invoice, event)) {
            return;
        }
        // Last, inside the transaction - see this class's own Javadoc.
        cryptoClient.unregisterWatch(invoice.watchId());
    }

    @KafkaListener(topics = "chain.tx.confirmed", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onChainTxConfirmed(String rawJson) {
        ChainTxConfirmed event = deserialize(rawJson, ChainTxConfirmed.class, "chain.tx.confirmed");
        if (event == null || !dedupe(event.idempotencyKey())) {
            return;
        }
        paymentService.applyConfirmed(event);
    }

    /** R10/R15 (T13). The invoice is looked up by {@link PaymentService
     * #findInvoiceIdByChainAndTxHash}'s own stable {@code invoiceId} - never by {@code
     * event.watchId()} (Phase 4 Finding 2) - so {@link PaymentService#applyFinalized} has the
     * invoice's own {@code amount} on hand to compute the discrepancy before its one atomic
     * update runs.
     *
     * <p><b>T17: no longer {@code @Transactional} on the method itself (Phase 3, Kimi Finding
     * 7).</b> The dedupe/resolve/apply/outcome-update sequence above is unchanged in its own
     * internal behavior, but now runs inside an explicit {@link TransactionTemplate} block, the
     * same split {@code onChainTxReorged} already established for the identical problem: {@link
     * ReceiptService#issueIfEligible} calls {@code CryptoClient.attest} (T15), an outbound HTTP
     * call that must never run with this transaction still open (L2). The call happens only
     * after that transaction commits, and only when the payment's own amount matched exactly
     * (R17's own resolved scope - an {@code UNDERPAID}/{@code OVERPAID} outcome never requests a
     * receipt at all). {@code ReceiptService} re-checks eligibility fresh itself (Phase 3, Kimi
     * Finding 3), so this method does not need to gate on whether {@code
     * markOutcomeIfOpenOrExpired} itself succeeded - only on the discrepancy classification.</p> */
    @KafkaListener(topics = "chain.tx.finalized", groupId = "${spring.kafka.consumer.group-id}")
    public void onChainTxFinalized(String rawJson) {
        ChainTxFinalized event = deserialize(rawJson, ChainTxFinalized.class, "chain.tx.finalized");
        if (event == null) {
            return;
        }

        Optional<FinalizationOutcome> outcome = transactionTemplate.execute(status -> {
            if (!dedupe(event.idempotencyKey())) {
                return Optional.<FinalizationOutcome>empty();
            }
            Optional<InvoiceWatchView> maybeInvoice = resolveInvoiceForFinalization(event);
            if (maybeInvoice.isEmpty()) {
                return Optional.<FinalizationOutcome>empty();
            }
            Optional<FinalizationOutcome> result = paymentService.applyFinalized(maybeInvoice.get(), event);
            result.ifPresent(o -> {
                InvoiceState invoiceOutcome = toInvoiceState(o.amountDiscrepancy());
                boolean updated = invoiceService.markOutcomeIfOpenOrExpired(o.invoiceId(), invoiceOutcome, clock.instant());
                if (!updated) {
                    log.warn("Invoice {} was not OPEN or EXPIRED when its payment finalized (state already "
                            + "moved on); leaving its own state as-is", o.invoiceId());
                }
            });
            return result;
        });

        outcome.filter(o -> o.amountDiscrepancy() == null)
                .ifPresent(o -> receiptService.issueIfEligible(o.paymentId(), event.idempotencyKey()));
    }

    /** Phase 9 Finding 1: extracted from {@code onChainTxFinalized}'s own body for readability -
     * resolves the invoice by the payment's own stable {@code invoiceId} (Phase 4 Finding 2),
     * never by {@code event.watchId()}, logging and returning empty for either failure case. */
    private Optional<InvoiceWatchView> resolveInvoiceForFinalization(ChainTxFinalized event) {
        Optional<Long> maybeInvoiceId = paymentService.findInvoiceIdByChainAndTxHash(event.chain(), event.txHash());
        if (maybeInvoiceId.isEmpty()) {
            log.warn("Ignoring chain.tx.finalized for chain={} txHash={}: no known payment", event.chain(), event.txHash());
            return Optional.empty();
        }
        Optional<InvoiceWatchView> maybeInvoice = invoiceService.findByIdAsWatchView(maybeInvoiceId.get());
        if (maybeInvoice.isEmpty()) {
            log.error("Ignoring chain.tx.finalized for invoiceId={}: payment references a non-existent invoice "
                    + "(data integrity issue, not a routine race)", maybeInvoiceId.get());
        }
        return maybeInvoice;
    }

    /** R15. {@code null} means an exact match (maps to {@code PAID}); the schema's own
     * `NULL | 'UNDERPAID' | 'OVERPAID'` values are the only ones {@code PaymentService
     * #classifyDiscrepancy} ever produces. */
    private static InvoiceState toInvoiceState(String amountDiscrepancy) {
        if (amountDiscrepancy == null) {
            return InvoiceState.PAID;
        }
        return switch (amountDiscrepancy) {
            case "UNDERPAID" -> InvoiceState.UNDERPAID;
            case "OVERPAID" -> InvoiceState.OVERPAID;
            default -> throw new IllegalStateException("Unexpected amountDiscrepancy value: " + amountDiscrepancy);
        };
    }

    /** R11/R12 (T12). See this class's own Javadoc for why this listener, alone, is not itself
     * {@code @Transactional}: {@code dedupe} and {@link PaymentService#applyReorged} run inside
     * one explicit {@link TransactionTemplate} transaction (L5's own "dedupe-first, same
     * transaction" still holds); the replacement-watch registration (R12 only) runs afterward,
     * outside it, best-effort (Decision 2).
     *
     * <p><b>Phase 9 Finding 5.</b> If the process crashes after the {@code transactionTemplate}
     * block above commits but before (or during) {@link #registerReplacementWatch}, Kafka
     * redelivery cannot retry the registration step on its own: {@code dedupe} already consumed
     * this event's idempotency key in the committed transaction, so the retry short-circuits
     * before ever calling {@code applyReorged} again. This lands in exactly the same place as a
     * {@code registerWatch} HTTP failure (the invoice rides its own natural expiry - see {@code
     * registerReplacementWatch}'s own Javadoc) - a second, distinct way to reach the same
     * already-accepted degradation, not a gap this method needs to additionally guard against.</p> */
    @KafkaListener(topics = "chain.tx.reorged", groupId = "${spring.kafka.consumer.group-id}")
    public void onChainTxReorged(String rawJson) {
        ChainTxReorged event = deserialize(rawJson, ChainTxReorged.class, "chain.tx.reorged");
        if (event == null) {
            return;
        }

        Optional<ReorgOutcome> outcome = transactionTemplate.execute(status -> {
            if (!dedupe(event.idempotencyKey())) {
                return Optional.empty();
            }
            return paymentService.applyReorged(event);
        });

        if (outcome != null && outcome.isPresent() && outcome.get().newState() == PaymentState.WATCHING) {
            registerReplacementWatch(outcome.get().invoiceId());
        }
    }

    /** Phase 4 Finding 3 resolution: a failure here (including an invoice whose own {@code
     * expires_at} has already passed, which {@code crypto-service}'s own watch-registration
     * validation rejects) is logged and swallowed, never retried from here - the invoice simply
     * rides its own natural expiry (see {@code InvoiceRepository
     * #findExpiredCandidatesWithoutInFlightPayment}'s own Phase 3 Finding 1 refinement), not a
     * permanent stuck state requiring operator intervention.
     *
     * <p><b>Phase 9 Finding 1.</b> Looks up the invoice by {@code invoiceId} (a stable foreign
     * key carried on {@link ReorgOutcome}), never by the original reorg event's own {@code
     * watchId} - see {@code ReorgOutcome}'s own Javadoc for the cross-partition staleness race
     * that lookup would otherwise be exposed to.</p>
     *
     * <p><b>Phase 9 Finding 7.</b> Short-circuits before calling {@code registerWatch} at all if
     * the invoice is no longer {@code OPEN} - avoids wasting the call (and, pending Finding 3
     * below, needing to immediately undo it) for an invoice that has already resolved some other
     * way by the time this best-effort step runs.</p>
     *
     * <p><b>Phase 9 Finding 3.</b> If {@code registerWatch} still succeeds but {@code
     * updateWatchIdIfOpen} returns {@code false} (the invoice resolved in the narrow window
     * between the state check above and this update), the newly registered watch would
     * otherwise be orphaned - nothing would ever reference or unregister it on the
     * crypto-service side. {@code unregisterWatch} is called immediately to clean it up.</p>
     *
     * <p><b>Phase 9 Finding 2 (and a self-caught correction during Phase 10 test generation: the
     * first version of this fix only wrapped the {@code registerWatch}/{@code
     * updateWatchIdIfOpen} calls, leaving the {@code findByIdAsWatchView} lookup above still
     * outside any catch - a new test exercising exactly that path caught it).</b> The
     * {@code try} below wraps this method's entire body, not just the two {@code CryptoClient}/
     * {@code InvoiceService} write calls - a failure anywhere in this best-effort step,
     * including the initial lookup, must reach the same logged-and-swallowed outcome; the
     * DB-side reversal already committed, so letting anything here propagate would only trigger
     * a Kafka redelivery that is guaranteed to be a no-op (Finding 5's own crash-window case).</p> */
    private void registerReplacementWatch(Long invoiceId) {
        try {
            Optional<InvoiceWatchView> maybeInvoice = invoiceService.findByIdAsWatchView(invoiceId);
            if (maybeInvoice.isEmpty()) {
                log.warn("Cannot register a replacement watch for invoiceId={}: no known invoice", invoiceId);
                return;
            }
            InvoiceWatchView invoice = maybeInvoice.get();
            if (invoice.state() != InvoiceState.OPEN) {
                log.warn("Skipping replacement watch registration for invoiceUuid={}: invoice is no longer OPEN "
                        + "(state={})", invoice.invoiceUuid(), invoice.state());
                return;
            }
            RegisterWatchResponse response = cryptoClient.registerWatch(invoice.invoiceUuid(), invoice.chain(),
                    invoice.payToAddress(), invoice.tokenContractAddress(), invoice.amount(), invoice.expiresAt());
            boolean updated = invoiceService.updateWatchIdIfOpen(invoice.id(), response.watchId(), clock.instant());
            if (!updated) {
                log.warn("Invoice {} was no longer OPEN when the replacement watch was persisted; "
                        + "unregistering the now-orphaned watch {}", invoice.invoiceUuid(), response.watchId());
                cryptoClient.unregisterWatch(response.watchId());
            }
        } catch (RuntimeException e) {
            log.error("Failed to register a replacement watch for invoiceId={}: the invoice will rely on its "
                    + "own natural expiry rather than get stuck (Phase 4 Finding 3)", invoiceId, e);
        }
    }

    /** Phase 8 Kimi Finding 2: a malformed payload must never propagate out of a listener method -
     * Spring Kafka's own default error handler would otherwise retry the same offset indefinitely
     * (a poison-message loop), never acknowledging past it. Logged at {@code ERROR} with the raw
     * payload (useful for diagnosing a producer-side bug) and dropped - this is the same
     * "an unreadable message is dead-lettered, not retried forever" judgment
     * {@code notification-service}'s own {@code RetryScheduler} already makes for its own
     * equivalent poison-pill case, applied here at the point of deserialization instead. */
    private <T> T deserialize(String rawJson, Class<T> type, String topic) {
        try {
            return objectMapper.readValue(rawJson, type);
        } catch (JsonProcessingException e) {
            log.error("Unreadable {} payload, dropping: {}", topic, rawJson, e);
            return null;
        }
    }

    /** R13/L5. @return {@code true} if this event key is genuinely new (processing should
     * continue), {@code false} if it's a duplicate delivery (the caller returns immediately, a
     * complete no-op). */
    private boolean dedupe(String idempotencyKey) {
        return processedEventRepository.insertIfNew(idempotencyKey, clock.instant()) == 1;
    }
}
