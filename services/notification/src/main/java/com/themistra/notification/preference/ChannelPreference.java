package com.themistra.notification.preference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Per-account, per-category, per-channel opt-out record - maps onto
 * {@code notifications.channel_preferences}, already migrated by T02's own
 * {@code V1__notifications_baseline.sql}.
 *
 * <p>Read-only in practice: no write API exists anywhere in this spec (no
 * {@code PreferenceController} is named in {@code tasks.md} through T20) - {@code V6}'s own grant
 * is {@code SELECT} only. This class exists purely so {@link PreferenceResolver} can read a
 * recipient's own stored opt-out, if one exists; nothing in this codebase constructs or persists
 * an instance.</p>
 */
@Entity
@Table(name = "channel_preferences", schema = "notifications")
public class ChannelPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_uuid", nullable = false)
    private UUID accountUuid;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "channel", nullable = false)
    private String channel;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ChannelPreference() {
        // JPA only
    }

    public Long getId() {
        return id;
    }

    public UUID getAccountUuid() {
        return accountUuid;
    }

    public String getCategory() {
        return category;
    }

    public String getChannel() {
        return channel;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
