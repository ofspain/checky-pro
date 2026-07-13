package com.themistra.auth.token;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One row per superseded refresh-token hash. A hit here on lookup means a replay (D-003). */
@Entity
@Table(name = "refresh_token_archive")
public class RefreshTokenArchiveEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "superseded_at", nullable = false)
    private Instant supersededAt;

    protected RefreshTokenArchiveEntry() {
        // JPA only
    }

    public RefreshTokenArchiveEntry(UUID familyId, String tokenHash, Instant supersededAt) {
        this.familyId = familyId;
        this.tokenHash = tokenHash;
        this.supersededAt = supersededAt;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getSupersededAt() {
        return supersededAt;
    }
}
