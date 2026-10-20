package com.themistra.notification.routing;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
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
    private final ParkedPaymentEventRepository parkedEvents;

    public PaymentRouting(InvoiceRouteRepository invoiceRoutes, PaymentRouteRepository paymentRoutes,
                          ParkedPaymentEventRepository parkedEvents) {
        this.invoiceRoutes = invoiceRoutes;
        this.paymentRoutes = paymentRoutes;
        this.parkedEvents = parkedEvents;
    }

    /** FU-T07-4: keeps an event whose merchant can't be found yet, for {@link #findParkedFor} to
     * hand back once its route arrives. At least one of the two UUIDs must be known. */
    @Transactional
    public void park(String eventKey, String kind, String payload, UUID invoiceUuid, UUID paymentUuid) {
        Objects.requireNonNull(eventKey, "eventKey must not be null");
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
        if (invoiceUuid == null && paymentUuid == null) {
            throw new IllegalArgumentException("a parked event must name its invoice or its payment");
        }
        parkedEvents.park(eventKey, kind, payload, invoiceUuid == null ? null : invoiceUuid.toString(),
                paymentUuid == null ? null : paymentUuid.toString());
    }

    /** The parked events that may now be routable through this invoice, in lifecycle order
     * (seen, finalized, receipt), then oldest first. */
    @Transactional(readOnly = true)
    public List<ParkedEvent> findParkedFor(UUID invoiceUuid) {
        Objects.requireNonNull(invoiceUuid, "invoiceUuid must not be null");
        return parkedEvents.findForInvoice(invoiceUuid).stream()
                .map(parked -> new ParkedEvent(parked.getEventKey(), parked.getKind(), parked.getPayload()))
                .toList();
    }

    /** Removes every event parked before {@code cutoff} and returns their keys, oldest first. An
     * event whose route arrives later than this is never notified; the caller logs each key. */
    @Transactional
    public List<String> expireParkedBefore(Instant cutoff) {
        Objects.requireNonNull(cutoff, "cutoff must not be null");
        List<String> keys = parkedEvents.findKeysParkedBefore(cutoff);
        if (!keys.isEmpty()) {
            parkedEvents.expire(keys, cutoff);
        }
        return keys;
    }

    /** Removes a parked event once it has been dispatched, in the dispatch's own transaction. */
    @Transactional
    public void releaseParked(String eventKey) {
        Objects.requireNonNull(eventKey, "eventKey must not be null");
        parkedEvents.release(eventKey);
    }

    /** Records which merchant owns an invoice. The first merchant recorded wins. Decimals are
     * filled in later if the first event lacked them, and never overwritten once known.
     *
     * @param tokenDecimals may be null when the source event doesn't carry it. */
    @Transactional
    public void recordInvoice(UUID invoiceUuid, UUID merchantAccountUuid, Short tokenDecimals) {
        Objects.requireNonNull(invoiceUuid, "invoiceUuid must not be null");
        Objects.requireNonNull(merchantAccountUuid, "merchantAccountUuid must not be null");
        invoiceRoutes.insertOrFillDecimals(invoiceUuid, merchantAccountUuid, tokenDecimals);
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
