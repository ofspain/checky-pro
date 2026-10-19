package com.themistra.notification.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.themistra.notification.consumer.dto.InvoiceCreatedEvent;
import com.themistra.notification.consumer.dto.PaymentFinalizedEvent;
import com.themistra.notification.consumer.dto.PaymentSeenEvent;
import com.themistra.notification.consumer.dto.ReceiptIssuedEvent;
import com.themistra.notification.routing.ParkedEvent;
import com.themistra.notification.routing.PaymentRouting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Notification T07 (R3, R4, R5). Consumes the four {@code payments.*} events and notifies the
 * invoice's merchant through {@link NotificationDispatcher}.
 *
 * <p><b>Recipients (Q7, decided at T07 Phase 4).</b> The merchant only. {@code design.md} §4c
 * says "merchant + payer", but a payer is an anonymous wallet with no account or contact anywhere
 * on the platform, so payer notification is deferred (FU-T07-1).</p>
 *
 * <p><b>Finding the merchant.</b> {@code invoice.created} names it. {@code payment.seen} names
 * only its invoice, and {@code payment.finalized} only its payment, so {@link PaymentRouting}
 * remembers the links earlier events carried. {@code receipt.issued} names it optionally. When no
 * merchant can be found (for example, a route event still in another topic's lag), the event is
 * deduplicated and parked, not thrown: a throw would redeliver it forever. The event that later
 * records the missing route replays the parked ones for that invoice, in lifecycle order, and removes
 * each one it dispatches (FU-T07-4, closed 2026-10-19). An event whose route never arrives stays
 * parked.</p>
 *
 * <p>Each listener deduplicates first (L1), then records routing, then dispatches, all in one
 * transaction. Logs carry only the topic, the event key and aggregate UUIDs, never payloads,
 * wallet addresses or amounts. {@code receiptUri} (an internal {@code s3://} location) never
 * reaches a template.</p>
 */
@Component
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);

    static final String INVOICE_CREATED = "invoice.created";
    static final String PAYMENT_SEEN = "payment.seen";
    static final String PAYMENT_FINALIZED = "payment.finalized";
    static final String RECEIPT_ISSUED = "receipt.issued";

    private final ObjectMapper objectMapper;
    private final IdempotencyGuard idempotencyGuard;
    private final PaymentRouting paymentRouting;
    private final NotificationDispatcher notificationDispatcher;

    public PaymentEventConsumer(ObjectMapper objectMapper, IdempotencyGuard idempotencyGuard,
                                PaymentRouting paymentRouting, NotificationDispatcher notificationDispatcher) {
        this.objectMapper = objectMapper;
        this.idempotencyGuard = idempotencyGuard;
        this.paymentRouting = paymentRouting;
        this.notificationDispatcher = notificationDispatcher;
    }

    @KafkaListener(topics = "payments.invoice.created", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onInvoiceCreated(String rawJson) throws JsonProcessingException {
        InvoiceCreatedEvent event = objectMapper.readValue(rawJson, InvoiceCreatedEvent.class);
        String eventKey = INVOICE_CREATED + ":" + event.invoiceUuid();
        if (event.invoiceUuid() == null) {
            logNotDispatched("payments.invoice.created", eventKey, "it names no invoice; not recorded");
            return;
        }
        if (!idempotencyGuard.recordIfNew(eventKey, INVOICE_CREATED)) {
            return;
        }
        if (event.merchantAccountUuid() == null) {
            logNotDispatched("payments.invoice.created", eventKey, "it names no merchant");
            return;
        }
        paymentRouting.recordInvoice(event.invoiceUuid(), event.merchantAccountUuid(), event.tokenDecimals());

        Map<String, String> eventData = invoiceData(eventKey, event.invoiceUuid(), event.chain());
        putIfPresent(eventData, "amount", AmountFormatter.format(event.amount(),
                event.tokenDecimals() == null ? null : event.tokenDecimals().intValue()));
        notificationDispatcher.dispatch(event.merchantAccountUuid(), INVOICE_CREATED, eventData);
        // FU-T07-4: this event is what most late payment events were waiting for.
        replayParkedFor(event.invoiceUuid());
    }

    @KafkaListener(topics = "payments.payment.seen", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onPaymentSeen(String rawJson) throws JsonProcessingException {
        PaymentSeenEvent event = objectMapper.readValue(rawJson, PaymentSeenEvent.class);
        String eventKey = PAYMENT_SEEN + ":" + event.paymentUuid();
        if (event.paymentUuid() == null) {
            logNotDispatched("payments.payment.seen", eventKey, "it names no payment; not recorded");
            return;
        }
        if (!idempotencyGuard.recordIfNew(eventKey, PAYMENT_SEEN)) {
            return;
        }
        if (event.invoiceUuid() == null) {
            logNotDispatched("payments.payment.seen", eventKey, "it names no invoice");
            return;
        }
        paymentRouting.recordPayment(event.paymentUuid(), event.invoiceUuid());

        if (dispatchPaymentSeen(event, eventKey)) {
            // The payment route just recorded may unblock a payment.finalized that came first.
            replayParkedFor(event.invoiceUuid());
        } else {
            park("payments.payment.seen", eventKey, PAYMENT_SEEN, rawJson, event.invoiceUuid(), event.paymentUuid(),
                    "no merchant route for invoice " + event.invoiceUuid());
        }
    }

    @KafkaListener(topics = "payments.payment.finalized", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onPaymentFinalized(String rawJson) throws JsonProcessingException {
        PaymentFinalizedEvent event = objectMapper.readValue(rawJson, PaymentFinalizedEvent.class);
        String eventKey = PAYMENT_FINALIZED + ":" + event.paymentUuid();
        if (event.paymentUuid() == null) {
            logNotDispatched("payments.payment.finalized", eventKey, "it names no payment; not recorded");
            return;
        }
        if (!idempotencyGuard.recordIfNew(eventKey, PAYMENT_FINALIZED)) {
            return;
        }
        if (!dispatchPaymentFinalized(event, eventKey)) {
            park("payments.payment.finalized", eventKey, PAYMENT_FINALIZED, rawJson,
                    paymentRouting.findInvoiceForPayment(event.paymentUuid()).orElse(null), event.paymentUuid(),
                    "no route for payment " + event.paymentUuid());
        }
    }

    @KafkaListener(topics = "payments.receipt.issued", groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onReceiptIssued(String rawJson) throws JsonProcessingException {
        ReceiptIssuedEvent event = objectMapper.readValue(rawJson, ReceiptIssuedEvent.class);
        String eventKey = RECEIPT_ISSUED + ":" + event.receiptUuid();
        if (event.receiptUuid() == null) {
            logNotDispatched("payments.receipt.issued", eventKey, "it names no receipt; not recorded");
            return;
        }
        if (!idempotencyGuard.recordIfNew(eventKey, RECEIPT_ISSUED)) {
            return;
        }
        if (event.invoiceUuid() == null) {
            logNotDispatched("payments.receipt.issued", eventKey, "it names no invoice");
            return;
        }
        // This event carries every link, so it also repairs routes an earlier event never left.
        if (event.paymentUuid() != null) {
            paymentRouting.recordPayment(event.paymentUuid(), event.invoiceUuid());
        }
        if (event.merchantAccountUuid() != null) {
            paymentRouting.recordInvoice(event.invoiceUuid(), event.merchantAccountUuid(), null);
        }
        // Earlier events of this payment that were waiting go out first, keeping their order.
        replayParkedFor(event.invoiceUuid());

        if (!dispatchReceiptIssued(event, eventKey)) {
            park("payments.receipt.issued", eventKey, RECEIPT_ISSUED, rawJson, event.invoiceUuid(),
                    event.paymentUuid(), "no merchant route for invoice " + event.invoiceUuid());
        }
    }

    private boolean dispatchPaymentSeen(PaymentSeenEvent event, String eventKey) {
        Optional<UUID> merchant = paymentRouting.findMerchantForInvoice(event.invoiceUuid());
        if (merchant.isEmpty()) {
            return false;
        }
        Map<String, String> eventData = invoiceData(eventKey, event.invoiceUuid(), event.chain());
        putIfPresent(eventData, "txHash", event.txHash());
        notificationDispatcher.dispatch(merchant.get(), PAYMENT_SEEN, eventData);
        return true;
    }

    private boolean dispatchPaymentFinalized(PaymentFinalizedEvent event, String eventKey) {
        Optional<UUID> invoice = paymentRouting.findInvoiceForPayment(event.paymentUuid());
        Optional<UUID> merchant = invoice.flatMap(paymentRouting::findMerchantForInvoice);
        if (merchant.isEmpty()) {
            return false;
        }
        Map<String, String> eventData = invoiceData(eventKey, invoice.get(), event.chain());
        putIfPresent(eventData, "txHash", event.txHash());
        putIfPresent(eventData, "amount", AmountFormatter.format(event.amount(),
                paymentRouting.findTokenDecimals(invoice.get()).orElse(null)));
        notificationDispatcher.dispatch(merchant.get(), PAYMENT_FINALIZED, eventData);
        return true;
    }

    private boolean dispatchReceiptIssued(ReceiptIssuedEvent event, String eventKey) {
        Optional<UUID> merchant = Optional.ofNullable(event.merchantAccountUuid())
                .or(() -> paymentRouting.findMerchantForInvoice(event.invoiceUuid()));
        if (merchant.isEmpty()) {
            return false;
        }
        // receiptUuid makes TemplateRenderer compute the /app/receipts/<uuid> link (FU-T07-2,
        // closed 2026-10-19 once payment served the receipt read). receiptId is the display value.
        // The internal s3:// receiptUri is never passed on.
        Map<String, String> eventData = invoiceData(eventKey, event.invoiceUuid(), event.chain());
        eventData.put("receiptUuid", event.receiptUuid().toString());
        eventData.put("receiptId", event.receiptUuid().toString());
        notificationDispatcher.dispatch(merchant.get(), RECEIPT_ISSUED, eventData);
        return true;
    }

    /** FU-T07-4: an event whose merchant can't be found yet is parked, not dropped. It is already
     * deduplicated, so a redelivery can't bring it back; {@link #replayParkedFor} does instead. */
    private void park(String topic, String eventKey, String kind, String rawJson, UUID invoiceUuid,
                      UUID paymentUuid, String reason) {
        paymentRouting.park(eventKey, kind, rawJson, invoiceUuid, paymentUuid);
        logNotDispatched(topic, eventKey, reason + "; parked until its route arrives");
    }

    /** Re-tries, in lifecycle order, every parked event that this invoice's routes may now resolve. One
     * that dispatches is removed in the same transaction; one that still can't stays parked. */
    private void replayParkedFor(UUID invoiceUuid) {
        for (ParkedEvent parked : paymentRouting.findParkedFor(invoiceUuid)) {
            boolean dispatched = switch (parked.kind()) {
                case PAYMENT_SEEN -> dispatchPaymentSeen(read(parked, PaymentSeenEvent.class), parked.eventKey());
                case PAYMENT_FINALIZED ->
                        dispatchPaymentFinalized(read(parked, PaymentFinalizedEvent.class), parked.eventKey());
                case RECEIPT_ISSUED -> dispatchReceiptIssued(read(parked, ReceiptIssuedEvent.class), parked.eventKey());
                default -> throw new IllegalStateException("unknown parked event kind for key " + parked.eventKey());
            };
            if (dispatched) {
                paymentRouting.releaseParked(parked.eventKey());
                log.info("Parked payment event with key {} replayed", parked.eventKey());
            }
        }
    }

    private <T> T read(ParkedEvent parked, Class<T> type) {
        try {
            return objectMapper.readValue(parked.payload(), type);
        } catch (JsonProcessingException e) {
            // It parsed when it was parked, so this is corruption, not a caller error.
            throw new IllegalStateException("parked payment event " + parked.eventKey() + " is unreadable", e);
        }
    }

    private static Map<String, String> invoiceData(String eventKey, UUID invoiceUuid, String chain) {
        Map<String, String> eventData = new HashMap<>();
        eventData.put("sourceEventKey", eventKey);
        eventData.put("invoiceUuid", invoiceUuid.toString());
        eventData.put("invoiceId", invoiceUuid.toString());
        putIfPresent(eventData, "chain", chain);
        return eventData;
    }

    private static void putIfPresent(Map<String, String> eventData, String key, String value) {
        if (value != null) {
            eventData.put(key, value);
        }
    }

    private static void logNotDispatched(String topic, String eventKey, String reason) {
        log.info("Payment event on {} with key {} not dispatched: {}", topic, eventKey, reason);
    }
}
