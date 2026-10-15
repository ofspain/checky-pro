package com.themistra.payment.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.themistra.payment.common.CryptoClient;
import com.themistra.payment.common.CryptoServiceUnavailableException;
import com.themistra.payment.common.crypto.RegisterWatchResponse;
import com.themistra.payment.invoice.InvoiceService;
import com.themistra.payment.invoice.InvoiceState;
import com.themistra.payment.invoice.InvoiceWatchView;
import com.themistra.payment.payment.FinalizationOutcome;
import com.themistra.payment.payment.PaymentService;
import com.themistra.payment.payment.PaymentState;
import com.themistra.payment.payment.ReorgOutcome;
import com.themistra.payment.receipt.ReceiptService;
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

    private static final String REORGED_JSON = """
            {"idempotencyKey":"ETHEREUM:0xabc:chain.tx.reorged","watchId":"%s","invoiceUuid":"%s",
             "chain":"ETHEREUM","txHash":"0xabc","tokenContractAddress":"0x111",
             "occurredAt":"2026-07-13T00:00:00Z"}
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
    private ReceiptService receiptService;

    @Mock
    private PlatformTransactionManager transactionManager;

    private ChainEventConsumer consumer;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        consumer = new ChainEventConsumer(objectMapper, processedEventRepository, invoiceService, paymentService,
                cryptoClient, receiptService, Clock.fixed(NOW, ZoneOffset.UTC), transactionManager);
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

        // Phase 11 Gap 6 (Kimi, narrowed - a dedupe test already existed, it just didn't assert
        // on this T13-introduced intermediate call).
        verify(paymentService, never()).findInvoiceIdByChainAndTxHash(any(), any());
        verify(paymentService, never()).applyFinalized(any(), any());
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

        verify(paymentService, never()).applyFinalized(any(), any());
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
        when(paymentService.findInvoiceIdByChainAndTxHash("ETHEREUM", "0xabc")).thenReturn(Optional.of(7L));
        when(invoiceService.findByIdAsWatchView(7L)).thenReturn(Optional.of(openInvoice()));

        consumer.onChainTxFinalized(FINALIZED_JSON);

        org.mockito.ArgumentCaptor<com.themistra.payment.consumer.dto.ChainTxFinalized> captor =
                org.mockito.ArgumentCaptor.forClass(com.themistra.payment.consumer.dto.ChainTxFinalized.class);
        verify(paymentService).applyFinalized(any(), captor.capture());
        assertThat(captor.getValue().chain()).isEqualTo("ETHEREUM");
        assertThat(captor.getValue().txHash()).isEqualTo("0xabc");
        assertThat(captor.getValue().amount()).isEqualByComparingTo("1000");
        assertThat(captor.getValue().confirmations()).isEqualTo(12);
    }

    // --- T13: invoice-outcome marking after FINALIZED ---

    @Test
    void shouldLogAndDropChainTxFinalizedForAnUnknownPayment() {
        when(processedEventRepository.insertIfNew("ETHEREUM:0xabc:chain.tx.finalized", NOW)).thenReturn(1);
        when(paymentService.findInvoiceIdByChainAndTxHash("ETHEREUM", "0xabc")).thenReturn(Optional.empty());

        consumer.onChainTxFinalized(FINALIZED_JSON);

        verify(invoiceService, never()).findByIdAsWatchView(any());
        verify(paymentService, never()).applyFinalized(any(), any());
        verify(invoiceService, never()).markOutcomeIfOpenOrExpired(any(), any(), any());
    }

    @Test
    void shouldLogAndDropChainTxFinalizedWhenInvoiceIsMissing() {
        when(processedEventRepository.insertIfNew("ETHEREUM:0xabc:chain.tx.finalized", NOW)).thenReturn(1);
        when(paymentService.findInvoiceIdByChainAndTxHash("ETHEREUM", "0xabc")).thenReturn(Optional.of(7L));
        when(invoiceService.findByIdAsWatchView(7L)).thenReturn(Optional.empty());

        consumer.onChainTxFinalized(FINALIZED_JSON);

        verify(paymentService, never()).applyFinalized(any(), any());
        verify(invoiceService, never()).markOutcomeIfOpenOrExpired(any(), any(), any());
    }

    @Test
    void shouldNotMarkInvoiceOutcomeWhenFinalizedOutcomeIsEmpty() {
        when(processedEventRepository.insertIfNew("ETHEREUM:0xabc:chain.tx.finalized", NOW)).thenReturn(1);
        when(paymentService.findInvoiceIdByChainAndTxHash("ETHEREUM", "0xabc")).thenReturn(Optional.of(7L));
        when(invoiceService.findByIdAsWatchView(7L)).thenReturn(Optional.of(openInvoice()));
        when(paymentService.applyFinalized(any(), any())).thenReturn(Optional.empty());

        consumer.onChainTxFinalized(FINALIZED_JSON);

        verify(invoiceService, never()).markOutcomeIfOpenOrExpired(any(), any(), any());
    }

    @Test
    void shouldMarkInvoicePaidOnExactMatch() {
        when(processedEventRepository.insertIfNew("ETHEREUM:0xabc:chain.tx.finalized", NOW)).thenReturn(1);
        when(paymentService.findInvoiceIdByChainAndTxHash("ETHEREUM", "0xabc")).thenReturn(Optional.of(7L));
        when(invoiceService.findByIdAsWatchView(7L)).thenReturn(Optional.of(openInvoice()));
        when(paymentService.applyFinalized(any(), any())).thenReturn(Optional.of(new FinalizationOutcome(99L, 7L, null)));

        consumer.onChainTxFinalized(FINALIZED_JSON);

        verify(invoiceService).markOutcomeIfOpenOrExpired(7L, InvoiceState.PAID, NOW);
    }

    @Test
    void shouldMarkInvoiceUnderpaidOnUnderpayment() {
        when(processedEventRepository.insertIfNew("ETHEREUM:0xabc:chain.tx.finalized", NOW)).thenReturn(1);
        when(paymentService.findInvoiceIdByChainAndTxHash("ETHEREUM", "0xabc")).thenReturn(Optional.of(7L));
        when(invoiceService.findByIdAsWatchView(7L)).thenReturn(Optional.of(openInvoice()));
        when(paymentService.applyFinalized(any(), any()))
                .thenReturn(Optional.of(new FinalizationOutcome(99L, 7L, "UNDERPAID")));

        consumer.onChainTxFinalized(FINALIZED_JSON);

        verify(invoiceService).markOutcomeIfOpenOrExpired(7L, InvoiceState.UNDERPAID, NOW);
    }

    @Test
    void shouldMarkInvoiceOverpaidOnOverpayment() {
        when(processedEventRepository.insertIfNew("ETHEREUM:0xabc:chain.tx.finalized", NOW)).thenReturn(1);
        when(paymentService.findInvoiceIdByChainAndTxHash("ETHEREUM", "0xabc")).thenReturn(Optional.of(7L));
        when(invoiceService.findByIdAsWatchView(7L)).thenReturn(Optional.of(openInvoice()));
        when(paymentService.applyFinalized(any(), any()))
                .thenReturn(Optional.of(new FinalizationOutcome(99L, 7L, "OVERPAID")));

        consumer.onChainTxFinalized(FINALIZED_JSON);

        verify(invoiceService).markOutcomeIfOpenOrExpired(7L, InvoiceState.OVERPAID, NOW);
    }

    /** Phase 4 Finding 1 (T13): a {@code false} return from {@code markOutcomeIfOpenOrExpired}
     * (the invoice was in neither {@code OPEN} nor {@code EXPIRED}) is logged, not treated as an
     * error - mirrors {@code InvoiceService#expireInvoice}'s own identical convention.
     *
     * <p>Phase 11 Gap 4 (Kimi): a {@code ListAppender} (mirrors {@code
     * InvoiceExpirySweepJobTest}'s own identical pattern) proves the warning is actually
     * logged, not silently swallowed - asserting "does not throw" alone would also pass for a
     * no-op. */
    @Test
    void shouldLogWhenInvoiceOutcomeIsNotApplied() {
        when(processedEventRepository.insertIfNew("ETHEREUM:0xabc:chain.tx.finalized", NOW)).thenReturn(1);
        when(paymentService.findInvoiceIdByChainAndTxHash("ETHEREUM", "0xabc")).thenReturn(Optional.of(7L));
        when(invoiceService.findByIdAsWatchView(7L)).thenReturn(Optional.of(openInvoice()));
        when(paymentService.applyFinalized(any(), any())).thenReturn(Optional.of(new FinalizationOutcome(99L, 7L, null)));
        when(invoiceService.markOutcomeIfOpenOrExpired(7L, InvoiceState.PAID, NOW)).thenReturn(false);

        org.slf4j.Logger slf4jLogger = org.slf4j.LoggerFactory.getLogger(ChainEventConsumer.class);
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger) slf4jLogger;
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            consumer.onChainTxFinalized(FINALIZED_JSON);

            assertThat(appender.list).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(ch.qos.logback.classic.Level.WARN);
                assertThat(event.getFormattedMessage())
                        .contains("7")
                        .contains("not OPEN or EXPIRED");
            });
        } finally {
            logger.detachAppender(appender);
        }
    }

    private static com.themistra.payment.consumer.dto.ChainTxConfirmed argThatConfirmedEventHasConfirmations(int expected) {
        return org.mockito.ArgumentMatchers.argThat(event -> {
            assertThat(event.confirmations()).isEqualTo(expected);
            return true;
        });
    }

    // --- R11/R12 (T12) ---

    private static ReorgOutcome seenOutcome() {
        return new ReorgOutcome(99L, 7L, PaymentState.SEEN);
    }

    private static ReorgOutcome watchingOutcome() {
        return new ReorgOutcome(99L, 7L, PaymentState.WATCHING);
    }

    @Test
    void shouldDedupeDuplicateChainEventsByEventKeyOnReorg() {
        when(processedEventRepository.insertIfNew("ETHEREUM:0xabc:chain.tx.reorged", NOW)).thenReturn(0);

        consumer.onChainTxReorged(REORGED_JSON);

        verify(paymentService, never()).applyReorged(any());
        verify(invoiceService, never()).findByIdAsWatchView(any());
        verify(cryptoClient, never()).registerWatch(any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldLogAndDropAMalformedReorgedPayloadWithoutPropagating() {
        org.assertj.core.api.Assertions.assertThatCode(() -> consumer.onChainTxReorged("not valid json{{{"))
                .doesNotThrowAnyException();

        verify(paymentService, never()).applyReorged(any());
    }

    /** R11: a CONFIRMING -> SEEN reversal needs no watch action at all. */
    @Test
    void shouldNotRegisterReplacementWatchWhenReorgWalksBackToSeen() {
        when(processedEventRepository.insertIfNew("ETHEREUM:0xabc:chain.tx.reorged", NOW)).thenReturn(1);
        when(paymentService.applyReorged(any())).thenReturn(Optional.of(seenOutcome()));

        consumer.onChainTxReorged(REORGED_JSON);

        verify(invoiceService, never()).findByIdAsWatchView(any());
        verify(cryptoClient, never()).registerWatch(any(), any(), any(), any(), any(), any());
    }

    /** R14 continuity: a stale/unknown reorg event means {@code applyReorged} itself returned
     * empty - the replacement-watch step must never even attempt a lookup. */
    @Test
    void shouldNotRegisterReplacementWatchWhenReorgIsStaleOrUnknown() {
        when(processedEventRepository.insertIfNew("ETHEREUM:0xabc:chain.tx.reorged", NOW)).thenReturn(1);
        when(paymentService.applyReorged(any())).thenReturn(Optional.empty());

        consumer.onChainTxReorged(REORGED_JSON);

        verify(invoiceService, never()).findByIdAsWatchView(any());
        verify(cryptoClient, never()).registerWatch(any(), any(), any(), any(), any(), any());
    }

    /** R12, named. Phase 9 Finding 1: looks up the invoice by {@code ReorgOutcome.invoiceId()}
     * (7L here), never by the event's own {@code watchId}. */
    @Test
    void shouldRegisterReplacementWatchAfterWalkingBackToWatching() {
        when(processedEventRepository.insertIfNew("ETHEREUM:0xabc:chain.tx.reorged", NOW)).thenReturn(1);
        when(paymentService.applyReorged(any())).thenReturn(Optional.of(watchingOutcome()));
        when(invoiceService.findByIdAsWatchView(7L)).thenReturn(Optional.of(openInvoice()));
        UUID newWatchId = UUID.randomUUID();
        when(cryptoClient.registerWatch(INVOICE_UUID, "ETHEREUM", "0xdef", "0x111", BigDecimal.valueOf(1000),
                NOW.plusSeconds(3600))).thenReturn(new RegisterWatchResponse(newWatchId, "REGISTERED"));
        when(invoiceService.updateWatchIdIfOpen(7L, newWatchId, NOW)).thenReturn(true);

        consumer.onChainTxReorged(REORGED_JSON);

        verify(cryptoClient).registerWatch(INVOICE_UUID, "ETHEREUM", "0xdef", "0x111", BigDecimal.valueOf(1000),
                NOW.plusSeconds(3600));
        verify(invoiceService).updateWatchIdIfOpen(7L, newWatchId, NOW);
        verify(cryptoClient, never()).unregisterWatch(any());
    }

    /** Phase 9 Finding 7: short-circuits before even calling {@code registerWatch} if the
     * invoice is no longer OPEN. */
    @Test
    void shouldSkipRegistrationWhenInvoiceIsNoLongerOpen() {
        when(processedEventRepository.insertIfNew("ETHEREUM:0xabc:chain.tx.reorged", NOW)).thenReturn(1);
        when(paymentService.applyReorged(any())).thenReturn(Optional.of(watchingOutcome()));
        InvoiceWatchView expiredInvoice = new InvoiceWatchView(7L, INVOICE_UUID, InvoiceState.EXPIRED, "0xdef",
                WATCH_ID, "ETHEREUM", "0x111", BigDecimal.valueOf(1000), NOW.plusSeconds(3600));
        when(invoiceService.findByIdAsWatchView(7L)).thenReturn(Optional.of(expiredInvoice));

        consumer.onChainTxReorged(REORGED_JSON);

        verify(cryptoClient, never()).registerWatch(any(), any(), any(), any(), any(), any());
        verify(invoiceService, never()).updateWatchIdIfOpen(any(), any(), any());
    }

    /** Phase 9 Finding 3: a successful register whose {@code updateWatchIdIfOpen} still returns
     * {@code false} (the invoice resolved in the narrow window between the state check and this
     * update) must not leak the newly registered watch. */
    @Test
    void shouldUnregisterOrphanedWatchWhenUpdateWatchIdFailsAfterSuccessfulRegister() {
        when(processedEventRepository.insertIfNew("ETHEREUM:0xabc:chain.tx.reorged", NOW)).thenReturn(1);
        when(paymentService.applyReorged(any())).thenReturn(Optional.of(watchingOutcome()));
        when(invoiceService.findByIdAsWatchView(7L)).thenReturn(Optional.of(openInvoice()));
        UUID newWatchId = UUID.randomUUID();
        when(cryptoClient.registerWatch(any(), any(), any(), any(), any(), any()))
                .thenReturn(new RegisterWatchResponse(newWatchId, "REGISTERED"));
        when(invoiceService.updateWatchIdIfOpen(any(), any(), any())).thenReturn(false);

        consumer.onChainTxReorged(REORGED_JSON);

        verify(cryptoClient).unregisterWatch(newWatchId);
    }

    /** Phase 9 Finding 3 (AC5-equivalent): a {@code registerWatch} failure never rolls back the
     * already-committed DB-side reversal - {@code applyReorged} was already called before this
     * best-effort step even starts. */
    @Test
    void shouldSwallowACryptoServiceFailureDuringReplacementWatchRegistrationWithoutPropagating() {
        when(processedEventRepository.insertIfNew("ETHEREUM:0xabc:chain.tx.reorged", NOW)).thenReturn(1);
        when(paymentService.applyReorged(any())).thenReturn(Optional.of(watchingOutcome()));
        when(invoiceService.findByIdAsWatchView(7L)).thenReturn(Optional.of(openInvoice()));
        when(cryptoClient.registerWatch(any(), any(), any(), any(), any(), any()))
                .thenThrow(new CryptoServiceUnavailableException("down", null));

        org.assertj.core.api.Assertions.assertThatCode(() -> consumer.onChainTxReorged(REORGED_JSON))
                .doesNotThrowAnyException();

        verify(paymentService).applyReorged(any());
    }

    /** Phase 9 Finding 2: the catch was widened from only {@code CryptoServiceUnavailableException}
     * to {@code RuntimeException} - this specific exception type from {@code invoiceService}
     * would have propagated uncaught before that fix. */
    @Test
    void shouldSwallowAnUnexpectedRuntimeExceptionFromInvoiceLookupWithoutPropagating() {
        when(processedEventRepository.insertIfNew("ETHEREUM:0xabc:chain.tx.reorged", NOW)).thenReturn(1);
        when(paymentService.applyReorged(any())).thenReturn(Optional.of(watchingOutcome()));
        when(invoiceService.findByIdAsWatchView(7L)).thenThrow(new IllegalStateException("transient failure"));

        org.assertj.core.api.Assertions.assertThatCode(() -> consumer.onChainTxReorged(REORGED_JSON))
                .doesNotThrowAnyException();
    }
}
