package com.themistra.payment.invoice;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Maps {@code invoices} in {@code V1__payments_baseline.sql} exactly (immutable, T02) — 13
 * columns besides the surrogate {@code id}. {@code state} is {@code @Enumerated(STRING)} (the
 * default {@code ORDINAL} would mismatch the real {@code VARCHAR(16)} column); {@code amount} is
 * {@code @Column(precision = 78, scale = 0)} (the default {@code NUMERIC(19,2)} would fail this
 * service's {@code ddl-auto=validate}, set in T04); {@code tokenDecimals} is {@code short} to map
 * to {@code SMALLINT} (Phase 3 finding 2).
 *
 * <p><b>{@code createdAt}/{@code updatedAt} (Phase 3 finding 7).</b> {@link #create} always sets
 * both explicitly from the {@link java.time.Clock}-derived instant {@link
 * com.themistra.payment.invoice.InvoiceService} passes in — this is the path every current test
 * exercises. {@link #onCreate()}/{@link #onUpdate()} below are a last-resort {@code
 * Instant.now()} fallback only, mirroring {@code OutboxEvent}'s own identical, already-accepted
 * T04 pattern: a JPA entity cannot have an injected {@code Clock} (entities are not Spring
 * beans), so this fallback only fires if some future path ever persists/updates an {@code
 * Invoice} without going through {@link #create} or without otherwise setting {@code
 * updatedAt}. No caller takes that path within this task's own scope.</p>
 */
@Entity
@Table(name = "invoices")
public class Invoice {

    private static final int AMOUNT_PRECISION = 78;
    private static final int AMOUNT_SCALE = 0;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "invoice_uuid", nullable = false, unique = true)
    private UUID invoiceUuid;

    @Column(name = "merchant_account_uuid", nullable = false)
    private UUID merchantAccountUuid;

    @Column(name = "chain", nullable = false, length = 32)
    private String chain;

    @Column(name = "token_contract_address", nullable = false, length = 128)
    private String tokenContractAddress;

    @Column(name = "token_decimals", nullable = false)
    private short tokenDecimals;

    @Column(name = "amount", nullable = false, precision = AMOUNT_PRECISION, scale = AMOUNT_SCALE)
    private BigDecimal amount;

    @Column(name = "pay_to_address", nullable = false, length = 128)
    private String payToAddress;

    @Column(name = "reference", length = 140)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 16)
    private InvoiceState state;

    @Column(name = "watch_id")
    private UUID watchId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Invoice() {
        // JPA only
    }

    public static Invoice create(UUID invoiceUuid, UUID merchantAccountUuid, String chain,
                                  String tokenContractAddress, short tokenDecimals, BigDecimal amount,
                                  String payToAddress, String reference, Instant expiresAt, UUID watchId,
                                  Instant now) {
        Invoice invoice = new Invoice();
        invoice.invoiceUuid = invoiceUuid;
        invoice.merchantAccountUuid = merchantAccountUuid;
        invoice.chain = chain;
        invoice.tokenContractAddress = tokenContractAddress;
        invoice.tokenDecimals = tokenDecimals;
        invoice.amount = amount;
        invoice.payToAddress = payToAddress;
        invoice.reference = reference;
        invoice.watchId = watchId;
        invoice.state = InvoiceState.OPEN;
        invoice.expiresAt = expiresAt;
        invoice.createdAt = now;
        invoice.updatedAt = now;
        return invoice;
    }

    /** R5 (T08): a no-op unless the current state is {@code OPEN} (Phase 3 finding 2) - guards
     * the narrow race window between the sweep's own candidate query and this update, where a
     * {@code chain.tx.seen} event or a concurrent sweep run could already have moved the invoice
     * to a different state. */
    void markExpired() {
        if (state != InvoiceState.OPEN) {
            return;
        }
        state = InvoiceState.EXPIRED;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (updatedAt == null) {
            updatedAt = Instant.now();
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public UUID getInvoiceUuid() {
        return invoiceUuid;
    }

    public UUID getMerchantAccountUuid() {
        return merchantAccountUuid;
    }

    public String getChain() {
        return chain;
    }

    public String getTokenContractAddress() {
        return tokenContractAddress;
    }

    public short getTokenDecimals() {
        return tokenDecimals;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getPayToAddress() {
        return payToAddress;
    }

    public String getReference() {
        return reference;
    }

    public InvoiceState getState() {
        return state;
    }

    public UUID getWatchId() {
        return watchId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
