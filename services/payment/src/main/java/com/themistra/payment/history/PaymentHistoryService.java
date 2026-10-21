package com.themistra.payment.history;

import com.themistra.payment.history.dto.PaymentResponse;
import com.themistra.payment.invoice.InvoiceReceiptView;
import com.themistra.payment.invoice.InvoiceService;
import com.themistra.payment.payment.PaymentService;
import com.themistra.payment.payment.PaymentView;
import com.themistra.payment.receipt.ReceiptReadService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Payment read (frontend Phase 1b, 2026-10-21): a merchant's payments for one of its invoices,
 * with each payment's receipt UUID once one exists. The invoice is looked up for the merchant
 * first, so another merchant's invoice and an unknown one give the same 404 (R28).
 */
@Service
public class PaymentHistoryService {

    private final InvoiceService invoiceService;
    private final PaymentService paymentService;
    private final ReceiptReadService receiptReadService;

    public PaymentHistoryService(InvoiceService invoiceService, PaymentService paymentService,
                                 ReceiptReadService receiptReadService) {
        this.invoiceService = invoiceService;
        this.paymentService = paymentService;
        this.receiptReadService = receiptReadService;
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> listForMerchant(UUID merchantAccountUuid, UUID invoiceUuid) {
        InvoiceReceiptView invoice = invoiceService.getViewForMerchant(merchantAccountUuid, invoiceUuid);
        List<PaymentView> payments = paymentService.listForInvoice(invoice.id());
        Map<Long, UUID> receipts = receiptReadService.receiptUuidsByPaymentId(
                payments.stream().map(PaymentView::id).toList());
        return payments.stream()
                .map(payment -> new PaymentResponse(payment.paymentUuid(), invoice.invoiceUuid(), payment.chain(),
                        payment.txHash(), payment.state().name(), payment.confirmations(),
                        payment.amount() == null ? null : payment.amount().toPlainString(),
                        payment.tokenContractAddress(), invoice.tokenDecimals(), payment.fromAddress(),
                        payment.amountDiscrepancy(), payment.addressPoisoningFlag(), payment.seenAt(),
                        payment.confirmedAt(), payment.finalizedAt(), payment.reorgedAt(), payment.attestedAt(),
                        receipts.get(payment.id())))
                .toList();
    }
}
