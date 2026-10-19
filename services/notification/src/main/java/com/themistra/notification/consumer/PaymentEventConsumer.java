package com.themistra.notification.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.themistra.notification.consumer.dto.InvoiceCreatedEvent;
import com.themistra.notification.consumer.dto.PaymentFinalizedEvent;
import com.themistra.notification.consumer.dto.PaymentSeenEvent;
import com.themistra.notification.consumer.dto.ReceiptIssuedEvent;
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
 * deduplicated and logged, not dispatched and not thrown: a throw would redeliver it forever.
 * There is no deferred retry (FU-T07-4).</p>
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

        Optional<UUID> merchant = paymentRouting.findMerchantForInvoice(event.invoiceUuid());
        if (merchant.isEmpty()) {
            logNotDispatched("payments.payment.seen", eventKey, "no merchant route for invoice " + event.invoiceUuid());
            return;
        }
        Map<String, String> eventData = invoiceData(eventKey, event.invoiceUuid(), event.chain());
        putIfPresent(eventData, "txHash", event.txHash());
        notificationDispatcher.dispatch(merchant.get(), PAYMENT_SEEN, eventData);
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
        Optional<UUID> invoice = paymentRouting.findInvoiceForPayment(event.paymentUuid());
        Optional<UUID> merchant = invoice.flatMap(paymentRouting::findMerchantForInvoice);
        if (merchant.isEmpty()) {
            logNotDispatched("payments.payment.finalized", eventKey, "no route for payment " + event.paymentUuid());
            return;
        }
        Map<String, String> eventData = invoiceData(eventKey, invoice.get(), event.chain());
        putIfPresent(eventData, "txHash", event.txHash());
        putIfPresent(eventData, "amount", AmountFormatter.format(event.amount(),
                paymentRouting.findTokenDecimals(invoice.get()).orElse(null)));
        notificationDispatcher.dispatch(merchant.get(), PAYMENT_FINALIZED, eventData);
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

        Optional<UUID> merchant = Optional.ofNullable(event.merchantAccountUuid())
                .or(() -> paymentRouting.findMerchantForInvoice(event.invoiceUuid()));
        if (merchant.isEmpty()) {
            logNotDispatched("payments.receipt.issued", eventKey, "no merchant route for invoice " + event.invoiceUuid());
            return;
        }
        // receiptUuid makes TemplateRenderer compute the /app/receipts/<uuid> link (FU-T07-2,
        // closed 2026-10-19 once payment served the receipt read). receiptId is the display value.
        // The internal s3:// receiptUri is never passed on.
        Map<String, String> eventData = invoiceData(eventKey, event.invoiceUuid(), event.chain());
        eventData.put("receiptUuid", event.receiptUuid().toString());
        eventData.put("receiptId", event.receiptUuid().toString());
        notificationDispatcher.dispatch(merchant.get(), RECEIPT_ISSUED, eventData);
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
