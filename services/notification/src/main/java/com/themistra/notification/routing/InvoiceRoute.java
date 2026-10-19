package com.themistra.notification.routing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Which merchant owns an invoice, learned from consumed {@code payments.invoice.created} (or
 * {@code payments.receipt.issued}) events (notification T07, frozen brief D2). Insert-once: the
 * routing facts never change, so there are no setters and the grant has no UPDATE.
 */
@Entity
@Table(name = "invoice_routes", schema = "notifications")
public class InvoiceRoute {

    @Id
    @Column(name = "invoice_uuid")
    private UUID invoiceUuid;

    @Column(name = "merchant_account_uuid", nullable = false)
    private UUID merchantAccountUuid;

    /** Null when the route was learned from an event that carries no {@code tokenDecimals}. */
    @Column(name = "token_decimals")
    private Short tokenDecimals;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    protected InvoiceRoute() {
        // JPA only
    }

    public UUID getInvoiceUuid() {
        return invoiceUuid;
    }

    public UUID getMerchantAccountUuid() {
        return merchantAccountUuid;
    }

    public Short getTokenDecimals() {
        return tokenDecimals;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
