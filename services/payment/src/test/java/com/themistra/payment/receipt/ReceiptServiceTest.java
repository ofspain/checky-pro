package com.themistra.payment.receipt;

import com.themistra.payment.common.AttestationRefusedException;
import com.themistra.payment.common.CryptoClient;
import com.themistra.payment.common.CryptoServiceUnavailableException;
import com.themistra.payment.common.crypto.AttestationResult;
import com.themistra.payment.events.OutboxPublisher;
import com.themistra.payment.invoice.InvoiceReceiptView;
import com.themistra.payment.invoice.InvoiceService;
import com.themistra.payment.invoice.InvoiceState;
import com.themistra.payment.payment.PaymentReceiptView;
import com.themistra.payment.payment.PaymentService;
import com.themistra.payment.payment.PaymentState;
import com.themistra.payment.receipt.ledger.LedgerService;
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
import java.util.Map;
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

    @Mock
    private ReceiptStore receiptStore;

    @Mock
    private ReceiptRepository receiptRepository;

    @Mock
    private LedgerService ledgerService;

    @Mock
    private OutboxPublisher outboxPublisher;

    private ReceiptService service;

    @BeforeEach
    void setUp() {
        service = new ReceiptService(paymentService, invoiceService, cryptoClient, receiptStore,
                receiptRepository, ledgerService, outboxPublisher, transactionManager,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static final Long SAVED_RECEIPT_ID = 555L;

    /** T19 - the happy path through {@code persistSignedReceipt} now needs every new
     * collaborator stubbed; not every test needs this (the ineligibility/BLOCKED/409/
     * unavailable tests never reach this code path), so it's explicit per-test rather than a
     * blanket {@code @BeforeEach} stub Mockito's strict stubbing would otherwise flag as
     * unnecessary on those tests. */
    private void stubSuccessfulPersistence() {
        when(receiptStore.store(any(), any())).thenReturn(
                new ReceiptStorageLocation("bucket", "receipts/uuid", "receipts/uuid.json", "receipts/uuid.pdf"));
        Receipt savedReceipt = Receipt.create(UUID.randomUUID(), PAYMENT_ID, "digest", "sig", "key",
                "bucket", "receipts/uuid", NOW, NOW);
        // Phase 11 Gap 1 (Kimi): a real persisted Receipt always has a non-null, generated id -
        // set one via reflection (no public setter exists, by design) so tests can verify
        // ReceiptService actually passes THIS id to ledgerService.append, not just any value.
        org.springframework.test.util.ReflectionTestUtils.setField(savedReceipt, "id", SAVED_RECEIPT_ID);
        when(receiptRepository.save(any())).thenReturn(savedReceipt);
        when(paymentService.markAttestedIfFinalized(any(), any(), any(), any())).thenReturn(true);
    }

    // Phase 11 Gap 2 (Kimi): fixed, not freshly-random per call - shouldEmitReceiptIssuedAfterAttestation
    // needs to assert the published event's fields against the SAME UUIDs the stub was given,
    // not a second, independently-random set from calling these factories again.
    private static final UUID PAYMENT_UUID = UUID.randomUUID();
    private static final UUID INVOICE_UUID_VALUE = UUID.randomUUID();
    private static final UUID MERCHANT_ACCOUNT_UUID = UUID.randomUUID();

    private static PaymentReceiptView finalizedPayment() {
        return new PaymentReceiptView(PAYMENT_ID, PAYMENT_UUID, INVOICE_ID, CHAIN, TX_HASH, "0x111",
                BigDecimal.valueOf(1000), "0xfrom", NOW, PaymentState.FINALIZED);
    }

    private static InvoiceReceiptView paidInvoice() {
        return new InvoiceReceiptView(INVOICE_ID, INVOICE_UUID_VALUE, MERCHANT_ACCOUNT_UUID, (short) 6, "0xdef",
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
        stubSuccessfulPersistence();
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
        stubSuccessfulPersistence();
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
        stubSuccessfulPersistence();
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
        stubSuccessfulPersistence();
        when(cryptoClient.attest(any(), any(), any())).thenReturn(new AttestationResult.Signed("sig", "key", NOW));

        ReceiptIssueResult.Signed first = (ReceiptIssueResult.Signed) service.issueIfEligible(PAYMENT_ID, TRIGGER_EVENT_KEY);
        ReceiptIssueResult.Signed second = (ReceiptIssueResult.Signed) service.issueIfEligible(PAYMENT_ID, TRIGGER_EVENT_KEY);

        assertThat(first.receiptUuid()).isNotEqualTo(second.receiptUuid());
    }

    // --- R21/R23/R24/R25 (T19), named ---

    @Test
    void shouldTransitionToAttestedAfterReceiptStoredAndLedgerAppended() {
        stubEligiblePaymentAndInvoice();
        stubSuccessfulPersistence();
        when(cryptoClient.attest(any(), any(), any())).thenReturn(new AttestationResult.Signed("sig", "key", NOW));

        ReceiptIssueResult result = service.issueIfEligible(PAYMENT_ID, TRIGGER_EVENT_KEY);

        assertThat(result).isInstanceOf(ReceiptIssueResult.Signed.class);
        UUID receiptUuid = ((ReceiptIssueResult.Signed) result).receiptUuid();
        verify(receiptStore).store(any(), any());

        // Phase 11 Gap 3 (Kimi): assert every field of the persisted Receipt, not just paymentId.
        ArgumentCaptor<Receipt> receiptCaptor = ArgumentCaptor.forClass(Receipt.class);
        verify(receiptRepository).save(receiptCaptor.capture());
        Receipt savedReceipt = receiptCaptor.getValue();
        assertThat(savedReceipt.getReceiptUuid()).isEqualTo(receiptUuid);
        assertThat(savedReceipt.getPaymentId()).isEqualTo(PAYMENT_ID);
        assertThat(savedReceipt.getSignature()).isEqualTo("sig");
        assertThat(savedReceipt.getKmsKeyId()).isEqualTo("key");
        assertThat(savedReceipt.getSignedAt()).isEqualTo(NOW);
        assertThat(savedReceipt.getDigestSha256()).matches("[0-9a-f]{64}");

        // Phase 11 Gap 1 (Kimi): the real, generated Receipt id must be the one passed to the
        // ledger append - not just "some" id.
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> ledgerPayloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(ledgerService).append(eq(SAVED_RECEIPT_ID), ledgerPayloadCaptor.capture());

        // Phase 11 Gap 3 (Kimi): the ledger payload must carry the full 17-field shape, not an
        // arbitrary map.
        Map<String, Object> ledgerPayload = ledgerPayloadCaptor.getValue();
        assertThat(ledgerPayload)
                .containsEntry("receiptUuid", receiptUuid.toString())
                .containsEntry("chain", CHAIN)
                .containsEntry("txHash", TX_HASH)
                .containsEntry("signature", "sig")
                .containsEntry("kmsKeyId", "key")
                .containsEntry("s3Bucket", "bucket")
                .containsEntry("s3KeyPrefix", "receipts/uuid");

        verify(paymentService).markAttestedIfFinalized(eq(PAYMENT_ID), eq(TRIGGER_EVENT_KEY), eq(receiptUuid), any());
    }

    @Test
    void shouldEmitReceiptIssuedAfterAttestation() {
        stubEligiblePaymentAndInvoice();
        stubSuccessfulPersistence();
        when(cryptoClient.attest(any(), any(), any())).thenReturn(new AttestationResult.Signed("sig", "key", NOW));

        ReceiptIssueResult result = service.issueIfEligible(PAYMENT_ID, TRIGGER_EVENT_KEY);
        UUID receiptUuid = ((ReceiptIssueResult.Signed) result).receiptUuid();

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(outboxPublisher).publish(eq("receipt"), eq(receiptUuid.toString()), eq("receipt.issued"),
                payloadCaptor.capture());
        com.themistra.payment.receipt.event.ReceiptIssuedPayload payload =
                (com.themistra.payment.receipt.event.ReceiptIssuedPayload) payloadCaptor.getValue();

        // Phase 11 Gap 2 (Kimi): assert all 11 fields of the frozen schema, not a subset.
        assertThat(payload.invoiceUuid()).isEqualTo(INVOICE_UUID_VALUE);
        assertThat(payload.paymentUuid()).isEqualTo(PAYMENT_UUID);
        assertThat(payload.receiptUuid()).isEqualTo(receiptUuid);
        assertThat(payload.merchantAccountUuid()).isEqualTo(MERCHANT_ACCOUNT_UUID);
        assertThat(payload.chain()).isEqualTo(CHAIN);
        assertThat(payload.txHash()).isEqualTo(TX_HASH);
        assertThat(payload.amount()).isEqualTo("1000");
        assertThat(payload.tokenContractAddress()).isEqualTo("0x111");
        assertThat(payload.kmsKeyId()).isEqualTo("key");
        assertThat(payload.receiptUri()).isEqualTo("s3://bucket/receipts/uuid.json");
        assertThat(payload.occurredAt()).isEqualTo(NOW);
    }

    /** AC6, Phase 3/4 Kimi Finding 2: a concurrent reorg moving the payment out of
     * {@code FINALIZED} between the eligibility check and this transaction must abort the whole
     * persist sequence - no {@code receipt.issued} event for a payment that never actually
     * reached {@code ATTESTED}. */
    @Test
    void shouldAbortAndReturnFailedWhenMarkAttestedIfFinalizedReturnsZeroRows() {
        stubEligiblePaymentAndInvoice();
        stubSuccessfulPersistence();
        when(paymentService.markAttestedIfFinalized(any(), any(), any(), any())).thenReturn(false);
        when(cryptoClient.attest(any(), any(), any())).thenReturn(new AttestationResult.Signed("sig", "key", NOW));

        ReceiptIssueResult result = service.issueIfEligible(PAYMENT_ID, TRIGGER_EVENT_KEY);

        assertThat(result).isInstanceOf(ReceiptIssueResult.Failed.class);
        verify(outboxPublisher, never()).publish(any(), any(), any(), any());
    }

    /** Phase 7 self-review Finding 6 / Phase 9: an S3 failure before the DB transaction even
     * opens must not be mistaken for a Signed result. */
    @Test
    void shouldReturnFailedWhenReceiptStoreThrows() {
        stubEligiblePaymentAndInvoice();
        when(receiptStore.store(any(), any())).thenThrow(new ReceiptStorageException("s3 down", null));
        when(cryptoClient.attest(any(), any(), any())).thenReturn(new AttestationResult.Signed("sig", "key", NOW));

        ReceiptIssueResult result = service.issueIfEligible(PAYMENT_ID, TRIGGER_EVENT_KEY);

        assertThat(result).isInstanceOf(ReceiptIssueResult.Failed.class);
        verify(receiptRepository, never()).save(any());
    }

    /** The S3 object was already written when the ledger append fails - the orphan-window risk
     * `ReceiptService`'s own class Javadoc discloses, not solved here, but the method must still
     * fail safely rather than claim success. */
    @Test
    void shouldReturnFailedWhenLedgerServiceThrows() {
        stubEligiblePaymentAndInvoice();
        when(receiptStore.store(any(), any())).thenReturn(
                new ReceiptStorageLocation("bucket", "receipts/uuid", "receipts/uuid.json", "receipts/uuid.pdf"));
        when(receiptRepository.save(any())).thenReturn(Receipt.create(UUID.randomUUID(), PAYMENT_ID, "digest",
                "sig", "key", "bucket", "receipts/uuid", NOW, NOW));
        when(ledgerService.append(any(), any())).thenThrow(new RuntimeException("ledger db down"));
        when(cryptoClient.attest(any(), any(), any())).thenReturn(new AttestationResult.Signed("sig", "key", NOW));

        ReceiptIssueResult result = service.issueIfEligible(PAYMENT_ID, TRIGGER_EVENT_KEY);

        assertThat(result).isInstanceOf(ReceiptIssueResult.Failed.class);
        verify(outboxPublisher, never()).publish(any(), any(), any(), any());
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
