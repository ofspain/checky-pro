package com.themistra.notification.consumer;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.themistra.notification.routing.ParkedEvent;
import com.themistra.notification.routing.PaymentRouting;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Notification T07: {@code package.md} §8's three named tests plus routing, dedupe and the
 * not-dispatched branches. Mocked collaborators, a real {@link ObjectMapper}, no Spring context.
 * {@code PaymentEventConsumerIntegrationTest} covers the real Kafka, Postgres and routing wiring.
 */
class PaymentEventConsumerTest {

    private static final UUID INVOICE = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MERCHANT = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID PAYMENT = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID RECEIPT = UUID.fromString("44444444-4444-4444-4444-444444444444");
    private static final String PARKED = "; parked until its route arrives";

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final IdempotencyGuard idempotencyGuard = mock(IdempotencyGuard.class);
    private final PaymentRouting paymentRouting = mock(PaymentRouting.class);
    private final NotificationDispatcher dispatcher = mock(NotificationDispatcher.class);
    private final PaymentEventConsumer consumer =
            new PaymentEventConsumer(objectMapper, idempotencyGuard, paymentRouting, dispatcher);

    @BeforeEach
    void newEventsByDefault() {
        when(idempotencyGuard.recordIfNew(anyString(), anyString())).thenReturn(true);
    }

    static String invoiceCreatedJson() {
        return "{\"invoiceUuid\":\"" + INVOICE + "\",\"merchantAccountUuid\":\"" + MERCHANT + "\","
                + "\"chain\":\"ETHEREUM\",\"tokenContractAddress\":\"0xtoken\",\"tokenDecimals\":6,"
                + "\"amount\":\"1500000\",\"payToAddress\":\"0xpayto\",\"reference\":\"order-1\","
                + "\"expiresAt\":\"2026-10-19T01:00:00Z\"}";
    }

    static String paymentSeenJson() {
        return "{\"paymentUuid\":\"" + PAYMENT + "\",\"invoiceUuid\":\"" + INVOICE + "\",\"chain\":\"ETHEREUM\","
                + "\"txHash\":\"0xtx\",\"tokenContractAddress\":\"0xtoken\",\"confirmations\":1,"
                + "\"occurredAt\":\"2026-10-19T00:10:00Z\"}";
    }

    static String paymentFinalizedJson() {
        return "{\"paymentUuid\":\"" + PAYMENT + "\",\"chain\":\"ETHEREUM\",\"txHash\":\"0xtx\","
                + "\"fromAddress\":\"0xfrom\",\"toAddress\":\"0xpayto\",\"tokenContractAddress\":\"0xtoken\","
                + "\"amount\":\"1500000\",\"confirmations\":12,\"addressPoisoningFlag\":false,"
                + "\"occurredAt\":\"2026-10-19T00:20:00Z\"}";
    }

