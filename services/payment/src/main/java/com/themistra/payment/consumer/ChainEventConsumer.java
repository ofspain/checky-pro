package com.themistra.payment.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.themistra.payment.common.CryptoClient;
import com.themistra.payment.consumer.dto.ChainTxConfirmed;
import com.themistra.payment.consumer.dto.ChainTxFinalized;
import com.themistra.payment.consumer.dto.ChainTxSeen;
import com.themistra.payment.invoice.InvoiceService;
import com.themistra.payment.invoice.InvoiceWatchView;
import com.themistra.payment.payment.Payment;
import com.themistra.payment.payment.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;

/**
 * T11. Mirrors {@code notification-service}'s own {@code AuthEventConsumer} shape exactly: raw
 * {@code String} payloads, Spring Boot's autoconfigured {@link ObjectMapper}, one
 * {@code @Transactional} listener method per topic (Phase 3/4 Finding 4 — resolves {@code
 * design.md}'s own open O6 question: one listener per event type, same consumer group, every
 * event schema's own producer-side partition key is {@code watchId}, so per-payment ordering is
 * preserved within each topic).
 *
 * <p><b>{@code unregisterWatch} runs last, inside the transaction (Decision 4/Finding 6 — a
 * deliberate departure from {@code CryptoClient.registerWatch}'s own T06 precedent, which runs
 * outside/before any transaction).</b> If it fails, the whole {@code SEEN} transition rolls back
 * — including the {@code processed_events} dedupe mark — so Kafka's own at-least-once redelivery
 * retries the complete atomic operation until it fully succeeds. Committing {@code SEEN} while
 * the watch is still live is exactly the window L14 exists to close. This holds a DB connection
 * open across an outbound HTTP call, an accepted trade-off (Finding 6) — future performance work
 * should tune connection-pool size/HTTP timeouts, never move this call outside the transaction.</p>
 */
@Component
public class ChainEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(ChainEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final ProcessedEventRepository processedEventRepository;
    private final InvoiceService invoiceService;
    private final PaymentService paymentService;
    private final CryptoClient cryptoClient;
    private final Clock clock;

    public ChainEventConsumer(ObjectMapper objectMapper, ProcessedEventRepository processedEventRepository,
                               InvoiceService invoiceService, PaymentService paymentService,
                               CryptoClient cryptoClient, Clock clock) {
        this.objectMapper = objectMapper;
        this.processedEventRepository = processedEventRepository;
        this.invoiceService = invoiceService;
        this.paymentService = paymentService;
        this.cryptoClient = cryptoClient;
        this.clock = clock;
    }

    @KafkaListener(topics = "chain.tx.seen", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onChainTxSeen(String rawJson) throws JsonProcessingException {
        ChainTxSeen event = objectMapper.readValue(rawJson, ChainTxSeen.class);
        if (!dedupe(event.idempotencyKey())) {
            return;
        }

        Optional<InvoiceWatchView> maybeInvoice = invoiceService.findByWatchId(event.watchId());
        if (maybeInvoice.isEmpty()) {
            log.warn("Ignoring chain.tx.seen for watchId={}: no known invoice (unknown or already-unregistered watch)",
                    event.watchId());
            return;
        }
        InvoiceWatchView invoice = maybeInvoice.get();

        Optional<Payment> applied = paymentService.applySeen(invoice, event);
        if (applied.isEmpty()) {
            return;
        }
        // Last, inside the transaction - see this class's own Javadoc.
        cryptoClient.unregisterWatch(invoice.watchId());
    }

    @KafkaListener(topics = "chain.tx.confirmed", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onChainTxConfirmed(String rawJson) throws JsonProcessingException {
        ChainTxConfirmed event = objectMapper.readValue(rawJson, ChainTxConfirmed.class);
        if (!dedupe(event.idempotencyKey())) {
            return;
        }
        paymentService.applyConfirmed(event);
    }

    @KafkaListener(topics = "chain.tx.finalized", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onChainTxFinalized(String rawJson) throws JsonProcessingException {
        ChainTxFinalized event = objectMapper.readValue(rawJson, ChainTxFinalized.class);
        if (!dedupe(event.idempotencyKey())) {
            return;
        }
        paymentService.applyFinalized(event);
    }

    /** R13/L5. @return {@code true} if this event key is genuinely new (processing should
     * continue), {@code false} if it's a duplicate delivery (the caller returns immediately, a
     * complete no-op). */
    private boolean dedupe(String idempotencyKey) {
        return processedEventRepository.insertIfNew(idempotencyKey, clock.instant()) == 1;
    }
}
