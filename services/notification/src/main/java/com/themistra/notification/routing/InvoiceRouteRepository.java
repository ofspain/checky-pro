package com.themistra.notification.routing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

interface InvoiceRouteRepository extends JpaRepository<InvoiceRoute, UUID> {

    /** Insert-if-absent: the first writer wins, and {@code DO NOTHING} needs only the INSERT grant. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "INSERT INTO notifications.invoice_routes (invoice_uuid, merchant_account_uuid, token_decimals) "
            + "VALUES (:invoiceUuid, :merchantAccountUuid, CAST(:tokenDecimals AS SMALLINT)) "
            + "ON CONFLICT (invoice_uuid) DO NOTHING",
            nativeQuery = true)
    int insertIfAbsent(@Param("invoiceUuid") UUID invoiceUuid,
                       @Param("merchantAccountUuid") UUID merchantAccountUuid,
                       @Param("tokenDecimals") Short tokenDecimals);
}