    static String receiptIssuedJson(boolean withMerchant) {
        return "{\"invoiceUuid\":\"" + INVOICE + "\",\"paymentUuid\":\"" + PAYMENT + "\",\"receiptUuid\":\"" + RECEIPT + "\","
                + (withMerchant ? "\"merchantAccountUuid\":\"" + MERCHANT + "\"," : "")
                + "\"chain\":\"ETHEREUM\",\"txHash\":\"0xtx\",\"amount\":\"1500000\",\"tokenContractAddress\":\"0xtoken\","
                + "\"kmsKeyId\":\"kms-1\",\"receiptUri\":\"s3://receipts-bucket/receipts/" + RECEIPT + ".json\","
                + "\"occurredAt\":\"2026-10-19T00:30:00Z\"}";
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> dispatchedData(String kind) {
        ArgumentCaptor<Map<String, String>> data = ArgumentCaptor.forClass(Map.class);
        verify(dispatcher).dispatch(eq(MERCHANT), eq(kind), data.capture());
        return data.getValue();
    }

    /**
     * {@code package.md} §8 name, kept literally. It asserts merchant-only delivery: a payer has
     * no account or contact on the platform, so Q7 was decided as merchant-only at T07 Phase 4
     * (payer notification is follow-up FU-T07-1).
     */
    @Test
    void shouldNotifyBothPartiesOnInvoiceCreated() throws Exception {
        consumer.onInvoiceCreated(invoiceCreatedJson());

        verify(paymentRouting).recordInvoice(INVOICE, MERCHANT, (short) 6);
        Map<String, String> data = dispatchedData("invoice.created");
        assertThat(data).containsEntry("sourceEventKey", "invoice.created:" + INVOICE)
                .containsEntry("invoiceUuid", INVOICE.toString())
                .containsEntry("invoiceId", INVOICE.toString())
                .containsEntry("chain", "ETHEREUM")
                .containsEntry("amount", "1.5");
        assertThat(data.values()).as("no wallet address reaches a template").doesNotContain("0xpayto");
    }

    @Test
    void shouldNotifyOnPaymentSeen() throws Exception {
        when(paymentRouting.findMerchantForInvoice(INVOICE)).thenReturn(Optional.of(MERCHANT));

        consumer.onPaymentSeen(paymentSeenJson());

        var order = inOrder(idempotencyGuard, paymentRouting, dispatcher);
        order.verify(idempotencyGuard).recordIfNew("payment.seen:" + PAYMENT, "payment.seen");
        order.verify(paymentRouting).recordPayment(PAYMENT, INVOICE);
        order.verify(dispatcher).dispatch(eq(MERCHANT), eq("payment.seen"), anyMap());
        assertThat(dispatchedData("payment.seen")).containsEntry("invoiceId", INVOICE.toString())
                .containsEntry("txHash", "0xtx").containsEntry("sourceEventKey", "payment.seen:" + PAYMENT);
    }

    @Test
    void shouldNotifyTheRoutedMerchantOnPaymentFinalizedWithHumanAmount() throws Exception {
        when(paymentRouting.findInvoiceForPayment(PAYMENT)).thenReturn(Optional.of(INVOICE));
        when(paymentRouting.findMerchantForInvoice(INVOICE)).thenReturn(Optional.of(MERCHANT));
        when(paymentRouting.findTokenDecimals(INVOICE)).thenReturn(Optional.of(6));

        consumer.onPaymentFinalized(paymentFinalizedJson());

        Map<String, String> data = dispatchedData("payment.finalized");
        assertThat(data).containsEntry("invoiceId", INVOICE.toString()).containsEntry("amount", "1.5")
                .containsEntry("txHash", "0xtx").containsEntry("sourceEventKey", "payment.finalized:" + PAYMENT);
        assertThat(data.values()).as("no wallet address reaches a template").doesNotContain("0xfrom", "0xpayto");
    }

    @Test
    void shouldLabelBaseUnitsWhenTheInvoiceDecimalsAreUnknown() throws Exception {
        when(paymentRouting.findInvoiceForPayment(PAYMENT)).thenReturn(Optional.of(INVOICE));
        when(paymentRouting.findMerchantForInvoice(INVOICE)).thenReturn(Optional.of(MERCHANT));
        when(paymentRouting.findTokenDecimals(INVOICE)).thenReturn(Optional.empty());

        consumer.onPaymentFinalized(paymentFinalizedJson());

        assertThat(dispatchedData("payment.finalized")).containsEntry("amount", "1500000 base units");
    }

    /**
     * {@code package.md} §8 name. Since FU-T07-2 (2026-10-19) the consumer supplies
     * {@code receiptUuid}, from which {@code TemplateRenderer} computes the link; it never supplies
     * the link itself, nor the internal {@code s3://} {@code receiptUri}.
     */
    @Test
    void shouldNotifyWithReceiptLinkOnReceiptIssued() throws Exception {
        consumer.onReceiptIssued(receiptIssuedJson(true));

        Map<String, String> data = dispatchedData("receipt.issued");
        assertThat(data).containsEntry("receiptId", RECEIPT.toString())
                .containsEntry("invoiceId", INVOICE.toString())
                .containsEntry("sourceEventKey", "receipt.issued:" + RECEIPT)
                .containsEntry("receiptUuid", RECEIPT.toString())
                .doesNotContainKey("receiptLink")
                .doesNotContainKey("receiptUri");
        assertThat(data.values()).noneMatch(value -> value.startsWith("s3://"));
        verify(paymentRouting).recordPayment(PAYMENT, INVOICE);
        verify(paymentRouting).recordInvoice(INVOICE, MERCHANT, null);
    }

    @Test
    void shouldFallBackToTheRoutedMerchantWhenReceiptIssuedOmitsIt() throws Exception {
        when(paymentRouting.findMerchantForInvoice(INVOICE)).thenReturn(Optional.of(MERCHANT));

        consumer.onReceiptIssued(receiptIssuedJson(false));

        dispatchedData("receipt.issued");
        verify(paymentRouting, never()).recordInvoice(any(), any(), any());
    }

    @Test
    void shouldNotDispatchWhenNoMerchantRouteIsKnown() throws Exception {
        when(paymentRouting.findMerchantForInvoice(INVOICE)).thenReturn(Optional.empty());
        when(paymentRouting.findInvoiceForPayment(PAYMENT)).thenReturn(Optional.empty());

        consumer.onPaymentSeen(paymentSeenJson());
        consumer.onPaymentFinalized(paymentFinalizedJson());
        consumer.onReceiptIssued(receiptIssuedJson(false));

        verifyNoInteractions(dispatcher);
        // The route the event itself carries is still recorded, so later events can use it.
        verify(paymentRouting, org.mockito.Mockito.times(2)).recordPayment(PAYMENT, INVOICE);
        // FU-T07-4: each one is parked with its raw JSON, for a later route to replay.
        verify(paymentRouting).park("payment.seen:" + PAYMENT, "payment.seen", paymentSeenJson(), INVOICE, PAYMENT);
        verify(paymentRouting).park("payment.finalized:" + PAYMENT, "payment.finalized", paymentFinalizedJson(),
                null, PAYMENT);
        verify(paymentRouting).park("receipt.issued:" + RECEIPT, "receipt.issued", receiptIssuedJson(false),
                INVOICE, PAYMENT);
    }

    /** FU-T07-4: invoice.created dispatches itself, then replays what was waiting for it, oldest
     * first, removing each parked event it dispatches. */
    @Test
    void invoiceCreatedReplaysParkedEventsInOrderAndReleasesThem() throws Exception {
        when(paymentRouting.findParkedFor(INVOICE)).thenReturn(List.of(
                new ParkedEvent("payment.seen:" + PAYMENT, "payment.seen", paymentSeenJson()),
                new ParkedEvent("payment.finalized:" + PAYMENT, "payment.finalized", paymentFinalizedJson())));
        when(paymentRouting.findMerchantForInvoice(INVOICE)).thenReturn(Optional.of(MERCHANT));
        when(paymentRouting.findInvoiceForPayment(PAYMENT)).thenReturn(Optional.of(INVOICE));
        when(paymentRouting.findTokenDecimals(INVOICE)).thenReturn(Optional.of(6));

        consumer.onInvoiceCreated(invoiceCreatedJson());

        InOrder order = inOrder(dispatcher, paymentRouting);
        order.verify(dispatcher).dispatch(eq(MERCHANT), eq("invoice.created"), anyMap());
        order.verify(dispatcher).dispatch(eq(MERCHANT), eq("payment.seen"),
                argThat(data -> ("payment.seen:" + PAYMENT).equals(data.get("sourceEventKey"))));
        order.verify(paymentRouting).releaseParked("payment.seen:" + PAYMENT);
        order.verify(dispatcher).dispatch(eq(MERCHANT), eq("payment.finalized"),
                argThat(data -> "1.5".equals(data.get("amount"))));
        order.verify(paymentRouting).releaseParked("payment.finalized:" + PAYMENT);
    }

    /** A parked event that still can't be routed stays parked: no dispatch, no release. */
    @Test
    void aParkedEventThatStillHasNoRouteStaysParked() throws Exception {
        when(paymentRouting.findParkedFor(INVOICE)).thenReturn(List.of(
                new ParkedEvent("payment.finalized:" + PAYMENT, "payment.finalized", paymentFinalizedJson())));
        when(paymentRouting.findInvoiceForPayment(PAYMENT)).thenReturn(Optional.empty());

        consumer.onInvoiceCreated(invoiceCreatedJson());

        verify(dispatcher, never()).dispatch(any(), eq("payment.finalized"), anyMap());
        verify(paymentRouting, never()).releaseParked(anyString());
    }

    /** receipt.issued replays earlier parked events before dispatching itself, so the merchant
     * sees seen, finalized, then receipt. */
    @Test
    void receiptIssuedReplaysParkedEventsBeforeItsOwnDispatch() throws Exception {
        when(paymentRouting.findParkedFor(INVOICE)).thenReturn(List.of(
                new ParkedEvent("payment.seen:" + PAYMENT, "payment.seen", paymentSeenJson())));
        when(paymentRouting.findMerchantForInvoice(INVOICE)).thenReturn(Optional.of(MERCHANT));

        consumer.onReceiptIssued(receiptIssuedJson(true));

        InOrder order = inOrder(dispatcher);
        order.verify(dispatcher).dispatch(eq(MERCHANT), eq("payment.seen"), anyMap());
        order.verify(dispatcher).dispatch(eq(MERCHANT), eq("receipt.issued"), anyMap());
        verify(paymentRouting).releaseParked("payment.seen:" + PAYMENT);
    }

    /** A dispatched payment.seen also replays, since its payment route can unblock a
     * payment.finalized that arrived first. A parked one doesn't replay: nothing new resolves. */
    @Test
    void paymentSeenReplaysOnlyWhenItDispatches() throws Exception {
        when(paymentRouting.findMerchantForInvoice(INVOICE)).thenReturn(Optional.empty());
        consumer.onPaymentSeen(paymentSeenJson());
        verify(paymentRouting, never()).findParkedFor(any());

        when(paymentRouting.findMerchantForInvoice(INVOICE)).thenReturn(Optional.of(MERCHANT));
        consumer.onPaymentSeen(paymentSeenJson().replace(PAYMENT.toString(), RECEIPT.toString()));
        verify(paymentRouting).findParkedFor(INVOICE);
    }

    @Test
    void shouldDoNothingForARedeliveredEvent() throws Exception {
        when(idempotencyGuard.recordIfNew(anyString(), anyString())).thenReturn(false);

        consumer.onInvoiceCreated(invoiceCreatedJson());
        consumer.onPaymentSeen(paymentSeenJson());
        consumer.onPaymentFinalized(paymentFinalizedJson());
        consumer.onReceiptIssued(receiptIssuedJson(true));

        verifyNoInteractions(dispatcher, paymentRouting);
    }

    @Test
    void shouldKeyEachEventByTypeAndAggregate() throws Exception {
        when(paymentRouting.findMerchantForInvoice(any())).thenReturn(Optional.of(MERCHANT));
        when(paymentRouting.findInvoiceForPayment(any())).thenReturn(Optional.of(INVOICE));

        consumer.onInvoiceCreated(invoiceCreatedJson());
        consumer.onPaymentSeen(paymentSeenJson());
        consumer.onPaymentFinalized(paymentFinalizedJson());
        consumer.onReceiptIssued(receiptIssuedJson(true));

        verify(idempotencyGuard).recordIfNew("invoice.created:" + INVOICE, "invoice.created");
        verify(idempotencyGuard).recordIfNew("payment.seen:" + PAYMENT, "payment.seen");
        verify(idempotencyGuard).recordIfNew("payment.finalized:" + PAYMENT, "payment.finalized");
        verify(idempotencyGuard).recordIfNew("receipt.issued:" + RECEIPT, "receipt.issued");
    }

    @Test
    void shouldNotDispatchAnInvoiceEventWithoutAMerchant() throws Exception {
        consumer.onInvoiceCreated(invoiceCreatedJson().replace("\"merchantAccountUuid\":\"" + MERCHANT + "\",", ""));

        verifyNoInteractions(dispatcher, paymentRouting);
    }

    /** Phase 9 (Kimi F3, self-review S3): an event without its aggregate UUID is logged and dropped
     * without being recorded. Otherwise every such event would dedupe against one shared
     * {@code <type>:null} key and later ones would vanish without a log line. */
    @Test
    void shouldNotRecordAnEventThatNamesNoAggregate() throws Exception {
        consumer.onInvoiceCreated(invoiceCreatedJson().replace("\"invoiceUuid\":\"" + INVOICE + "\",", ""));
        consumer.onPaymentSeen(paymentSeenJson().replace("\"paymentUuid\":\"" + PAYMENT + "\",", ""));
        consumer.onPaymentFinalized(paymentFinalizedJson().replace("\"paymentUuid\":\"" + PAYMENT + "\",", ""));
        consumer.onReceiptIssued(receiptIssuedJson(true).replace("\"receiptUuid\":\"" + RECEIPT + "\",", ""));

        verifyNoInteractions(idempotencyGuard, paymentRouting, dispatcher);
    }

    /** Phase 9 (Kimi F7, F9, L4): the not-dispatched log lines carry only the topic, key and UUIDs.
     * Each event here is fully populated and takes a logging branch; none of the payload's
     * addresses, receipt URI, KMS key, amount or reference may appear in what is logged. */
    @Test
    void shouldNeverLogAddressesReceiptUriOrAmounts() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(PaymentEventConsumer.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            when(paymentRouting.findMerchantForInvoice(INVOICE)).thenReturn(Optional.empty());
            when(paymentRouting.findInvoiceForPayment(PAYMENT)).thenReturn(Optional.empty());

            consumer.onInvoiceCreated(invoiceCreatedJson().replace("\"merchantAccountUuid\":\"" + MERCHANT + "\",", ""));
            consumer.onPaymentSeen(paymentSeenJson());
            consumer.onPaymentFinalized(paymentFinalizedJson());
            consumer.onReceiptIssued(receiptIssuedJson(false));
            consumer.onReceiptIssued(receiptIssuedJson(true).replace("\"receiptUuid\":\"" + RECEIPT + "\",", ""));
        } finally {
            logger.detachAppender(appender);
        }

        List<String> lines = appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(lines).hasSize(5);
        // Phase 11 Gap 3: each drop is visible at INFO, names its topic and key, and says why.
        assertThat(appender.list).allSatisfy(event ->
                assertThat(event.getLevel()).isEqualTo(ch.qos.logback.classic.Level.INFO));
        assertThat(lines).allSatisfy(line -> assertThat(line).contains("not dispatched"));
        assertThat(lines).containsExactly(
                "Payment event on payments.invoice.created with key invoice.created:" + INVOICE
                        + " not dispatched: it names no merchant",
                "Payment event on payments.payment.seen with key payment.seen:" + PAYMENT
                        + " not dispatched: no merchant route for invoice " + INVOICE + PARKED,
                "Payment event on payments.payment.finalized with key payment.finalized:" + PAYMENT
                        + " not dispatched: no route for payment " + PAYMENT + PARKED,
                "Payment event on payments.receipt.issued with key receipt.issued:" + RECEIPT
                        + " not dispatched: no merchant route for invoice " + INVOICE + PARKED,
                "Payment event on payments.receipt.issued with key receipt.issued:null"
                        + " not dispatched: it names no receipt; not recorded");
        assertThat(lines).allSatisfy(line -> assertThat(line).doesNotContain(
                "0xpayto", "0xfrom", "0xtoken", "0xtx", "s3://", "receipts-bucket", "kms-1",
                "1500000", "order-1", "{"));
    }

