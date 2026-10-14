package com.themistra.payment.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Maps {@code payments} in {@code V1__payments_baseline.sql} exactly (immutable, T02) — every
 * real column is mapped here, even {@code amountDiscrepancy}/{@code reorgedAt}/{@code attestedAt}
 * (only populated by later tasks: 13, 12, 19 respectively), mirroring {@code Invoice}'s own
 * convention of mapping the full real schema from the start rather than growing the entity
 * incrementally.
 *
 * <p><b>No mutator methods (T11, mirrors T08's own corrected lesson).</b> Every state/field
 * change goes through {@link PaymentRepository}'s own atomic, conditional {@code UPDATE ...
 * WHERE} queries - never a load-mutate-save of this entity. Unlike {@code Invoice} (one writer
 * at a time in practice) or {@code VerificationRecord} (append-only, no writer at all after
 * creation), a {@code Payment} row can legitimately be targeted by {@code chain.tx.seen}/
 * {@code chain.tx.confirmed}/{@code chain.tx.finalized} - three separate Kafka topics with no
 * cross-topic ordering guarantee - so a detached-entity merge-save here would reintroduce exactly
 * the lost-update race Kimi's T08 Phase 8 review found in {@code InvoiceExpirySweepJob}'s own
 * original design.</p>
 *
 * <p><b>{@code amount = 0} at creation (T11, Phase 0/1/2 Finding C, Phase 3/4 Decision 2).</b>
 * Neither {@code chain.tx.seen} nor {@code chain.tx.confirmed} carries an observed amount - only
 * {@code chain.tx.finalized} does. The column is {@code NOT NULL}, so {@link
 * PaymentService#applySeen} creates the row with an explicit {@code BigDecimal.ZERO} placeholder,
 * overwritten with the real observed value once {@code chain.tx.finalized} arrives.</p>
 *
 * <p><b>No row is ever persisted in {@code CREATED}/{@code WATCHING} (T11, Phase 3/4 Finding 5,
 * a genuine Human Approval decision - see {@code artifacts/04-frozen-task-brief.md}).</b> R6
 * (watch-ack moves tracking {@code CREATED → WATCHING}) is a confirmed, disclosed specification
 * gap no task in this 29-task breakdown implements - watch-ack is a synchronous T06 REST
 * response, not an async event, and T06 never created a {@code Payment} row. The first row this
 * service ever persists for a given invoice is already {@code SEEN}, created by {@link
 * PaymentService#applySeen} on the first {@code chain.tx.seen}.</p>
 */
@Entity
@Table(name = "payments")
public class Payment {

    private static final int AMOUNT_PRECISION = 78;
    private static final int AMOUNT_SCALE = 0;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_uuid", nullable = false, unique = true)
    private UUID paymentUuid;

    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    @Column(name = "chain", nullable = false, length = 32)
    private String chain;

    @Column(name = "tx_hash", nullable = false, length = 128)
    private String txHash;

    @Column(name = "from_address", length = 128)
    private String fromAddress;

    @Column(name = "to_address", nullable = false, length = 128)
    private String toAddress;

    @Column(name = "token_contract_address", nullable = false, length = 128)
    private String tokenContractAddress;

    @Column(name = "amount", nullable = false, precision = AMOUNT_PRECISION, scale = AMOUNT_SCALE)
    private BigDecimal amount;

    @Column(name = "confirmations", nullable = false)
    private int confirmations;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 16)
    private PaymentState state;

    @Column(name = "amount_discrepancy", length = 16)
    private String amountDiscrepancy;

    @Column(name = "address_poisoning_flag", nullable = false)
    private boolean addressPoisoningFlag;

    @Column(name = "seen_at")
    private Instant seenAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "finalized_at")
    private Instant finalizedAt;

    @Column(name = "reorged_at")
    private Instant reorgedAt;

    @Column(name = "attested_at")
    private Instant attestedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Payment() {
        // JPA only
    }

    public static Payment createSeen(Long invoiceId, String chain, String txHash, String toAddress,
                                      String tokenContractAddress, int confirmations, Instant now) {
        Payment payment = new Payment();
        payment.paymentUuid = UUID.randomUUID();
        payment.invoiceId = invoiceId;
        payment.chain = chain;
        payment.txHash = txHash;
        payment.toAddress = toAddress;
        payment.tokenContractAddress = tokenContractAddress;
        payment.amount = BigDecimal.ZERO;
        payment.confirmations = confirmations;
        payment.state = PaymentState.SEEN;
        payment.addressPoisoningFlag = false;
        payment.seenAt = now;
        payment.createdAt = now;
        payment.updatedAt = now;
        return payment;
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

    public Long getId() {
        return id;
    }

    public UUID getPaymentUuid() {
        return paymentUuid;
    }

    public Long getInvoiceId() {
        return invoiceId;
    }

    public String getChain() {
        return chain;
    }

    public String getTxHash() {
        return txHash;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public String getToAddress() {
        return toAddress;
    }

    public String getTokenContractAddress() {
        return tokenContractAddress;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public int getConfirmations() {
        return confirmations;
    }

    public PaymentState getState() {
        return state;
    }

    public String getAmountDiscrepancy() {
        return amountDiscrepancy;
    }

    public boolean isAddressPoisoningFlag() {
        return addressPoisoningFlag;
    }

    public Instant getSeenAt() {
        return seenAt;
    }

    public Instant getConfirmedAt() {
        return confirmedAt;
    }

    public Instant getFinalizedAt() {
        return finalizedAt;
    }

    public Instant getReorgedAt() {
        return reorgedAt;
    }

    public Instant getAttestedAt() {
        return attestedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
