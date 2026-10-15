package com.themistra.payment.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * T17 (L12): the cross-module projection {@link PaymentService#findReceiptPrimitives} returns —
 * never the real {@link Payment} entity itself, mirroring {@code invoice.InvoiceWatchView}'s own
 * T11 precedent exactly. Exposes exactly the fields {@code receipt.ReceiptService} needs to
 * build a {@code receipt.ReceiptPayload} (`design.md` O4) and to re-check eligibility, and
 * nothing else.
 */
public record PaymentReceiptView(Long id, UUID paymentUuid, Long invoiceId, String chain, String txHash,
                                  String tokenContractAddress, BigDecimal amount, String fromAddress,
                                  Instant finalizedAt, PaymentState state) {

    static PaymentReceiptView from(Payment payment) {
        return new PaymentReceiptView(payment.getId(), payment.getPaymentUuid(), payment.getInvoiceId(),
                payment.getChain(), payment.getTxHash(), payment.getTokenContractAddress(), payment.getAmount(),
                payment.getFromAddress(), payment.getFinalizedAt(), payment.getState());
    }
}
