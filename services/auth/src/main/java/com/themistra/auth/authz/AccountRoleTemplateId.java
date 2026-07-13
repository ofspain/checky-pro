package com.themistra.auth.authz;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class AccountRoleTemplateId implements Serializable {

    private UUID accountUuid;
    private Long roleTemplateId;

    protected AccountRoleTemplateId() {
        // JPA only
    }

    public AccountRoleTemplateId(UUID accountUuid, Long roleTemplateId) {
        this.accountUuid = accountUuid;
        this.roleTemplateId = roleTemplateId;
    }

    public UUID getAccountUuid() {
        return accountUuid;
    }

    public Long getRoleTemplateId() {
        return roleTemplateId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AccountRoleTemplateId that)) return false;
        return Objects.equals(accountUuid, that.accountUuid)
                && Objects.equals(roleTemplateId, that.roleTemplateId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountUuid, roleTemplateId);
    }
}