    /** Phase 10 (AC6): an event with a valid key but no invoice is recorded once (so a redelivery
     * is a no-op) and neither routed nor dispatched. */
    @Test
    void shouldRecordButNotRouteOrDispatchAnEventThatNamesNoInvoice() throws Exception {
        consumer.onPaymentSeen(paymentSeenJson().replace("\"invoiceUuid\":\"" + INVOICE + "\",", ""));
        consumer.onReceiptIssued(receiptIssuedJson(true).replace("\"invoiceUuid\":\"" + INVOICE + "\",", ""));

        verify(idempotencyGuard).recordIfNew("payment.seen:" + PAYMENT, "payment.seen");
        verify(idempotencyGuard).recordIfNew("receipt.issued:" + RECEIPT, "receipt.issued");
        verifyNoInteractions(paymentRouting, dispatcher);
    }

    /** Phase 10 (AC3): the event's own merchantAccountUuid is the authority; the routed merchant
     * is only a fallback for when the event omits it. */
    @Test
    void shouldPreferTheReceiptEventsMerchantOverARoutedOne() throws Exception {
        UUID otherMerchant = UUID.fromString("55555555-5555-5555-5555-555555555555");
        when(paymentRouting.findMerchantForInvoice(INVOICE)).thenReturn(Optional.of(otherMerchant));

        consumer.onReceiptIssued(receiptIssuedJson(true));

        verify(dispatcher).dispatch(eq(MERCHANT), eq("receipt.issued"), anyMap());
        verify(dispatcher, never()).dispatch(eq(otherMerchant), anyString(), anyMap());
    }

