package com.themistra.payment.invoice;

import java.math.BigDecimal;
import java.time.Instant;
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
 *
 * <p><b>T12:</b> widened with {@code chain}/{@code tokenContractAddress}/{@code amount}/
 * {@code expiresAt} — exactly the fields {@code CryptoClient.registerWatch} needs to register a
 * replacement watch after a {@code SEEN → WATCHING} reorg reversal (R12), reusing the invoice's
 * own original values rather than re-deriving any of them.</p>
 */
public record InvoiceWatchView(Long id, UUID invoiceUuid, InvoiceState state, String payToAddress, UUID watchId,
                                String chain, String tokenContractAddress, BigDecimal amount, Instant expiresAt) {

    static InvoiceWatchView from(Invoice invoice) {
        return new InvoiceWatchView(invoice.getId(), invoice.getInvoiceUuid(), invoice.getState(),
                invoice.getPayToAddress(), invoice.getWatchId(), invoice.getChain(),
                invoice.getTokenContractAddress(), invoice.getAmount(), invoice.getExpiresAt());
    }
}
