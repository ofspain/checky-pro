package com.themistra.payment.history;

import com.themistra.payment.history.dto.PaymentResponse;
import com.themistra.payment.invoice.InvoiceNotFoundForTests;
import com.themistra.payment.invoice.InvoiceReceiptView;
import com.themistra.payment.invoice.InvoiceService;
import com.themistra.payment.invoice.InvoiceState;
import com.themistra.payment.payment.PaymentService;
import com.themistra.payment.payment.PaymentState;
import com.themistra.payment.payment.PaymentView;
import com.themistra.payment.receipt.ReceiptReadService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Frontend Phase 1b (2026-10-21): {@code GET /api/v1/invoices/{invoiceUuid}/payments}. */
class PaymentHistoryServiceTest {

    private static final UUID MERCHANT = UUID.randomUUID();
    private static final UUID INVOICE_UUID = UUID.randomUUID();
    private static final long INVOICE_ID = 41L;
    private static final Instant T0 = Instant.parse("2026-10-21T10:00:00Z");

    private final InvoiceService invoiceService = mock(InvoiceService.class);
    private final PaymentService paymentService = mock(PaymentService.class);
    private final ReceiptReadService receiptReadService = mock(ReceiptReadService.class);
    private final PaymentHistoryService service =
            new PaymentHistoryService(invoiceService, paymentService, receiptReadService);

    private static PaymentView attested(long id, UUID paymentUuid) {
        return new PaymentView(id, paymentUuid, "ETHEREUM", "0xattested", PaymentState.ATTESTED, 14,
                new BigDecimal("1000000"), "0xtoken", "0xfrom", null, false, T0, T0.plusSeconds(1),
                T0.plusSeconds(2), null, T0.plusSeconds(3));
    }

    private static PaymentView seen(long id, UUID paymentUuid) {
        return new PaymentView(id, paymentUuid, "ETHEREUM", "0xseen", PaymentState.SEEN, 0, null, "0xtoken",
                null, null, true, T0.plusSeconds(10), null, null, null, null);
    }

    private void invoiceIsTheMerchants() {
        when(invoiceService.getViewForMerchant(MERCHANT, INVOICE_UUID)).thenReturn(
                new InvoiceReceiptView(INVOICE_ID, INVOICE_UUID, MERCHANT, (short) 6, "0xpayto", InvoiceState.PAID));
    }

    @Test
    void listsEveryPaymentInOrderWithItsReceiptWhereOneExists() {
        invoiceIsTheMerchants();
        UUID attestedUuid = UUID.randomUUID();
        UUID seenUuid = UUID.randomUUID();
        UUID receiptUuid = UUID.randomUUID();
        when(paymentService.listForInvoice(INVOICE_ID)).thenReturn(List.of(attested(7L, attestedUuid), seen(8L, seenUuid)));
        when(receiptReadService.receiptUuidsByPaymentId(List.of(7L, 8L))).thenReturn(Map.of(7L, receiptUuid));

        List<PaymentResponse> result = service.listForMerchant(MERCHANT, INVOICE_UUID);

        assertThat(result).extracting(PaymentResponse::paymentUuid).containsExactly(attestedUuid, seenUuid);
        assertThat(result.get(0)).isEqualTo(new PaymentResponse(attestedUuid, INVOICE_UUID, "ETHEREUM", "0xattested",
                "ATTESTED", 14, "1000000", "0xtoken", (short) 6, "0xfrom", null, false, T0, T0.plusSeconds(1),
                T0.plusSeconds(2), null, T0.plusSeconds(3), receiptUuid));
        assertThat(result.get(1)).isEqualTo(new PaymentResponse(seenUuid, INVOICE_UUID, "ETHEREUM", "0xseen",
                "SEEN", 0, null, "0xtoken", (short) 6, null, null, true, T0.plusSeconds(10), null, null, null, null,
                null));
    }

    /** L6: amounts are whole base units written without an exponent. */
    @Test
    void amountIsAPlainDecimalString() {
        invoiceIsTheMerchants();
        PaymentView big = new PaymentView(7L, UUID.randomUUID(), "ETHEREUM", "0x1", PaymentState.FINALIZED, 12,
                new BigDecimal("1E+21"), "0xtoken", "0xfrom", "OVERPAID", false, T0, T0, T0, null, null);
        when(paymentService.listForInvoice(INVOICE_ID)).thenReturn(List.of(big));
        when(receiptReadService.receiptUuidsByPaymentId(List.of(7L))).thenReturn(Map.of());

        PaymentResponse response = service.listForMerchant(MERCHANT, INVOICE_UUID).get(0);

        assertThat(response.amount()).isEqualTo("1000000000000000000000");
        assertThat(response.amountDiscrepancy()).isEqualTo("OVERPAID");
        assertThat(response.receiptUuid()).isNull();
    }

    @Test
    void anInvoiceWithNoPaymentGivesAnEmptyList() {
        invoiceIsTheMerchants();
        when(paymentService.listForInvoice(INVOICE_ID)).thenReturn(List.of());
        when(receiptReadService.receiptUuidsByPaymentId(List.of())).thenReturn(Map.of());

        assertThat(service.listForMerchant(MERCHANT, INVOICE_UUID)).isEmpty();
    }

    /** R28: another merchant's invoice is not found, and no payment is read. */
    @Test
    void anInvoiceThatIsNotTheCallersIsNotFoundAndNoPaymentIsRead() {
        RuntimeException notFound = InvoiceNotFoundForTests.exception();
        when(invoiceService.getViewForMerchant(MERCHANT, INVOICE_UUID)).thenThrow(notFound);

        assertThatThrownBy(() -> service.listForMerchant(MERCHANT, INVOICE_UUID)).isSameAs(notFound);
        verifyNoInteractions(paymentService, receiptReadService);
    }
}
