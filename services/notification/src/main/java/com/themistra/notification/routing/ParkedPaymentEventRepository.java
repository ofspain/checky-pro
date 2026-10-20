package com.themistra.notification.routing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

interface ParkedPaymentEventRepository extends JpaRepository<ParkedPaymentEvent, String> {

    /** Insert-if-absent: a redelivered event is deduplicated before it could park twice, but a
     * repeat park is still a quiet no-op rather than a key violation. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "INSERT INTO notifications.parked_payment_events (event_key, kind, payload, invoice_uuid, payment_uuid) "
            + "VALUES (:eventKey, :kind, :payload, CAST(:invoiceUuid AS UUID), CAST(:paymentUuid AS UUID)) "
            + "ON CONFLICT (event_key) DO NOTHING",
            nativeQuery = true)
    int park(@Param("eventKey") String eventKey, @Param("kind") String kind, @Param("payload") String payload,
             @Param("invoiceUuid") String invoiceUuid, @Param("paymentUuid") String paymentUuid);

    /** Every parked event for an invoice: those naming it directly, and those naming only a
     * payment whose route now points at it. Lifecycle order (seen, finalized, receipt), then
     * arrival order, so a merchant never hears "finalized" before "seen". */
    @Query(value = "SELECT * FROM notifications.parked_payment_events "
            + "WHERE invoice_uuid = :invoiceUuid OR payment_uuid IN "
            + "(SELECT payment_uuid FROM notifications.payment_routes WHERE invoice_uuid = :invoiceUuid) "
            + "ORDER BY CASE kind WHEN 'payment.seen' THEN 1 WHEN 'payment.finalized' THEN 2 ELSE 3 END, "
            + "parked_at, event_key",
            nativeQuery = true)
    List<ParkedPaymentEvent> findForInvoice(@Param("invoiceUuid") UUID invoiceUuid);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM notifications.parked_payment_events WHERE event_key = :eventKey", nativeQuery = true)
    int release(@Param("eventKey") String eventKey);

    /** Keys of events parked before {@code cutoff}, oldest first (expiry). */
    @Query(value = "SELECT event_key FROM notifications.parked_payment_events WHERE parked_at < :cutoff "
            + "ORDER BY parked_at, event_key",
            nativeQuery = true)
    List<String> findKeysParkedBefore(@Param("cutoff") Instant cutoff);

    /** Removes the given expired events. The {@code parked_at} condition is repeated so a row
     * re-parked under the same key after the select is never removed early. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "DELETE FROM notifications.parked_payment_events "
            + "WHERE event_key IN (:eventKeys) AND parked_at < :cutoff",
            nativeQuery = true)
    int expire(@Param("eventKeys") Collection<String> eventKeys, @Param("cutoff") Instant cutoff);
}
