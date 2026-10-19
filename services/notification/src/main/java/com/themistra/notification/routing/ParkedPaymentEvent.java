package com.themistra.notification.routing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/** A payment event waiting for its route (FU-T07-4). Written and removed only through
 * {@link ParkedPaymentEventRepository}'s native queries; never updated. */
@Entity
@Immutable
@Table(name = "parked_payment_events", schema = "notifications")
public class ParkedPaymentEvent {

    @Id
    @Column(name = "event_key")
    private String eventKey;

    @Column(name = "kind", nullable = false)
    private String kind;

    @Column(name = "payload", nullable = false)
    private String payload;

    @Column(name = "invoice_uuid")
    private UUID invoiceUuid;

    @Column(name = "payment_uuid")
    private UUID paymentUuid;

    @Column(name = "parked_at", nullable = false, insertable = false, updatable = false)
    private Instant parkedAt;

    protected ParkedPaymentEvent() {
        // JPA only
    }

    public String getEventKey() {
        return eventKey;
    }

    public String getKind() {
        return kind;
    }

    public String getPayload() {
        return payload;
    }

    public UUID getInvoiceUuid() {
        return invoiceUuid;
    }

    public UUID getPaymentUuid() {
        return paymentUuid;
    }

    public Instant getParkedAt() {
        return parkedAt;
    }
}
