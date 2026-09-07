package com.themistra.crypto.watch;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A registered address-watch for a Payment invoice (R18/R19) - maps {@code chain.watches} exactly as
 * shipped by T02 (see {@code V1__chain_baseline.sql}). {@code watchId} is the public identifier
 * returned to callers, deliberately distinct from the surrogate {@code id} primary key - the DDL gives
 * {@code watch_id} no default, so it is always generated in application code
 * ({@link WatchService#register}), never left to the database.
 *
 * <p>No raw setters, and no {@code unregister()} instance mutator either - unlike {@code
 * ProviderHealth}'s own update-in-place precedent, the {@code REGISTERED -> UNREGISTERED} transition is
 * a single atomic conditional {@code UPDATE} issued directly by {@link WatchRepository#markUnregisteredIfRegistered},
 * not a load-then-save entity mutation (Phase 3 Kimi Finding 4 - race-safe by construction: two
 * concurrent {@code DELETE}s can never both "win" and double-set {@code unregisteredAt}, since at most
 * one {@code UPDATE ... WHERE status = 'REGISTERED'} can match a given row).</p>
 */
@Entity
@Table(name = "watches", schema = "chain")
public class Watch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "watch_id", nullable = false, unique = true)
    private UUID watchId;

    @Column(name = "invoice_uuid", nullable = false)
    private UUID invoiceUuid;

    @Column(nullable = false, length = 32)
    private String chain;

    @Column(nullable = false, length = 128)
    private String address;

    @Column(name = "token_contract_address", nullable = false, length = 128)
    private String tokenContractAddress;

    @Column(name = "expected_amount", nullable = false, precision = 78, scale = 0)
    private BigDecimal expectedAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private WatchStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "unregistered_at")
    private Instant unregisteredAt;

    protected Watch() {
        // JPA only
    }

    /** A freshly registered watch (R18) - {@code status = REGISTERED}, {@code unregisteredAt = null}.
     * {@code watchId} and {@code now} are supplied by the caller ({@link WatchService#register}), never
     * generated here, so this factory stays a pure, deterministic function of its arguments. */
    public static Watch register(UUID watchId, UUID invoiceUuid, String chain, String address,
            String tokenContractAddress, BigDecimal expectedAmount, Instant expiresAt, Instant now) {
        Objects.requireNonNull(watchId, "watchId");
        Objects.requireNonNull(invoiceUuid, "invoiceUuid");
        Objects.requireNonNull(chain, "chain");
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(tokenContractAddress, "tokenContractAddress");
        Objects.requireNonNull(expectedAmount, "expectedAmount");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(now, "now");

        Watch watch = new Watch();
        watch.watchId = watchId;
        watch.invoiceUuid = invoiceUuid;
        watch.chain = chain;
        watch.address = address;
        watch.tokenContractAddress = tokenContractAddress;
        watch.expectedAmount = expectedAmount;
        watch.status = WatchStatus.REGISTERED;
        watch.expiresAt = expiresAt;
        watch.createdAt = now;
        return watch;
    }

    public Long id() {
        return id;
    }

    public UUID watchId() {
        return watchId;
    }

    public UUID invoiceUuid() {
        return invoiceUuid;
    }

    public String chain() {
        return chain;
    }

    public String address() {
        return address;
    }

    public String tokenContractAddress() {
        return tokenContractAddress;
    }

    public BigDecimal expectedAmount() {
        return expectedAmount;
    }

    public WatchStatus status() {
        return status;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant unregisteredAt() {
        return unregisteredAt;
    }
}
