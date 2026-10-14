package com.themistra.payment.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.themistra.payment.common.CryptoClient;
import com.themistra.payment.invoice.InvoiceService;
import com.themistra.payment.invoice.InvoiceState;
import com.themistra.payment.invoice.InvoiceWatchView;
import com.themistra.payment.payment.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChainEventConsumerTest {

    private static final Instant NOW = Instant.parse("2026-07-13T00:00:00Z");
    private static final UUID WATCH_ID = UUID.randomUUID();
    private static final UUID INVOICE_UUID = UUID.randomUUID();
    private static final String TRIGGER_EVENT_KEY = "ETHEREUM:0xabc:chain.tx.seen";

    private static final String SEEN_JSON = """
            {"idempotencyKey":"%s","watchId":"%s","invoiceUuid":"%s","chain":"ETHEREUM",
             "txHash":"0xabc","tokenContractAddress":"0x111","confirmations":1,
             "occurredAt":"2026-07-13T00:00:00Z"}
            """.formatted(TRIGGER_EVENT_KEY, WATCH_ID, INVOICE_UUID);

    private static final String CONFIRMED_JSON = """
            {"idempotencyKey":"ETHEREUM:0xabc:chain.tx.confirmed","watchId":"%s","invoiceUuid":"%s",
             "chain":"ETHEREUM","txHash":"0xabc","tokenContractAddress":"0x111","confirmations":3,
             "occurredAt":"2026-07-13T00:00:00Z"}
            """.formatted(WATCH_ID, INVOICE_UUID);

    private static final String FINALIZED_JSON = """
            {"idempotencyKey":"ETHEREUM:0xabc:chain.tx.finalized","watchId":"%s","invoiceUuid":"%s",
             "chain":"ETHEREUM","txHash":"0xabc","fromAddress":"0xfrom","toAddress":"0xto",
             "tokenContractAddress":"0x111","amount":"1000","confirmations":12,
             "addressPoisoningFlag":false,"occurredAt":"2026-07-13T00:00:00Z"}
            """.formatted(WATCH_ID, INVOICE_UUID);

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @Mock
    private InvoiceService invoiceService;

    @Mock
    private PaymentService paymentService;

    @Mock
    private CryptoClient cryptoClient;

    @Mock
    private PlatformTransactionManager transactionManager;

    private ChainEventConsumer consumer;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        consumer = new ChainEventConsumer(objectMapper, processedEventRepository, invoiceService, paymentService,
                cryptoClient, Clock.fixed(NOW, ZoneOffset.UTC), transactionManager);
    }

    private static InvoiceWatchView openInvoice() {
        return new InvoiceWatchView(7L, INVOICE_UUID, InvoiceState.OPEN, "0xdef", WATCH_ID,
                "ETHEREUM", "0x111", BigDecimal.valueOf(1000), NOW.plusSeconds(3600));
    }

    // --- R13/L5, named ---

    @Test
    void shouldDedupeDuplicateChainEventsByEventKeyOnSeen() {
        when(processedEventRepository.insertIfNew(TRIGGER_EVENT_KEY, NOW)).thenReturn(0);

        consumer.onChainTxSeen(SEEN_JSON);

        verify(invoiceService, never()).findByWatchId(any());
        verify(paymentService, never()).applySeen(any(), any());
        verify(cryptoClient, never()).unregisterWatch(any());
    }

    @Test
    void shouldDedupeDuplicateChainEventsByEventKeyOnConfirmed() {
        when(processedEventRepository.insertIfNew("ETHEREUM:0xabc:chain.tx.confirmed", NOW)).thenReturn(0);

        consumer.onChainTxConfirmed(CONFIRMED_JSON);

        verify(paymentService, never()).applyConfirmed(any());
    }

    @Test
    void shouldDedupeDuplicateChainEventsByEventKeyOnFinalized() {
        when(processedEventRepository.insertIfNew("ETHEREUM:0xabc:chain.tx.finalized", NOW)).thenReturn(0);

        consumer.onChainTxFinalized(FINALIZED_JSON);

        verify(paymentService, never()).applyFinalized(any());
    }

    // --- R7a, named ---

    @Test
    void shouldUnregisterWatchOnFirstSeenAndIgnoreSecondMatch() {
        when(processedEventRepository.insertIfNew(TRIGGER_EVENT_KEY, NOW)).thenReturn(1);
        when(invoiceService.findByWatchId(WATCH_ID)).thenReturn(Optional.of(openInvoice()));
        when(paymentService.applySeen(any(), any())).thenReturn(true);

        consumer.onChainTxSeen(SEEN_JSON);

        // Decision 4/Finding 6: unregisterWatch must run strictly after applySeen, inside the
        // same transaction - not merely "both were called."
        InOrder inOrder = inOrder(paymentService, cryptoClient);
        inOrder.verify(paymentService).applySeen(any(), any());
        inOrder.verify(cryptoClient).unregisterWatch(WATCH_ID);
    }

    @Test
    void shouldNotUnregisterWatchWhenApplySeenReturnsFalse() {
        when(processedEventRepository.insertIfNew(TRIGGER_EVENT_KEY, NOW)).thenReturn(1);
        when(invoiceService.findByWatchId(WATCH_ID)).thenReturn(Optional.of(openInvoice()));
        when(paymentService.applySeen(any(), any())).thenReturn(false);

        consumer.onChainTxSeen(SEEN_JSON);

        verify(cryptoClient, never()).unregisterWatch(any());
    }

    @Test
    void shouldLogAndDropWhenWatchIdIsUnknown() {
        when(processedEventRepository.insertIfNew(TRIGGER_EVENT_KEY, NOW)).thenReturn(1);
        when(invoiceService.findByWatchId(WATCH_ID)).thenReturn(Optional.empty());

        consumer.onChainTxSeen(SEEN_JSON);

        verify(paymentService, never()).applySeen(any(), any());
        verify(cryptoClient, never()).unregisterWatch(any());
    }

    // --- Phase 8 Kimi Finding 2 ---

    @Test
    void shouldLogAndDropAMalformedPayloadWithoutPropagating() {
        org.assertj.core.api.Assertions.assertThatCode(() -> consumer.onChainTxSeen("not valid json{{{"))
                .doesNotThrowAnyException();

        verify(processedEventRepository, never()).insertIfNew(any(), any());
    }

    @Test
    void shouldLogAndDropAMalformedConfirmedPayloadWithoutPropagating() {
        org.assertj.core.api.Assertions.assertThatCode(() -> consumer.onChainTxConfirmed("not valid json{{{"))
                .doesNotThrowAnyException();

        verify(paymentService, never()).applyConfirmed(any());
    }

    /** Phase 11 Gap 2 (Kimi): completes the set - `seen`/`confirmed` were already covered. */
    @Test
    void shouldLogAndDropAMalformedFinalizedPayloadWithoutPropagating() {
        org.assertj.core.api.Assertions.assertThatCode(() -> consumer.onChainTxFinalized("not valid json{{{"))
                .doesNotThrowAnyException();

        verify(paymentService, never()).applyFinalized(any());
    }

    // --- basic delegation ---

    @Test
    void shouldDelegateChainTxConfirmedToPaymentService() {
        when(processedEventRepository.insertIfNew("ETHEREUM:0xabc:chain.tx.confirmed", NOW)).thenReturn(1);

        consumer.onChainTxConfirmed(CONFIRMED_JSON);

        verify(paymentService).applyConfirmed(argThatConfirmedEventHasConfirmations(3));
    }

    /** Phase 11 Gap 4 (Kimi): an `ArgumentCaptor`, not a bare `any()` - proves the exact
     * deserialized event (not an empty/wrong one) reaches `PaymentService`. */
    @Test
    void shouldDelegateChainTxFinalizedToPaymentService() {
        when(processedEventRepository.insertIfNew("ETHEREUM:0xabc:chain.tx.finalized", NOW)).thenReturn(1);

        consumer.onChainTxFinalized(FINALIZED_JSON);

        org.mockito.ArgumentCaptor<com.themistra.payment.consumer.dto.ChainTxFinalized> captor =
                org.mockito.ArgumentCaptor.forClass(com.themistra.payment.consumer.dto.ChainTxFinalized.class);
        verify(paymentService).applyFinalized(captor.capture());
        assertThat(captor.getValue().chain()).isEqualTo("ETHEREUM");
        assertThat(captor.getValue().txHash()).isEqualTo("0xabc");
        assertThat(captor.getValue().amount()).isEqualByComparingTo("1000");
        assertThat(captor.getValue().confirmations()).isEqualTo(12);
    }

    private static com.themistra.payment.consumer.dto.ChainTxConfirmed argThatConfirmedEventHasConfirmations(int expected) {
        return org.mockito.ArgumentMatchers.argThat(event -> {
            assertThat(event.confirmations()).isEqualTo(expected);
            return true;
        });
    }
}
