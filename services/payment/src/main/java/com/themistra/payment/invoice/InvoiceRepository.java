package com.themistra.payment.invoice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface InvoiceRepository extends JpaRepository<Invoice, Long> {

    List<Invoice> findByMerchantAccountUuidOrderByCreatedAtDesc(UUID merchantAccountUuid);

    Optional<Invoice> findByInvoiceUuidAndMerchantAccountUuid(UUID invoiceUuid, UUID merchantAccountUuid);

    /** T11: every consumed {@code chain.tx.*} event schema guarantees {@code watchId} in its own
     * {@code required} list (unlike {@code invoiceUuid}, present but optional in all of them) -
     * the one correlation key {@code ChainEventConsumer} can rely on. Called only through {@link
     * com.themistra.payment.invoice.InvoiceService#findByWatchId} (Phase 3 Finding 2) - never
     * directly from the {@code consumer} package, which must not depend on this repository. */
    Optional<Invoice> findByWatchId(UUID watchId);

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

    /** T08 (Phase 9, Kimi Phase 8 findings 1/3/6): a single atomic, conditional update - no
     * entity load, no merge, no lost-update window. Returns the number of rows actually updated
     * (0 or 1); 0 means the invoice was no longer in {@code expectedCurrentState} by the time
     * this ran (e.g. a payment became in-flight first) - not an error, the caller treats this as
     * "correctly left alone," not a failure. Bypasses {@code @PreUpdate} (a JPQL bulk update
     * never loads an entity instance), so {@code updatedAt} is set explicitly here instead. */
    @Modifying
    @Query("UPDATE Invoice i SET i.state = :newState, i.updatedAt = :now "
            + "WHERE i.id = :id AND i.state = :expectedCurrentState")
    int updateStateIfCurrentState(@Param("id") Long id, @Param("expectedCurrentState") InvoiceState expectedCurrentState,
                                   @Param("newState") InvoiceState newState, @Param("now") Instant now);
}
