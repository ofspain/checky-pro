package com.themistra.crypto.token;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;

/**
 * One signed, versioned canonical-token allowlist entry (L7) - maps {@code chain.token_allowlist}
 * exactly as shipped by T02 (see {@code V1__chain_baseline.sql}). Append-only, matching the table's
 * own "Seeded, never runtime-edited" comment - no setters, protected no-arg constructor, public
 * static {@link #create}.
 *
 * <p><b>{@code symbol} is display-only, never identity (R13, L7).</b> {@link TokenValidator} never
 * consults it when resolving a token; it exists purely so a caller can show something human-readable.</p>
 *
 * <p>{@code signature} maps the schema's only {@code TEXT} column via {@code @JdbcTypeCode(SqlTypes.LONG32VARCHAR)}
 * - the same annotation family {@code OutboxEvent.payload} already uses for {@code SqlTypes.JSON}.
 * **Phase 9 (Kimi Phase 8 Issue 11): confirmed via direct bytecode inspection of Hibernate
 * 6.6.22.Final's own {@code PostgreSQLDialect.columnType(int)} that {@code SqlTypes.LONGVARCHAR} (-1)
 * is NOT one of its explicitly-mapped codes and falls through to the generic base-{@code Dialect}
 * default (not {@code "text"}), while {@code SqlTypes.LONG32VARCHAR} (4001) is explicitly mapped to
 * {@code "text"}** - the Phase 6 draft's original choice of {@code LONGVARCHAR} was simply wrong, not
 * merely unverified.</p>
 *
 * <p>Production code seeds rows only via {@link TokenAllowlistSeeder}, driven by config
 * ({@code TokenAllowlistProperties}) - no Flyway DML migration exists for this table (Phase 3 Kimi
 * Issue 1: a DML seed migration would have violated agents.md's "Flyway, DDL-only migrations" rule).
 * {@link #create} is still a public factory (matching every other entity's own convention in this
 * codebase) even though its only real caller is the seeder, not a manually-triggered application
 * flow.</p>
 */
@Entity
@Table(name = "token_allowlist", schema = "chain")
public class TokenAllowlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 32)
    private String chain;

    @Column(name = "contract_address", nullable = false, length = 128)
    private String contractAddress;

    @Column(nullable = false, length = 32)
    private String symbol;

    @Column(nullable = false)
    private short decimals;

    @Column(nullable = false)
    private int version;

    @JdbcTypeCode(SqlTypes.LONG32VARCHAR)
    @Column(nullable = false)
    private String signature;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TokenAllowlist() {
        // JPA only
    }

    public static TokenAllowlist create(String chain, String contractAddress, String symbol,
                                         int decimals, int version, String signature,
                                         Instant createdAt) {
        Objects.requireNonNull(chain, "chain");
        Objects.requireNonNull(contractAddress, "contractAddress");
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(signature, "signature");
        Objects.requireNonNull(createdAt, "createdAt");

        TokenAllowlist entry = new TokenAllowlist();
        entry.chain = chain;
        entry.contractAddress = contractAddress;
        entry.symbol = symbol;
        entry.decimals = toShort(decimals, "decimals");
        entry.version = version;
        entry.signature = signature;
        entry.createdAt = createdAt;
        return entry;
    }

    /** Phase 9 (Kimi Phase 8 Issue 10): bounded to 30, not {@code Short.MAX_VALUE} - real
     * ERC-20/TRC-20 token decimals are virtually always 0-18 (rarely up to ~24); a value like 600
     * would otherwise pass a short-overflow-only check as a plausible-looking but implausible entry. */
    private static final int MAX_DECIMALS = 30;

    private static short toShort(int value, String fieldName) {
        if (value < 0 || value > MAX_DECIMALS) {
            throw new IllegalArgumentException(
                    fieldName + " must be between 0 and " + MAX_DECIMALS + ", got " + value);
        }
        return (short) value;
    }

    public Long id() {
        return id;
    }

    public String chain() {
        return chain;
    }

    public String contractAddress() {
        return contractAddress;
    }

    public String symbol() {
        return symbol;
    }

    public short decimals() {
        return decimals;
    }

    public int version() {
        return version;
    }

    public String signature() {
        return signature;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
