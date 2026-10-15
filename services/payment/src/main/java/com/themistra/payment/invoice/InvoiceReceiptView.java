package com.themistra.payment.invoice;

import java.util.UUID;

/**
 * T17 (L12): the cross-module projection {@link InvoiceService#findReceiptPrimitives} returns —
 * never the real {@link Invoice} entity itself, mirroring {@link InvoiceWatchView}'s own T11
 * precedent exactly. Exposes exactly the fields {@code receipt.ReceiptService} needs to build a
 * {@code receipt.ReceiptPayload} (`design.md` O4) and to re-check eligibility, and nothing else.
 */
public record InvoiceReceiptView(Long id, UUID invoiceUuid, UUID merchantAccountUuid, short tokenDecimals,
                                  String payToAddress, InvoiceState state) {

    static InvoiceReceiptView from(Invoice invoice) {
        return new InvoiceReceiptView(invoice.getId(), invoice.getInvoiceUuid(), invoice.getMerchantAccountUuid(),
                invoice.getTokenDecimals(), invoice.getPayToAddress(), invoice.getState());
    }
}
