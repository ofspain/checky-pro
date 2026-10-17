package com.themistra.payment.export;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * T22 (R27) - a three-table reporting read owned by no entity's own module (L12): a plain
 * {@link EntityManager}-backed class, never a {@code JpaRepository}, extending
 * {@code InvoiceRepository}'s own established "native query issued by a module that owns neither
 * target table" precedent (T08/T12's {@code findExpiredCandidatesWithoutInFlightPayment}) to a
 * query that in fact owns none of its three tables, not just one.
 *
 * <p>{@code amount}/{@code payToAddress} are read from {@code payments} (the <b>observed</b>
 * on-chain values), never {@code invoices} (Phase 3 Kimi Finding 2, accepted at Phase 4) - the
 * two tables carry duplicate-looking column names for deliberately different things (see
 * {@code V1__payments_baseline.sql}'s own "expected amount" vs. "observed amount" comments).
 * Only {@code ATTESTED} payments are included (L2 continuity: a {@code Receipt} row cannot exist
 * any earlier). Ordered {@code finalized_at ASC, payment_uuid ASC} (Phase 3 Kimi Finding 4,
 * accepted) for a reproducible export.</p>
 *
 * <p>Column runtime types were confirmed empirically against this exact Hibernate/Postgres
 * pairing (Phase 0's own flagged unknown) before writing {@link #toRow}: {@code uuid} ->
 * {@link UUID}, {@code smallint} -> {@link Short}, {@code numeric} -> {@link BigDecimal},
 * {@code timestamptz} -> {@link Instant} directly - never {@link java.sql.Timestamp}.</p>
 */
@Repository
class TransactionExportRepository {

    // Phase 9 Kimi Finding 5, accepted: this column order is positional and must stay in exact
    // sync with toRow(Object[]) below - reordering, adding, or removing a column here without
    // updating that method compiles cleanly but fails at runtime (ClassCastException) or
    // silently produces wrong data. Change both together.
    private static final String SQL = """
            SELECT i.invoice_uuid, p.payment_uuid, r.receipt_uuid, p.chain, p.tx_hash,
                   p.token_contract_address, i.token_decimals, p.amount, p.to_address,
                   p.finalized_at, p.attested_at, r.s3_bucket, r.s3_key
            FROM payments.payments p
            JOIN payments.invoices i ON i.id = p.invoice_id
            JOIN payments.receipts r ON r.payment_id = p.id
            WHERE i.merchant_account_uuid = :merchantAccountUuid
              AND p.state = 'ATTESTED'
              AND p.finalized_at >= :fromInstant
              AND p.finalized_at <= :toInstant
            ORDER BY p.finalized_at ASC, p.payment_uuid ASC
            """;

    private final EntityManager entityManager;

    TransactionExportRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @SuppressWarnings("unchecked")
    List<TransactionExportRow> findAttestedTransactions(UUID merchantAccountUuid, Instant from, Instant to) {
        Query query = entityManager.createNativeQuery(SQL)
                .setParameter("merchantAccountUuid", merchantAccountUuid)
                .setParameter("fromInstant", from)
                .setParameter("toInstant", to);
        List<Object[]> rows = query.getResultList();
        return rows.stream().map(TransactionExportRepository::toRow).toList();
    }

    // Positional mapping - must stay in exact column-order sync with SQL above (Phase 9 Kimi
    // Finding 5).
    private static TransactionExportRow toRow(Object[] row) {
        UUID invoiceUuid = (UUID) row[0];
        UUID paymentUuid = (UUID) row[1];
        UUID receiptUuid = (UUID) row[2];
        String chain = (String) row[3];
        String txHash = (String) row[4];
        String tokenContractAddress = (String) row[5];
        short tokenDecimals = (Short) row[6];
        String amount = ((BigDecimal) row[7]).toPlainString();
        String payToAddress = (String) row[8];
        Instant finalizedAt = (Instant) row[9];
        Instant attestedAt = (Instant) row[10];
        String s3Bucket = (String) row[11];
        String s3Key = (String) row[12];
        String receiptUri = "s3://" + s3Bucket + "/" + s3Key + ".json";
        return new TransactionExportRow(invoiceUuid, paymentUuid, receiptUuid, chain, txHash,
                tokenContractAddress, tokenDecimals, amount, payToAddress, finalizedAt, attestedAt, receiptUri);
    }
}
