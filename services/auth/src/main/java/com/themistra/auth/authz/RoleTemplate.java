package com.themistra.auth.authz;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A named bundle of roles (kept from the reference — genuinely good admin UX, gap-analysis §6).
 * Expansion into constituent role names happens at token issuance (RoleService), never cached:
 * editing a template's membership affects only future tokens, by design.
 */
@Entity
@Table(name = "role_templates")
public class RoleTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, unique = true, length = 64)
    private String name;

    @Column(name = "description")
    private String description;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "role_template_roles",
            joinColumns = @JoinColumn(name = "role_template_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new LinkedHashSet<>();

    protected RoleTemplate() {
        // JPA only
    }

    public static RoleTemplate create(String name, String description, Set<Role> roles) {
        RoleTemplate template = new RoleTemplate();
        template.name = name;
        template.description = description;
        template.roles = new LinkedHashSet<>(roles);
        return template;
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

    public Set<Role> getRoles() {
        return Set.copyOf(roles);
    }
}
