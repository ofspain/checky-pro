package com.themistra.payment.chainverify;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Maps {@code payments.chain_verifications} ({@code V11__payment_chain_verifications.sql}) — one
 * row per on-demand verification asked for from the public landing page.
 *
 * <p><b>{@code @Immutable}, because the table is append-only.</b> {@code payment_app} holds
 * {@code INSERT} and {@code SELECT} and nothing else, so an {@code UPDATE} Hibernate decided to
 * issue on its own would fail with "permission denied" at runtime rather than at review. The same
 * annotation is on {@code VerificationRecord} and {@code LedgerEntry} for the same reason.</p>
 *
 * <p><b>Every fact is nullable and that is the contract, not slack.</b> crypto-service answers
 * {@code HELD} when its three providers disagree, and a {@code HELD} answer deliberately carries no
 * facts. A row holding {@code outcome = 'HELD'} with nulls beside it is a complete record of what
 * was agreed: nothing. Reading a null here as "we did not store it" would be the one misreading
 * worth guarding against.</p>
 *
 * <p>Fields are non-final because JPA requires a no-arg constructor and mutable fields; each is set
 * exactly once, by {@link #record}, and never reassigned.</p>
 */
@Entity
@Immutable
@Table(name = "chain_verifications")
public class ChainVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "verification_uuid", nullable = false, updatable = false)
    private UUID verificationUuid;

    @Column(nullable = false, updatable = false)
    private String chain;

    @Column(name = "tx_hash", nullable = false, updatable = false)
    private String txHash;

    @Column(nullable = false, updatable = false)
    private String outcome;

    @Column(updatable = false)
    private String existence;

    @Column(updatable = false)
    private Integer confirmations;

    @Column(name = "token_contract_address", updatable = false)
    private String tokenContractAddress;

    @Column(name = "from_address", updatable = false)
    private String fromAddress;

    @Column(name = "to_address", updatable = false)
    private String toAddress;

    /** Token base units. {@code NUMERIC(78,0)} on the way in and out — never a {@code double}. */
    @Column(updatable = false)
    private BigDecimal amount;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    protected ChainVerification() {
    }

    static ChainVerification record(UUID verificationUuid, String chain, String txHash, String outcome,
            String existence, Integer confirmations, String tokenContractAddress, String fromAddress,
            String toAddress, BigDecimal amount, Instant requestedAt) {
        ChainVerification verification = new ChainVerification();
        verification.verificationUuid = verificationUuid;
        verification.chain = chain;
        verification.txHash = txHash;
        verification.outcome = outcome;
        verification.existence = existence;
        verification.confirmations = confirmations;
        verification.tokenContractAddress = tokenContractAddress;
        verification.fromAddress = fromAddress;
        verification.toAddress = toAddress;
        verification.amount = amount;
        verification.requestedAt = requestedAt;
        return verification;
    }

    public Long getId() {
        return id;
    }

    public UUID getVerificationUuid() {
        return verificationUuid;
    }

    public String getChain() {
        return chain;
    }

    public String getTxHash() {
        return txHash;
    }

    public String getOutcome() {
        return outcome;
    }

    public String getExistence() {
        return existence;
    }

    public Integer getConfirmations() {
        return confirmations;
    }

    public String getTokenContractAddress() {
        return tokenContractAddress;
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public String getToAddress() {
        return toAddress;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }
}
