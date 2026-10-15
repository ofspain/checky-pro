package com.themistra.payment.receipt.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * T19 (R21, L7). Maps `ledger_entries` in `V1__payments_baseline.sql` exactly - the append-only
 * tamper-evident hash chain. {@code payment_app} has {@code INSERT}+{@code SELECT} only (no
 * {@code UPDATE}/{@code DELETE} anywhere, L7), already granted since {@code V2}.
 *
 * <p><b>The primary key column is {@code seq}, not {@code id}</b> (Phase 3/4, Kimi Finding 11) -
 * the real DDL names it {@code seq BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY}, not the
 * {@code id} convention every other entity in this codebase uses.</p>
 *
 * <p>{@code receiptId} is a plain {@code Long}, never a {@code @ManyToOne} to {@code Receipt}
 * (L12, Phase 3/4 Finding 10) - {@code receipt} and {@code receipt.ledger} are genuinely
 * different Java packages (package-private visibility does not span sub-packages), and more
 * fundamentally this avoids a JPA relationship across what the module-boundary rule treats as
 * two separate aggregates.</p>
 *
 * <p>{@code payload} (JSONB) mirrors {@code VerificationRecord.detail}'s own established
 * {@code @JdbcTypeCode(SqlTypes.JSON)} mapping and defensive-copy discipline (Phase 8, Kimi
 * Finding 1 on that class) - a {@code Map} is mutable by reference, so both {@link #create} and
 * {@link #getPayload()} copy it rather than sharing the caller's own instance.</p>
 *
 * <p><b>{@code @Immutable} is load-bearing, not decorative (Phase 11, a real bug found and fixed
 * while adding the Phase 10 test suite).</b> Confirmed empirically: appending a payload whose
 * {@code Map} contains any non-{@code String} value (e.g. {@code tokenDecimals} as a {@code
 * Short} - exactly what the real {@code ReceiptStore.toCanonicalMap}-derived ledger payload
 * always contains) made Hibernate issue a spurious {@code UPDATE ledger_entries SET ...} to the
 * same row immediately after the correct {@code INSERT}, within the same transaction - rejected
 * by {@code payment_app}'s own intentional no-{@code UPDATE} grant (L7), meaning every single
 * real production {@code append()} call would have failed. Root cause: Hibernate's own dirty-
 * checking for a {@code @JdbcTypeCode(SqlTypes.JSON)} {@code Map} field takes its "last known
 * state" snapshot by round-tripping the value through JSON - a boxed {@code Short} survives
 * serialization but comes back as a different boxed numeric type (e.g. {@code Integer}) on that
 * round-trip, and {@code Short(6).equals(Integer(6))} is {@code false} in Java, so Hibernate
 * concludes the field "changed" and re-flushes an UPDATE that was never actually needed. {@code
 * @Immutable} tells Hibernate this entity is never updated after insert - skipping dirty-
 * checking entirely - which both fixes this bug as a side effect and is the semantically correct
 * annotation anyway: L7 itself says "no UPDATE/DELETE ever."</p>
 */
@Entity
@Immutable
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seq")
    private Long seq;

    @Column(name = "receipt_id", nullable = false, unique = true)
    private Long receiptId;

    /** {@code @JdbcTypeCode(SqlTypes.CHAR)} on both hash fields is load-bearing, not decorative
     * - mirrors crypto-service's own already-solved {@code Attestation.receiptDigest} precedent
     * for the identical {@code CHAR(64)}-vs-Hibernate's-default-{@code VARCHAR} schema-
     * validation mismatch (confirmed by a real failed context-startup run before fixing, not
     * assumed). */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "prev_hash", nullable = false, length = 64)
    private String prevHash;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "entry_hash", nullable = false, unique = true, length = 64)
    private String entryHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false)
    private Map<String, Object> payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerEntry() {
        // JPA only
    }

    public static LedgerEntry create(Long receiptId, String prevHash, String entryHash,
            Map<String, Object> payload, Instant createdAt) {
        LedgerEntry entry = new LedgerEntry();
        entry.receiptId = receiptId;
        entry.prevHash = prevHash;
        entry.entryHash = entryHash;
        entry.payload = copyOf(payload);
        entry.createdAt = createdAt;
        return entry;
    }

    private static Map<String, Object> copyOf(Map<String, Object> source) {
        return source == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    public Long getSeq() {
        return seq;
    }

    public Long getReceiptId() {
        return receiptId;
    }

    public String getPrevHash() {
        return prevHash;
    }

    public String getEntryHash() {
        return entryHash;
    }

    public Map<String, Object> getPayload() {
        return copyOf(payload);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
