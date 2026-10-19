package com.themistra.notification.routing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

interface InvoiceRouteRepository extends JpaRepository<InvoiceRoute, UUID> {

    /** Insert-if-absent, except that a route stored without decimals takes them from a later
     * write (open-issues sweep S2). The merchant is never changed: the first writer wins. Known
     * decimals are never overwritten. The fill needs V17's UPDATE grant on {@code token_decimals}
     * only. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "INSERT INTO notifications.invoice_routes AS r (invoice_uuid, merchant_account_uuid, token_decimals) "
            + "VALUES (:invoiceUuid, :merchantAccountUuid, CAST(:tokenDecimals AS SMALLINT)) "
            + "ON CONFLICT (invoice_uuid) DO UPDATE SET token_decimals = EXCLUDED.token_decimals "
            + "WHERE r.token_decimals IS NULL AND EXCLUDED.token_decimals IS NOT NULL",
            nativeQuery = true)
    int insertOrFillDecimals(@Param("invoiceUuid") UUID invoiceUuid,
                       @Param("merchantAccountUuid") UUID merchantAccountUuid,
                       @Param("tokenDecimals") Short tokenDecimals);
}
