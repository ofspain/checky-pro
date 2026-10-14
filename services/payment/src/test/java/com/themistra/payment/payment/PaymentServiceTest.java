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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Plain JUnit + Mockito, no Spring context, no Docker. Uses a real {@link PaymentStateMachine}
 * (stateless, no dependencies — mirrors {@code VerificationRecordServiceTest}'s own real-{@code
 * Clock} precedent: a genuine instance gives real confidence the two layers actually agree,
 * rather than a mock that would just answer {@code true} for everything).
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-13T00:00:00Z");
    private static final Long INVOICE_ID = 7L;
    private static final UUID INVOICE_UUID = UUID.randomUUID();
    private static final UUID WATCH_ID = UUID.randomUUID();
    private static final String PAY_TO_ADDRESS = "0xdef";
    private static final String CHAIN = "ETHEREUM";
    private static final String TX_HASH = "0xabc";
    private static final String TOKEN_CONTRACT_ADDRESS = "0x111";
    private static final String TRIGGER_EVENT_KEY = CHAIN + ":" + TX_HASH + ":chain.tx.seen";

    @Mock
    private PaymentRepository repository;

    @Mock
    private VerificationRecordService verificationRecordService;

    @Mock
    private OutboxPublisher outboxPublisher;

    private PaymentService service;

    @BeforeEach
    void setUp() {
        service = new PaymentService(repository, new PaymentStateMachine(), verificationRecordService,
                outboxPublisher, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static InvoiceWatchView openInvoice() {
        return new InvoiceWatchView(INVOICE_ID, INVOICE_UUID, InvoiceState.OPEN, PAY_TO_ADDRESS, WATCH_ID);
    }

    private static ChainTxSeen seenEvent() {
        return new ChainTxSeen(TRIGGER_EVENT_KEY, WATCH_ID, INVOICE_UUID, CHAIN, TX_HASH, TOKEN_CONTRACT_ADDRESS,
                1, NOW);
    }

    // --- AC1 (R7, named), AC3 (R8, named) ---

    @Test
    void shouldTransitionToSeenOnChainTxSeen() {
        when(repository.existsByInvoiceIdAndStateIn(eq(INVOICE_ID), any())).thenReturn(false);
        when(repository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        boolean applied = service.applySeen(openInvoice(), seenEvent());

        assertThat(applied).isTrue();
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(repository).save(captor.capture());
        Payment saved = captor.getValue();
        assertThat(saved.getInvoiceId()).isEqualTo(INVOICE_ID);
        assertThat(saved.getChain()).isEqualTo(CHAIN);
        assertThat(saved.getTxHash()).isEqualTo(TX_HASH);
        assertThat(saved.getToAddress()).isEqualTo(PAY_TO_ADDRESS);
        assertThat(saved.getState()).isEqualTo(PaymentState.SEEN);
        assertThat(saved.getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldEmitPaymentSeenOnFirstSeen() {
        when(repository.existsByInvoiceIdAndStateIn(eq(INVOICE_ID), any())).thenReturn(false);
        when(repository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.applySeen(openInvoice(), seenEvent());

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outboxPublisher).publish(eq("payment-seen"), any(String.class), eq("payment.seen"), payloadCaptor.capture());
        PaymentSeenPayload payload = (PaymentSeenPayload) payloadCaptor.getValue();
        assertThat(payload.invoiceUuid()).isEqualTo(INVOICE_UUID);
        assertThat(payload.chain()).isEqualTo(CHAIN);
        assertThat(payload.txHash()).isEqualTo(TX_HASH);
        assertThat(payload.confirmations()).isEqualTo(1);
    }

    @Test
    void shouldRecordVerificationWithNullFromStateOnFirstSeen() {
        when(repository.existsByInvoiceIdAndStateIn(eq(INVOICE_ID), any())).thenReturn(false);
        when(repository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.applySeen(openInvoice(), seenEvent());

        // The mocked repository.save(...) just echoes the unsaved Payment back, so getId() is
        // legitimately null here (no real Hibernate flush to populate the @GeneratedValue) -
        // any() (untyped), not any(Long.class), since the latter never matches null.
        verify(verificationRecordService).record(any(), isNull(), eq(PaymentState.SEEN),
                eq(TRIGGER_EVENT_KEY), eq(1), isNull());
    }

    // --- Phase 3 Finding 1 / Phase 8 Kimi Finding 1 ---

    @Test
    void shouldNotCreatePaymentWhenInvoiceIsNoLongerOpen() {
        InvoiceWatchView nonOpenInvoice = new InvoiceWatchView(INVOICE_ID, INVOICE_UUID, InvoiceState.EXPIRED,
                PAY_TO_ADDRESS, WATCH_ID);

        boolean applied = service.applySeen(nonOpenInvoice, seenEvent());

        assertThat(applied).isFalse();
        verify(repository, never()).save(any());
        verify(verificationRecordService, never()).record(any(), any(), any(), any(), any(), any());
        verify(outboxPublisher, never()).publish(any(), any(), any(), any());
    }

    @Test
    void shouldNotCreateASecondPaymentWhenANonTerminalPaymentAlreadyExistsForTheInvoice() {
        when(repository.existsByInvoiceIdAndStateIn(eq(INVOICE_ID), any())).thenReturn(true);

        boolean applied = service.applySeen(openInvoice(), seenEvent());

        assertThat(applied).isFalse();
        verify(repository, never()).save(any());
        verify(verificationRecordService, never()).record(any(), any(), any(), any(), any(), any());
        verify(outboxPublisher, never()).publish(any(), any(), any(), any());
    }

    @Test
    void existsByInvoiceIdAndStateInShouldCheckTheFullNonTerminalSet() {
        when(repository.existsByInvoiceIdAndStateIn(eq(INVOICE_ID), any())).thenReturn(false);
        when(repository.save(any(Payment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.applySeen(openInvoice(), seenEvent());

        ArgumentCaptor<List<PaymentState>> statesCaptor = ArgumentCaptor.forClass(List.class);
        verify(repository).existsByInvoiceIdAndStateIn(eq(INVOICE_ID), statesCaptor.capture());
        assertThat(statesCaptor.getValue()).containsExactlyInAnyOrder(
                PaymentState.CREATED, PaymentState.WATCHING, PaymentState.SEEN, PaymentState.CONFIRMING);
    }

    // --- AC9 (Phase 3/4 Finding 5): no row is ever persisted in CREATED/WATCHING ---

    @Test
    void createSeenShouldAlwaysSetStateToSeenNeverCreatedOrWatching() {
        Payment payment = Payment.createSeen(INVOICE_ID, CHAIN, TX_HASH, PAY_TO_ADDRESS, TOKEN_CONTRACT_ADDRESS, 1, NOW);

        assertThat(payment.getState()).isEqualTo(PaymentState.SEEN);
    }

    // --- R9: transition / refresh / stale three-way split (Phase 3/4 Finding 3) ---

    @Test
    void shouldTransitionToConfirmingOnChainTxConfirmed() {
        Payment existing = Payment.createSeen(INVOICE_ID, CHAIN, TX_HASH, PAY_TO_ADDRESS, TOKEN_CONTRACT_ADDRESS, 1, NOW);
        when(repository.findByChainAndTxHash(CHAIN, TX_HASH)).thenReturn(Optional.of(existing));
        when(repository.markConfirmingIfSeen(any(), eq(PaymentState.SEEN), eq(PaymentState.CONFIRMING), eq(3), eq(NOW)))
                .thenReturn(1);
        ChainTxConfirmed event = new ChainTxConfirmed(TRIGGER_EVENT_KEY, WATCH_ID, INVOICE_UUID, CHAIN, TX_HASH,
                TOKEN_CONTRACT_ADDRESS, 3, NOW);

        service.applyConfirmed(event);

        verify(verificationRecordService).record(any(), eq(PaymentState.SEEN), eq(PaymentState.CONFIRMING),
                eq(TRIGGER_EVENT_KEY), eq(3), isNull());
    }

    @Test
    void shouldRefreshConfirmationsWithoutAVerificationRecordWhenAlreadyConfirmingWithAGreaterCount() {
        Payment existing = Payment.createSeen(INVOICE_ID, CHAIN, TX_HASH, PAY_TO_ADDRESS, TOKEN_CONTRACT_ADDRESS, 1, NOW);
        when(repository.findByChainAndTxHash(CHAIN, TX_HASH)).thenReturn(Optional.of(existing));
        when(repository.markConfirmingIfSeen(any(), any(), any(), any(Integer.class), any())).thenReturn(0);
        when(repository.refreshConfirmationsIfGreaterAndCurrentlyConfirming(any(), eq(PaymentState.CONFIRMING), eq(5), eq(NOW)))
                .thenReturn(1);
        ChainTxConfirmed event = new ChainTxConfirmed(TRIGGER_EVENT_KEY, WATCH_ID, INVOICE_UUID, CHAIN, TX_HASH,
                TOKEN_CONTRACT_ADDRESS, 5, NOW);

        service.applyConfirmed(event);

        verify(verificationRecordService, never()).record(any(), any(), any(), any(), any(), any());
        verify(verificationRecordService, never()).recordIgnored(any(), any(), any(), any());
    }

    @Test
    void shouldRecordIgnoredWhenConfirmedCountIsNotGreaterOrPaymentIsInAnyOtherState() {
        Payment existing = Payment.createSeen(INVOICE_ID, CHAIN, TX_HASH, PAY_TO_ADDRESS, TOKEN_CONTRACT_ADDRESS, 1, NOW);
        when(repository.findByChainAndTxHash(CHAIN, TX_HASH)).thenReturn(Optional.of(existing));
        when(repository.markConfirmingIfSeen(any(), any(), any(), any(Integer.class), any())).thenReturn(0);
        when(repository.refreshConfirmationsIfGreaterAndCurrentlyConfirming(any(), any(), any(Integer.class), any()))
                .thenReturn(0);
        when(repository.findCurrentState(any())).thenReturn(Optional.of(PaymentState.FINALIZED));
        ChainTxConfirmed event = new ChainTxConfirmed(TRIGGER_EVENT_KEY, WATCH_ID, INVOICE_UUID, CHAIN, TX_HASH,
                TOKEN_CONTRACT_ADDRESS, 2, NOW);

        service.applyConfirmed(event);

        verify(verificationRecordService, never()).record(any(), any(), any(), any(), any(), any());
        verify(verificationRecordService).recordIgnored(any(), eq(PaymentState.FINALIZED), eq(TRIGGER_EVENT_KEY), any());
    }

    @Test
    void shouldLogAndDropChainTxConfirmedForAnUnknownPayment() {
        when(repository.findByChainAndTxHash(CHAIN, TX_HASH)).thenReturn(Optional.empty());
        ChainTxConfirmed event = new ChainTxConfirmed(TRIGGER_EVENT_KEY, WATCH_ID, INVOICE_UUID, CHAIN, TX_HASH,
                TOKEN_CONTRACT_ADDRESS, 3, NOW);

        service.applyConfirmed(event);

        verify(verificationRecordService, never()).record(any(), any(), any(), any(), any(), any());
        verify(verificationRecordService, never()).recordIgnored(any(), any(), any(), any());
    }

    // --- R10: finalize from either SEEN or CONFIRMING ---

    private static ChainTxFinalized finalizedEvent() {
        return new ChainTxFinalized(TRIGGER_EVENT_KEY, WATCH_ID, INVOICE_UUID, CHAIN, TX_HASH, "0xfrom", "0xto",
                TOKEN_CONTRACT_ADDRESS, new BigDecimal("1000"), 12, false, NOW);
    }

    @Test
    void shouldTransitionToFinalizedFromConfirming() {
        Payment existing = Payment.createSeen(INVOICE_ID, CHAIN, TX_HASH, PAY_TO_ADDRESS, TOKEN_CONTRACT_ADDRESS, 1, NOW);
        when(repository.findByChainAndTxHash(CHAIN, TX_HASH)).thenReturn(Optional.of(existing));
        when(repository.markFinalizedIfCurrentState(any(), eq(PaymentState.CONFIRMING), eq(PaymentState.FINALIZED),
                any(), any(), any(), any(Integer.class), any(Boolean.class), eq(NOW))).thenReturn(1);

        service.applyFinalized(finalizedEvent());

        verify(verificationRecordService).record(any(), eq(PaymentState.CONFIRMING), eq(PaymentState.FINALIZED),
                eq(TRIGGER_EVENT_KEY), eq(12), isNull());
        verify(repository, never()).markFinalizedIfCurrentState(any(), eq(PaymentState.SEEN), any(), any(), any(),
                any(), any(Integer.class), any(Boolean.class), any());
    }

    @Test
    void shouldTransitionToFinalizedFromSeenWhenNeverConfirmed() {
        Payment existing = Payment.createSeen(INVOICE_ID, CHAIN, TX_HASH, PAY_TO_ADDRESS, TOKEN_CONTRACT_ADDRESS, 1, NOW);
        when(repository.findByChainAndTxHash(CHAIN, TX_HASH)).thenReturn(Optional.of(existing));
        when(repository.markFinalizedIfCurrentState(any(), eq(PaymentState.CONFIRMING), any(), any(), any(), any(),
                any(Integer.class), any(Boolean.class), any())).thenReturn(0);
        when(repository.markFinalizedIfCurrentState(any(), eq(PaymentState.SEEN), eq(PaymentState.FINALIZED), any(),
                any(), any(), any(Integer.class), any(Boolean.class), eq(NOW))).thenReturn(1);

        service.applyFinalized(finalizedEvent());

        verify(verificationRecordService).record(any(), eq(PaymentState.SEEN), eq(PaymentState.FINALIZED),
                eq(TRIGGER_EVENT_KEY), eq(12), isNull());
    }

    @Test
    void shouldPersistRealObservedAmountAndAddressesOnFinalize() {
        Payment existing = Payment.createSeen(INVOICE_ID, CHAIN, TX_HASH, PAY_TO_ADDRESS, TOKEN_CONTRACT_ADDRESS, 1, NOW);
        when(repository.findByChainAndTxHash(CHAIN, TX_HASH)).thenReturn(Optional.of(existing));
        when(repository.markFinalizedIfCurrentState(any(), eq(PaymentState.CONFIRMING), eq(PaymentState.FINALIZED),
                eq(new BigDecimal("1000")), eq("0xfrom"), eq("0xto"), eq(12), eq(false), eq(NOW))).thenReturn(1);

        service.applyFinalized(finalizedEvent());

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outboxPublisher).publish(eq("payment-finalized"), any(String.class), eq("payment.finalized"),
                payloadCaptor.capture());
        PaymentFinalizedPayload payload = (PaymentFinalizedPayload) payloadCaptor.getValue();
        assertThat(payload.amount()).isEqualTo("1000");
        assertThat(payload.fromAddress()).isEqualTo("0xfrom");
        assertThat(payload.toAddress()).isEqualTo("0xto");
        assertThat(payload.addressPoisoningFlag()).isFalse();
    }

    @Test
    void shouldRecordIgnoredWhenFinalizedEventIsStale() {
        Payment existing = Payment.createSeen(INVOICE_ID, CHAIN, TX_HASH, PAY_TO_ADDRESS, TOKEN_CONTRACT_ADDRESS, 1, NOW);
        when(repository.findByChainAndTxHash(CHAIN, TX_HASH)).thenReturn(Optional.of(existing));
        when(repository.markFinalizedIfCurrentState(any(), any(), any(), any(), any(), any(), any(Integer.class),
                any(Boolean.class), any())).thenReturn(0);
        when(repository.findCurrentState(any())).thenReturn(Optional.of(PaymentState.ATTESTED));

        service.applyFinalized(finalizedEvent());

        verify(verificationRecordService, never()).record(any(), any(), any(), any(), any(), any());
        verify(verificationRecordService).recordIgnored(any(), eq(PaymentState.ATTESTED), eq(TRIGGER_EVENT_KEY), any());
        verify(outboxPublisher, never()).publish(any(), any(), any(), any());
    }

    @Test
    void shouldLogAndDropChainTxFinalizedForAnUnknownPayment() {
        when(repository.findByChainAndTxHash(CHAIN, TX_HASH)).thenReturn(Optional.empty());

        service.applyFinalized(finalizedEvent());

        verify(verificationRecordService, never()).record(any(), any(), any(), any(), any(), any());
        verify(verificationRecordService, never()).recordIgnored(any(), any(), any(), any());
    }

    // --- null handling ---

    @Test
    void applySeenShouldThrowNullPointerExceptionForNullArguments() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.applySeen(null, seenEvent()))
                .isInstanceOf(NullPointerException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.applySeen(openInvoice(), null))
                .isInstanceOf(NullPointerException.class);
    }
}
