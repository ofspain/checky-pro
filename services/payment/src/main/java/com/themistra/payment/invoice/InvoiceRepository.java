package com.themistra.payment.invoice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    List<Invoice> findByMerchantAccountUuidOrderByCreatedAtDesc(UUID merchantAccountUuid);

    Optional<Invoice> findByInvoiceUuidAndMerchantAccountUuid(UUID invoiceUuid, UUID merchantAccountUuid);

    /** T08 (R5, Phase 3/4 finding 1): excludes any invoice with a {@code payments} row in
     * {@code CREATED}/{@code WATCHING}/{@code SEEN}/{@code CONFIRMING} - the full non-terminal
     * set `design.md` O2 names, not only {@code SEEN}/{@code CONFIRMING}. No {@code Payment} JPA
     * entity exists yet (task 11's own scope), so this is a native query against the real,
     * already-migrated {@code payments.payments} table. */
    @Query(value = """
            SELECT i.* FROM payments.invoices i
            WHERE i.state = 'OPEN' AND i.expires_at <= :now
            AND NOT EXISTS (
                SELECT 1 FROM payments.payments p
                WHERE p.invoice_id = i.id
                AND p.state IN ('CREATED', 'WATCHING', 'SEEN', 'CONFIRMING')
            )
            """, nativeQuery = true)
    List<Invoice> findExpiredCandidatesWithoutInFlightPayment(@Param("now") Instant now);
}
