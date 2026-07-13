package com.themistra.auth.authz;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A named authority (e.g. USER, MERCHANT, ADMIN, COMPLIANCE) — the catalog, not an assignment. */
@Entity
@Table(name = "roles")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 64)
    private String name;

    @Column(name = "description")
    private String description;

    protected Role() {
        // JPA only
    }

    public static Role create(String name, String description) {
        Role role = new Role();
        role.name = name;
        role.description = description;
        return role;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }
}
