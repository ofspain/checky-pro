package com.themistra.notification.template;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A versioned, seeded message template - maps onto {@code notifications.templates}, already
 * migrated and seeded (14 rows, all {@code version = 1}) by T02's own
 * {@code V1__notifications_baseline.sql} / {@code V3__seed_launch_templates.sql}.
 *
 * <p>Read-only in practice: no write API exists anywhere in this spec (L9: "seeded/versioned, not
 * runtime-edited") - nothing in this codebase constructs or persists an instance.</p>
 */
@Entity
@Table(name = "templates", schema = "notifications")
public class Template {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "channel", nullable = false)
    private String channel;

    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "subject")
    private String subject;

    @Column(name = "body", nullable = false)
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Template() {
        // JPA only
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getChannel() {
        return channel;
    }

    public int getVersion() {
        return version;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
