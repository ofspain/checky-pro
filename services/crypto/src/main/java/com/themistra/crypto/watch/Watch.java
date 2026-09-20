package com.themistra.crypto.watch;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigInteger;
import java.time.Instant;
import java.util.UUID;

/**
 * What the payment service asked us to look for.
 *
 * <p>Addresses are stored normalised. EIP-55 checksumming is a transport and display concern
 * (§6.3); whether an observation matches a watch must not depend on the casing the caller happened
 * to send.
 *
 * <p>The amount is base units as a {@link BigInteger}, never a converted decimal — the same
 * exactness the observation side keeps.
 */
@Entity
@Table(name = "watches")
public class Watch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** External identifier. The internal id never leaves the service. */
    @Column(name = "watch_uuid", nullable = false, updatable = false)
    private UUID watchUuid;

    /** The caller's own reference — its invoice id. The idempotency key. */
    @Column(name = "caller_reference", nullable = false, updatable = false)
    private String callerReference;

    @Column(name = "chain_id", nullable = false, updatable = false)
    private String chainId;

    @Column(name = "recipient_address", nullable = false, updatable = false)
    private String recipientAddress;

    @Column(name = "token_address", nullable = false, updatable = false)
    private String tokenAddress;

    @Column(name = "expected_amount", nullable = false, updatable = false)
    private BigInteger expectedAmount;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private WatchStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Watch() {
        // JPA
    }

    public Watch(UUID watchUuid, String callerReference, String chainId, String recipientAddress,
                 String tokenAddress, BigInteger expectedAmount, Instant expiresAt, Instant now) {
        this.watchUuid = watchUuid;
        this.callerReference = callerReference;
        this.chainId = chainId;
        this.recipientAddress = recipientAddress;
        this.tokenAddress = tokenAddress;
        this.expectedAmount = expectedAmount;
        this.expiresAt = expiresAt;
        this.status = WatchStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Whether this watch is currently active.
     *
     * <p>Active means active <em>and unexpired at the moment of asking</em>. Nothing sweeps rows
     * into {@link WatchStatus#EXPIRED}, so the clock is always part of the answer.
     */
    public boolean isActiveAt(Instant instant) {
        return status == WatchStatus.ACTIVE && expiresAt.isAfter(instant);
    }

    public void cancel(Instant now) {
        this.status = WatchStatus.CANCELLED;
        this.updatedAt = now;
    }

    /** Whether another registration under the same reference describes the same watch. */
    public boolean hasSameTermsAs(String chainId, String recipientAddress,
                                  String tokenAddress, BigInteger expectedAmount) {
        return this.chainId.equals(chainId)
                && this.recipientAddress.equals(recipientAddress)
                && this.tokenAddress.equals(tokenAddress)
                && this.expectedAmount.equals(expectedAmount);
    }

    public Long getId() {
        return id;
    }

    public UUID getWatchUuid() {
        return watchUuid;
    }

    public String getCallerReference() {
        return callerReference;
    }

    public String getChainId() {
        return chainId;
    }

    public String getRecipientAddress() {
        return recipientAddress;
    }

    public String getTokenAddress() {
        return tokenAddress;
    }

    public BigInteger getExpectedAmount() {
        return expectedAmount;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public WatchStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
