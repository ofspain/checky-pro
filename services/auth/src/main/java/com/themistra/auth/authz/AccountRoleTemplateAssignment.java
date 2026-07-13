package com.themistra.auth.authz;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_role_templates")
public class AccountRoleTemplateAssignment {

    @EmbeddedId
    private AccountRoleTemplateId id;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    @Column(name = "granted_by")
    private UUID grantedBy;

    protected AccountRoleTemplateAssignment() {
        // JPA only
    }

    public static AccountRoleTemplateAssignment of(
            UUID accountUuid, Long roleTemplateId, UUID grantedBy, Instant now) {
        AccountRoleTemplateAssignment assignment = new AccountRoleTemplateAssignment();
        assignment.id = new AccountRoleTemplateId(accountUuid, roleTemplateId);
        assignment.grantedBy = grantedBy;
        assignment.grantedAt = now;
        return assignment;
    }

    public AccountRoleTemplateId getId() {
        return id;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }

    public UUID getGrantedBy() {
        return grantedBy;
    }
}