    /** Phase 10 (AC4, AC6): payment.finalized resolves in two steps. A known payment route whose
     * invoice route is missing still resolves no merchant, so nothing is dispatched. */
    @Test
    void shouldNotDispatchPaymentFinalizedWhenOnlyTheSecondRouteIsMissing() throws Exception {
        when(paymentRouting.findInvoiceForPayment(PAYMENT)).thenReturn(Optional.of(INVOICE));
        when(paymentRouting.findMerchantForInvoice(INVOICE)).thenReturn(Optional.empty());

        consumer.onPaymentFinalized(paymentFinalizedJson());

        verify(paymentRouting).findMerchantForInvoice(INVOICE);
        verifyNoInteractions(dispatcher);
        verify(paymentRouting).park("payment.finalized:" + PAYMENT, "payment.finalized", paymentFinalizedJson(),
                INVOICE, PAYMENT);
    }

    /** Phase 10: malformed JSON propagates, the same disposition as AuthEventConsumer (the
     * container's default error handler retries, then skips). Nothing is recorded first, so the
     * retry isn't swallowed by dedupe. */
    @Test
    void shouldThrowOnMalformedJsonWithoutRecordingAnything() {
        assertThatThrownBy(() -> consumer.onInvoiceCreated("{not valid json"))
                .isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
        assertThatThrownBy(() -> consumer.onPaymentSeen("{not valid json"))
                .isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
        assertThatThrownBy(() -> consumer.onPaymentFinalized("{not valid json"))
                .isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);
        assertThatThrownBy(() -> consumer.onReceiptIssued("{not valid json"))
                .isInstanceOf(com.fasterxml.jackson.core.JsonProcessingException.class);

