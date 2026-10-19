package com.themistra.notification.routing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Which invoice a payment belongs to, learned from consumed {@code payments.payment.seen} (or
 * {@code payments.receipt.issued}) events (notification T07, frozen brief D2). Insert-once.
 */
@Entity
@Table(name = "payment_routes", schema = "notifications")
public class PaymentRoute {

    @Id
    @Column(name = "payment_uuid")
    private UUID paymentUuid;

    @Column(name = "invoice_uuid", nullable = false)
    private UUID invoiceUuid;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected PaymentRoute() {
        // JPA only
    }

    public UUID getPaymentUuid() {
        return paymentUuid;
    }

    public UUID getInvoiceUuid() {
        return invoiceUuid;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
