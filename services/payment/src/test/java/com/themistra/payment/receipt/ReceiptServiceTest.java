package com.themistra.payment.receipt;

import com.themistra.payment.common.AttestationRefusedException;
import com.themistra.payment.common.CryptoClient;
import com.themistra.payment.common.CryptoServiceUnavailableException;
import com.themistra.payment.common.crypto.AttestationResult;
import com.themistra.payment.invoice.InvoiceReceiptView;
import com.themistra.payment.invoice.InvoiceService;
import com.themistra.payment.invoice.InvoiceState;
import com.themistra.payment.payment.PaymentReceiptView;
import com.themistra.payment.payment.PaymentService;
import com.themistra.payment.payment.PaymentState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * T17 (R17/R18/R19/R20). Plain JUnit + Mockito, no Spring context - mirrors {@code
 * ChainEventConsumerTest}'s own established, already-proven pattern for mocking {@link
 * PlatformTransactionManager} (an unstubbed mock's {@code getTransaction(...)} returns
 * {@code null}, and {@code TransactionTemplate} still invokes the callback with that null
 * status - the callback body here never touches it directly, same as every existing
 * {@code TransactionTemplate} test in this codebase).
 */
@ExtendWith(MockitoExtension.class)
class ReceiptServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-13T00:00:00Z");
    private static final Long PAYMENT_ID = 99L;
    private static final Long INVOICE_ID = 7L;
    private static final String TRIGGER_EVENT_KEY = "ETHEREUM:0xabc:chain.tx.finalized";
    private static final String CHAIN = "ETHEREUM";
    private static final String TX_HASH = "0xabc";

    @Mock
    private PaymentService paymentService;

    @Mock
    private InvoiceService invoiceService;

    @Mock
    private CryptoClient cryptoClient;

    @Mock
    private PlatformTransactionManager transactionManager;

    private ReceiptService service;

    @BeforeEach
    void setUp() {
        service = new ReceiptService(paymentService, invoiceService, cryptoClient, transactionManager,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static PaymentReceiptView finalizedPayment() {
        return new PaymentReceiptView(PAYMENT_ID, UUID.randomUUID(), INVOICE_ID, CHAIN, TX_HASH, "0x111",
                BigDecimal.valueOf(1000), "0xfrom", NOW, PaymentState.FINALIZED);
    }

    private static InvoiceReceiptView paidInvoice() {
        return new InvoiceReceiptView(INVOICE_ID, UUID.randomUUID(), UUID.randomUUID(), (short) 6, "0xdef",
                InvoiceState.PAID);
    }

    private void stubEligiblePaymentAndInvoice() {
        when(paymentService.findReceiptPrimitives(PAYMENT_ID)).thenReturn(Optional.of(finalizedPayment()));
        when(invoiceService.findReceiptPrimitives(INVOICE_ID)).thenReturn(Optional.of(paidInvoice()));
    }

    // --- R17/R18/L2, named ---

    @Test
    void shouldIssueReceiptOnlyFromFinalizedState() {
        stubEligiblePaymentAndInvoice();
        when(cryptoClient.attest(any(), eq(CHAIN), eq(TX_HASH)))
                .thenReturn(new AttestationResult.Signed("sig", "key", NOW));

        ReceiptIssueResult result = service.issueIfEligible(PAYMENT_ID, TRIGGER_EVENT_KEY);

        assertThat(result).isInstanceOf(ReceiptIssueResult.Signed.class);
        verify(cryptoClient).attest(any(), eq(CHAIN), eq(TX_HASH));
        // Phase 11 Gap 3 (Kimi): a SIGNED outcome must never also trigger the HELD transition.
        verify(paymentService, never()).markHeldIfFinalized(any(), any(), any(), any());
        verify(invoiceService, never()).markHeldIfPaid(any(), any());
    }

    @Test
    void shouldNeverIssueReceiptBeforeFinalized() {
        PaymentReceiptView notFinalized = new PaymentReceiptView(PAYMENT_ID, UUID.randomUUID(), INVOICE_ID, CHAIN,
                TX_HASH, "0x111", BigDecimal.valueOf(1000), "0xfrom", null, PaymentState.CONFIRMING);
        when(paymentService.findReceiptPrimitives(PAYMENT_ID)).thenReturn(Optional.of(notFinalized));

        ReceiptIssueResult result = service.issueIfEligible(PAYMENT_ID, TRIGGER_EVENT_KEY);

        assertThat(result).isInstanceOf(ReceiptIssueResult.NotEligible.class);
        verify(cryptoClient, never()).attest(any(), any(), any());
        verify(invoiceService, never()).findReceiptPrimitives(any());
    }

    @Test
    void shouldReturnNotEligibleWhenInvoiceIsUnderpaidOrOverpaid() {
        when(paymentService.findReceiptPrimitives(PAYMENT_ID)).thenReturn(Optional.of(finalizedPayment()));
        InvoiceReceiptView underpaid = new InvoiceReceiptView(INVOICE_ID, UUID.randomUUID(), UUID.randomUUID(),
                (short) 6, "0xdef", InvoiceState.UNDERPAID);
        when(invoiceService.findReceiptPrimitives(INVOICE_ID)).thenReturn(Optional.of(underpaid));

        ReceiptIssueResult result = service.issueIfEligible(PAYMENT_ID, TRIGGER_EVENT_KEY);

        assertThat(result).isInstanceOf(ReceiptIssueResult.NotEligible.class);
        verify(cryptoClient, never()).attest(any(), any(), any());
    }

    @Test
    void shouldReturnNotEligibleWhenPaymentDoesNotExist() {
        when(paymentService.findReceiptPrimitives(PAYMENT_ID)).thenReturn(Optional.empty());

        ReceiptIssueResult result = service.issueIfEligible(PAYMENT_ID, TRIGGER_EVENT_KEY);

        assertThat(result).isInstanceOf(ReceiptIssueResult.NotEligible.class);
    }

    @Test
    void shouldReturnNotEligibleWhenInvoiceDoesNotExist() {
        when(paymentService.findReceiptPrimitives(PAYMENT_ID)).thenReturn(Optional.of(finalizedPayment()));
        when(invoiceService.findReceiptPrimitives(INVOICE_ID)).thenReturn(Optional.empty());

        ReceiptIssueResult result = service.issueIfEligible(PAYMENT_ID, TRIGGER_EVENT_KEY);

        assertThat(result).isInstanceOf(ReceiptIssueResult.NotEligible.class);
    }

    // --- R19, named ---

    @Test
    void shouldRequestKmsSignatureViaCryptoAttestEndpoint() {
        stubEligiblePaymentAndInvoice();
        when(cryptoClient.attest(any(), eq(CHAIN), eq(TX_HASH)))
                .thenReturn(new AttestationResult.Signed("sig", "key", NOW));

        ArgumentCaptor<String> digestCaptor = ArgumentCaptor.forClass(String.class);
        service.issueIfEligible(PAYMENT_ID, TRIGGER_EVENT_KEY);

        verify(cryptoClient).attest(digestCaptor.capture(), eq(CHAIN), eq(TX_HASH));
        assertThat(digestCaptor.getValue()).matches("[0-9a-f]{64}");
    }

    @Test
    void shouldCarryTheSameReceiptUuidInTheSignedResult() {
        stubEligiblePaymentAndInvoice();
        when(cryptoClient.attest(any(), any(), any())).thenReturn(new AttestationResult.Signed("sig", "key", NOW));

        ReceiptIssueResult.Signed result = (ReceiptIssueResult.Signed) service.issueIfEligible(PAYMENT_ID, TRIGGER_EVENT_KEY);

        assertThat(result.receiptUuid()).isNotNull();
        assertThat(result.signed().signature()).isEqualTo("sig");
    }

    /** Phase 11 Gap 4 (Kimi): proves {@code receiptUuid} is freshly generated per call, not a
     * constant/cached value that would silently collide across two different receipts. */
    @Test
    void shouldGenerateADifferentReceiptUuidOnEachCall() {
        stubEligiblePaymentAndInvoice();
        when(cryptoClient.attest(any(), any(), any())).thenReturn(new AttestationResult.Signed("sig", "key", NOW));

        ReceiptIssueResult.Signed first = (ReceiptIssueResult.Signed) service.issueIfEligible(PAYMENT_ID, TRIGGER_EVENT_KEY);
        ReceiptIssueResult.Signed second = (ReceiptIssueResult.Signed) service.issueIfEligible(PAYMENT_ID, TRIGGER_EVENT_KEY);

        assertThat(first.receiptUuid()).isNotEqualTo(second.receiptUuid());
    }

    /** Phase 11 Gap 1 (Kimi): the Phase 9 widened catch (self-review Finding 1) must also
     * swallow a genuine, unrelated runtime failure from either HELD update - not just the
     * intentional split-result {@code IllegalStateException}. */
    @Test
    void shouldReturnFailedWhenHeldUpdateThrowsAnUnrelatedRuntimeException() {
        stubEligiblePaymentAndInvoice();
        when(cryptoClient.attest(any(), any(), any())).thenReturn(new AttestationResult.Blocked("sanctioned"));
        when(paymentService.markHeldIfFinalized(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("db down"));

        ReceiptIssueResult result = service.issueIfEligible(PAYMENT_ID, TRIGGER_EVENT_KEY);

        assertThat(result).isInstanceOf(ReceiptIssueResult.Failed.class);
        verify(invoiceService, never()).markHeldIfPaid(any(), any());
    }

    // --- R20, named ---

    @Test
    void shouldNotIssueReceiptWhenAttestReturnsBlocked() {
        stubEligiblePaymentAndInvoice();
        when(cryptoClient.attest(any(), any(), any())).thenReturn(new AttestationResult.Blocked("sanctioned"));
        when(paymentService.markHeldIfFinalized(PAYMENT_ID, "sanctioned", TRIGGER_EVENT_KEY, NOW)).thenReturn(true);
        when(invoiceService.markHeldIfPaid(INVOICE_ID, NOW)).thenReturn(true);

        ReceiptIssueResult result = service.issueIfEligible(PAYMENT_ID, TRIGGER_EVENT_KEY);

        assertThat(result).isEqualTo(new ReceiptIssueResult.Blocked("sanctioned"));
        verify(paymentService).markHeldIfFinalized(PAYMENT_ID, "sanctioned", TRIGGER_EVENT_KEY, NOW);
        verify(invoiceService).markHeldIfPaid(INVOICE_ID, NOW);
    }

    @Test
    void shouldReturnBlockedWithoutErrorWhenBothHeldUpdatesAreAlreadyNoOps() {
        stubEligiblePaymentAndInvoice();
        when(cryptoClient.attest(any(), any(), any())).thenReturn(new AttestationResult.Blocked("sanctioned"));
        when(paymentService.markHeldIfFinalized(any(), any(), any(), any())).thenReturn(false);
        when(invoiceService.markHeldIfPaid(any(), any())).thenReturn(false);

        ReceiptIssueResult result = service.issueIfEligible(PAYMENT_ID, TRIGGER_EVENT_KEY);

        assertThat(result).isEqualTo(new ReceiptIssueResult.Blocked("sanctioned"));
    }

    @Test
    void shouldThrowAndRollBackWhenOnlyPaymentHeldUpdateSucceeds() {
        stubEligiblePaymentAndInvoice();
        when(cryptoClient.attest(any(), any(), any())).thenReturn(new AttestationResult.Blocked("sanctioned"));
        when(paymentService.markHeldIfFinalized(any(), any(), any(), any())).thenReturn(true);
        when(invoiceService.markHeldIfPaid(any(), any())).thenReturn(false);

        ReceiptIssueResult result = service.issueIfEligible(PAYMENT_ID, TRIGGER_EVENT_KEY);

        assertThat(result).isInstanceOf(ReceiptIssueResult.Failed.class);
    }

    @Test
    void shouldThrowAndRollBackWhenOnlyInvoiceHeldUpdateSucceeds() {
        stubEligiblePaymentAndInvoice();
        when(cryptoClient.attest(any(), any(), any())).thenReturn(new AttestationResult.Blocked("sanctioned"));
        when(paymentService.markHeldIfFinalized(any(), any(), any(), any())).thenReturn(false);
        when(invoiceService.markHeldIfPaid(any(), any())).thenReturn(true);

        ReceiptIssueResult result = service.issueIfEligible(PAYMENT_ID, TRIGGER_EVENT_KEY);

        assertThat(result).isInstanceOf(ReceiptIssueResult.Failed.class);
    }

    // --- Phase 3/9, 409 vs BLOCKED, and transport failures ---

    @Test
    void shouldReturnFailedWithoutStateChangeWhenAttestIsRefused() {
        stubEligiblePaymentAndInvoice();
        when(cryptoClient.attest(any(), any(), any())).thenThrow(new AttestationRefusedException("preconditions not met"));

        ReceiptIssueResult result = service.issueIfEligible(PAYMENT_ID, TRIGGER_EVENT_KEY);

        assertThat(result).isInstanceOf(ReceiptIssueResult.Failed.class);
        verify(paymentService, never()).markHeldIfFinalized(any(), any(), any(), any());
        verify(invoiceService, never()).markHeldIfPaid(any(), any());
    }

    @Test
    void shouldReturnFailedWithoutStateChangeWhenCryptoServiceIsUnavailable() {
        stubEligiblePaymentAndInvoice();
        when(cryptoClient.attest(any(), any(), any()))
                .thenThrow(new CryptoServiceUnavailableException("connection refused", null));

        ReceiptIssueResult result = service.issueIfEligible(PAYMENT_ID, TRIGGER_EVENT_KEY);

        assertThat(result).isInstanceOf(ReceiptIssueResult.Failed.class);
        verify(paymentService, never()).markHeldIfFinalized(any(), any(), any(), any());
        verify(invoiceService, never()).markHeldIfPaid(any(), any());
    }

    // --- null handling ---

    @Test
    void issueIfEligibleShouldThrowNullPointerExceptionForNullArguments() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.issueIfEligible(null, TRIGGER_EVENT_KEY))
                .isInstanceOf(NullPointerException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.issueIfEligible(PAYMENT_ID, null))
                .isInstanceOf(NullPointerException.class);
    }
}