        verifyNoInteractions(idempotencyGuard, paymentRouting, dispatcher);
    }

    /** Phase 11 Gaps 4 and 5: missing decimals or a missing or unparseable amount never throws
     * (a throw would mean endless redelivery). The route is still recorded and the merchant is
     * still notified, with the amount left out or passed through raw. */
    @Test
    @SuppressWarnings("unchecked")
    void shouldStillRouteAndDispatchWhenDecimalsOrAmountAreMissingOrUnparseable() throws Exception {
        consumer.onInvoiceCreated(invoiceCreatedJson()
                .replace("\"tokenDecimals\":6", "\"tokenDecimals\":null")
                .replace("\"amount\":\"1500000\"", "\"amount\":null"));
        consumer.onInvoiceCreated(invoiceCreatedJson().replace("\"amount\":\"1500000\"", "\"amount\":\"not-a-number\""));

        verify(paymentRouting).recordInvoice(INVOICE, MERCHANT, null);
        verify(paymentRouting).recordInvoice(INVOICE, MERCHANT, (short) 6);
        ArgumentCaptor<Map<String, String>> data = ArgumentCaptor.forClass(Map.class);
        verify(dispatcher, org.mockito.Mockito.times(2)).dispatch(eq(MERCHANT), eq("invoice.created"), data.capture());
        assertThat(data.getAllValues().get(0)).doesNotContainKey("amount").containsEntry("invoiceId", INVOICE.toString());
        assertThat(data.getAllValues().get(1)).containsEntry("amount", "not-a-number");
    }

    /** L1: each listener is transactional and bound to its real topic. */
    @Test
    void everyListenerIsTransactionalOnItsPaymentsTopic() {
        Map<String, String> expected = Map.of(
                "onInvoiceCreated", "payments.invoice.created",
                "onPaymentSeen", "payments.payment.seen",
                "onPaymentFinalized", "payments.payment.finalized",
                "onReceiptIssued", "payments.receipt.issued");
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            Method method = Arrays.stream(PaymentEventConsumer.class.getMethods())
                    .filter(m -> m.getName().equals(entry.getKey())).findFirst().orElseThrow();
            assertThat(method.getAnnotation(KafkaListener.class).topics()).containsExactly(entry.getValue());
            // Phase 11 Gap 1: the shared, configured group, so replicas split partitions.
            assertThat(method.getAnnotation(KafkaListener.class).groupId()).as(entry.getKey())
                    .isEqualTo("${spring.kafka.consumer.group-id}");
            assertThat(method.getAnnotation(Transactional.class)).as(entry.getKey()).isNotNull();
        }
    }
}
