package com.themistra.auth.authz;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_roles")
public class AccountRoleAssignment {

    @EmbeddedId
    private AccountRoleId id;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @Column(name = "granted_by")
    private UUID grantedBy;

    protected AccountRoleAssignment() {
        // JPA only
    }

    public static AccountRoleAssignment of(UUID accountUuid, Long roleId, UUID grantedBy, Instant now) {
        AccountRoleAssignment assignment = new AccountRoleAssignment();
        assignment.id = new AccountRoleId(accountUuid, roleId);
        assignment.grantedBy = grantedBy;
        assignment.grantedAt = now;
        return assignment;
    }

    public AccountRoleId getId() {
        return id;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }

    public UUID getGrantedBy() {
        return grantedBy;
    }
}
