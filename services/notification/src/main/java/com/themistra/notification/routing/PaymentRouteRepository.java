package com.themistra.notification.routing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

interface PaymentRouteRepository extends JpaRepository<PaymentRoute, UUID> {

    /** Insert-if-absent: the first writer wins, and {@code DO NOTHING} needs only the INSERT grant. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "INSERT INTO notifications.payment_routes (payment_uuid, invoice_uuid) "
            + "VALUES (:paymentUuid, :invoiceUuid) "
            + "ON CONFLICT (payment_uuid) DO NOTHING",
            nativeQuery = true)
    int insertIfAbsent(@Param("paymentUuid") UUID paymentUuid, @Param("invoiceUuid") UUID invoiceUuid);
}
