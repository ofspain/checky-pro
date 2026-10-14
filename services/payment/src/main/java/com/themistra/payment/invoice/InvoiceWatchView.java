package com.themistra.payment.invoice;

import java.util.UUID;

/**
 * T11 (L12): the cross-module projection {@link InvoiceService#findByWatchId} returns — never
 * the real {@link Invoice} entity itself. A real ArchUnit violation (T09's own
 * {@code shouldPreventCrossModuleEntityImports} rule) was caught empirically at Phase 6 when
 * {@code ChainEventConsumer}/{@code PaymentService} first called getters directly on an
 * {@code Invoice} instance received from a different feature module — receiving an entity and
 * calling its own methods from another module violates L12 just as much as importing its
 * repository would, regardless of how the entity instance arrived. This plain record exposes
 * exactly the fields {@code consumer}/{@code payment} need and nothing else.
 */
public record InvoiceWatchView(Long id, UUID invoiceUuid, InvoiceState state, String payToAddress, UUID watchId) {

    static InvoiceWatchView from(Invoice invoice) {
        return new InvoiceWatchView(invoice.getId(), invoice.getInvoiceUuid(), invoice.getState(),
                invoice.getPayToAddress(), invoice.getWatchId());
    }
}
