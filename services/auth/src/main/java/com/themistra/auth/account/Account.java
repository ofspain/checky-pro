package com.themistra.auth.account;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * The account aggregate. All state transitions go through the guarded methods below so an
 * illegal transition is unrepresentable; {@code account_uuid} is the external subject
 * ({@code sub} claim) — the numeric primary key never leaves this service.
 */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_uuid", nullable = false, unique = true, updatable = false)
    private UUID accountUuid;

    /** {@code citext} (case-insensitive text, agents.md) - JdbcTypeCode.OTHER matches how
     * Postgres reports this column's type so Hibernate's schema validation accepts it. */
    @JdbcTypeCode(SqlTypes.OTHER)
    @Column(name = "email", nullable = false, unique = true, columnDefinition = "citext")
    private String email;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private AccountStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Account() {
        // JPA only
    }

    /** Creates a new account awaiting email verification. The only way to construct one. */
    public static Account register(String email, String passwordHash) {
        Account account = new Account();
        account.accountUuid = UUID.randomUUID();
        account.email = email;
        account.emailVerified = false;
        account.passwordHash = passwordHash;
        account.status = AccountStatus.PENDING_VERIFICATION;
        return account;
    }

    /** PENDING_VERIFICATION → ACTIVE. */
    public void activateEmail() {
        requireStatus(AccountStatus.PENDING_VERIFICATION, "activate email");
        this.emailVerified = true;
        this.status = AccountStatus.ACTIVE;
    }

    /** ACTIVE | LOCKED | PENDING_VERIFICATION → SUSPENDED (admin/compliance action). */
    public void suspend() {
        if (status == AccountStatus.SUSPENDED || status == AccountStatus.DELETED) {
            throw new InvalidAccountStateException(accountUuid, status, "suspend");
        }
        this.status = AccountStatus.SUSPENDED;
    }

    /** SUSPENDED → ACTIVE, or back to PENDING_VERIFICATION if the email was never verified. */
    public void reinstate() {
        requireStatus(AccountStatus.SUSPENDED, "reinstate");
        this.status = emailVerified ? AccountStatus.ACTIVE : AccountStatus.PENDING_VERIFICATION;
    }

    /** ACTIVE → LOCKED (lockout policy, authn module). */
    public void lock() {
        requireStatus(AccountStatus.ACTIVE, "lock");
        this.status = AccountStatus.LOCKED;
    }

    /** LOCKED → ACTIVE (lock expiry or admin unlock). */
    public void unlock() {
        requireStatus(AccountStatus.LOCKED, "unlock");
        this.status = AccountStatus.ACTIVE;
    }

    /** Terminal. Clears the credential; the row is retained for audit-trail integrity. */
    public void markDeleted() {
        if (status == AccountStatus.DELETED) {
            throw new InvalidAccountStateException(accountUuid, status, "delete");
        }
        this.status = AccountStatus.DELETED;
        this.passwordHash = null;
    }

    /**
     * Only an {@code ACTIVE} account may change its password (T08, R11) — narrower than the
     * previous {@code DELETED}-only guard. Provably backward-compatible with T07's
     * {@code resetPassword}: that caller always reaches this method with {@code status == ACTIVE}
     * already (it calls {@link #unlock()} first when {@code LOCKED}, and its own eligibility
     * pre-check already excludes every other non-{@code ACTIVE} status before ever getting here).
     */
    public void changePasswordHash(String newPasswordHash) {
        if (status != AccountStatus.ACTIVE) {
            throw new InvalidAccountStateException(accountUuid, status, "change password");
        }
        this.passwordHash = newPasswordHash;
    }

    public boolean canAuthenticate() {
        return status == AccountStatus.ACTIVE;
    }

    private void requireStatus(AccountStatus required, String action) {
        if (status != required) {
            throw new InvalidAccountStateException(accountUuid, status, action);
        }
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public UUID getAccountUuid() {
        return accountUuid;
    }

    public String getEmail() {
        return email;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
