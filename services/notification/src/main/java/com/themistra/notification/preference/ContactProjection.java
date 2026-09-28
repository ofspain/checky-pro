package com.themistra.notification.preference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Recipient-contact projection (O1, Q1) - maps onto {@code notifications.contact_projection},
 * already migrated by T02's own {@code V1__notifications_baseline.sql}. {@code accountUuid} is the
 * client-assigned {@code @Id} - the account's own external identifier IS the primary key, mirroring
 * T04's own {@code ProcessedEvent.eventKey} shape.
 *
 * <p>Read-only in practice: the only write path is
 * {@link ContactProjectionRepository#upsertEmail}, a native upsert that binds its parameters
 * directly and never constructs an instance of this class (mirrors T04's own
 * {@code ProcessedEvent}, post-Phase-9 shape - see {@code IdempotencyGuard}'s own Javadoc for why
 * a JPA {@code save} path is deliberately avoided for this module's write paths).</p>
 *
 * <p>{@code email}'s {@code columnDefinition = "citext"} is required, not cosmetic: without it,
 * Hibernate's {@code ddl-auto=validate} fails at startup against the real {@code citext} column
 * (verified against {@code services/auth}'s own {@code Account.email}, which hit the identical
 * problem). No {@code @JdbcType} is declared here - this task never queries by {@code email} (only
 * by {@code accountUuid}), so the parameter-binding half of auth's own fix
 * ({@code CitextJdbcType}) is deliberately deferred to whichever future task first needs a
 * query-by-{@code email}.</p>
 *
 * <p>{@code displayName} is always {@code null} for every row this task creates or updates - no
 * data source for a display name exists anywhere in {@code auth-service}'s own domain (verified
 * directly, not assumed). The column is mapped so future code can read it once something else
 * populates it.</p>
 */
@Entity
@Table(name = "contact_projection", schema = "notifications")
public class ContactProjection {

    @Id
    @Column(name = "account_uuid")
    private UUID accountUuid;

    @Column(name = "email", columnDefinition = "citext")
    private String email;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ContactProjection() {
        // JPA only
    }

    public UUID getAccountUuid() {
        return accountUuid;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
