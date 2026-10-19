package com.themistra.notification.routing;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Notification T07, frozen brief D2. Two payment events don't name their merchant:
 * {@code payment.seen} carries only {@code invoiceUuid}, and {@code payment.finalized} only
 * {@code paymentUuid}. This projection remembers the links the other events do carry, so the
 * consumer can still find the merchant.
 *
 * <p>Fed only by consumed {@code payments.*} events, never by a call to payment-service. That keeps
 * L2's purpose (no live cross-service call on the delivery path), the same way
 * {@code contact_projection} is fed only by consumed auth events. The human granted this reading
 * of L2 explicitly at T07 Phase 4.</p>
 *
 * <p>The only seam {@code consumer} uses (L11): it returns plain identifiers, never entities.</p>
 */
@Service
public class PaymentRouting {

    private final InvoiceRouteRepository invoiceRoutes;
    private final PaymentRouteRepository paymentRoutes;

    public PaymentRouting(InvoiceRouteRepository invoiceRoutes, PaymentRouteRepository paymentRoutes) {
        this.invoiceRoutes = invoiceRoutes;
        this.paymentRoutes = paymentRoutes;
    }

    /** @param tokenDecimals may be null when the source event doesn't carry it. */
    @Transactional
    public void recordInvoice(UUID invoiceUuid, UUID merchantAccountUuid, Short tokenDecimals) {
        Objects.requireNonNull(invoiceUuid, "invoiceUuid must not be null");
        Objects.requireNonNull(merchantAccountUuid, "merchantAccountUuid must not be null");
        invoiceRoutes.insertIfAbsent(invoiceUuid, merchantAccountUuid, tokenDecimals);
    }

    @Transactional
    public void recordPayment(UUID paymentUuid, UUID invoiceUuid) {
        Objects.requireNonNull(paymentUuid, "paymentUuid must not be null");
        Objects.requireNonNull(invoiceUuid, "invoiceUuid must not be null");
        paymentRoutes.insertIfAbsent(paymentUuid, invoiceUuid);
    }

    @Transactional(readOnly = true)
    public Optional<UUID> findMerchantForInvoice(UUID invoiceUuid) {
        return invoiceUuid == null ? Optional.empty()
                : invoiceRoutes.findById(invoiceUuid).map(InvoiceRoute::getMerchantAccountUuid);
    }

    @Transactional(readOnly = true)
    public Optional<UUID> findInvoiceForPayment(UUID paymentUuid) {
        return paymentUuid == null ? Optional.empty()
                : paymentRoutes.findById(paymentUuid).map(PaymentRoute::getInvoiceUuid);
    }

    @Transactional(readOnly = true)
    public Optional<Integer> findTokenDecimals(UUID invoiceUuid) {
        return invoiceUuid == null ? Optional.empty()
                : invoiceRoutes.findById(invoiceUuid).map(InvoiceRoute::getTokenDecimals).map(Short::intValue);
    }
}
