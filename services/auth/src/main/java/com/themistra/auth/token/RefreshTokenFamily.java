package com.themistra.auth.token;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_token_family")
public class RefreshTokenFamily {

    @Id
    @Column(name = "family_id")
    private UUID familyId;

    @Column(name = "authorization_id", nullable = false, unique = true)
    private String authorizationId;

    @Column(name = "principal_name", nullable = false)
    private String principalName;

    @Column(name = "device_label")
    private String deviceLabel;

    /** CHAR(64), not VARCHAR - JdbcTypeCode.CHAR matches how Postgres reports this column's type
     * (bpchar) so Hibernate's schema validation accepts it. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "current_token_hash", nullable = false, length = 64, columnDefinition = "char(64)")
    private String currentTokenHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "rotated_at", nullable = false)
    private Instant rotatedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_reason")
    private String revokedReason;

    protected RefreshTokenFamily() {
        // JPA only
    }

    public static RefreshTokenFamily start(String authorizationId, String principalName,
                                           String deviceLabel, String initialTokenHash, Instant now) {
        RefreshTokenFamily family = new RefreshTokenFamily();
        family.familyId = UUID.randomUUID();
        family.authorizationId = authorizationId;
        family.principalName = principalName;
        family.deviceLabel = deviceLabel;
        family.currentTokenHash = initialTokenHash;
        family.createdAt = now;
        family.rotatedAt = now;
        return family;
    }

    /** Records a rotation: the caller archives {@link #getCurrentTokenHash()} before calling this. */
    public void rotateTo(String newTokenHash, Instant now) {
        requireLive("rotate");
        this.currentTokenHash = newTokenHash;
        this.rotatedAt = now;
    }

    public void revoke(String reason, Instant now) {
        if (revokedAt != null) {
            return; // idempotent
        }
        this.revokedAt = now;
        this.revokedReason = reason;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    private void requireLive(String action) {
        if (isRevoked()) {
            throw new IllegalStateException(
                    "Refresh token family " + familyId + " is revoked; cannot " + action);
        }
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public String getAuthorizationId() {
        return authorizationId;
    }

    public String getPrincipalName() {
        return principalName;
    }

    public String getDeviceLabel() {
        return deviceLabel;
    }

    public String getCurrentTokenHash() {
        return currentTokenHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRotatedAt() {
        return rotatedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public String getRevokedReason() {
        return revokedReason;
    }
}
